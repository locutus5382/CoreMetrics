package com.locutus5382.coremetrics.analysis

import kotlin.math.abs

/**
 * Read-only analysis of *observed* cpufreq ceilings. A limit is not proof that
 * ClusterTune (or any specific tuner) caused it. Contiguous phases are retained
 * even if the same limit returns later in the recording.
 */
object SessionRegimeAnalyzer {
    const val NOMINAL_BATTERY_WH = 30.8  // Odin 2 Portal estimate, NOT measured usable capacity
    private const val MIN_RUN_SAMPLES = 3
    private val capPattern = Regex("""policy(3|7)\s+max\s+([0-9]+(?:\.[0-9]+)?)\s+GHz""")

    data class Sample(
        val timeMs: Long,
        val observedCpuLimits: String?,
        val cpuTempC: Double?,
        val gpuTempC: Double?,
        val cpuLoadPct: Double?,
        val busiestCorePct: Double?,
        val gpuLoadPct: Double?,
        val ramUsedGib: Double?,
        val batteryNetW: Double?,
        val batteryPowerStatus: String?
    )
    data class RegimeSegment(
        val index: Int,
        val key: String,
        val label: String,
        val startMs: Long,
        val endMs: Long,
        val sampleCount: Int,
        val meanCpuC: Double?,
        val maxCpuC: Double?,
        val meanGpuC: Double?,
        val maxGpuC: Double?,
        val meanCpuLoadPct: Double?,
        val meanBusiestCorePct: Double?,
        val meanGpuLoadPct: Double?,
        val meanRamGib: Double?,
        val meanDischargeW: Double?,
        val projectedHours: Double?,
        val deltaMeanCpuC: Double? = null,
        val deltaMaxCpuC: Double? = null,
        val deltaMeanGpuC: Double? = null,
        val deltaMeanDischargeW: Double? = null,
        val deltaDischargePct: Double? = null,
        val deltaProjectedHours: Double? = null,
        val baseline: Boolean = false
    )
    private data class Run(val key: String, val samples: MutableList<Sample>)

    /**
     * Normalize only policy3 and policy7 ceilings: policy0 may change
     * independently and should not create a fictitious tuning transition.
     * Missing limits are explicitly 'unknown', never assumed stock.
     */
    fun key(raw: String?): String {
        val caps = capPattern.findAll(raw.orEmpty()).associate {
            it.groupValues[1] to it.groupValues[2].toDouble()
        }
        fun fmt(v: Double?): String =
            if (v == null) "?" else String.format(java.util.Locale.US, "%.2f", v)
        return "P3:" + fmt(caps["3"]) + "|P7:" + fmt(caps["7"])
    }

    fun label(key: String): String {
        val parts = key.split('|')
        val p3 = parts.firstOrNull()?.substringAfter(':') ?: "?"
        val p7 = parts.getOrNull(1)?.substringAfter(':') ?: "?"
        return when {
            p3 == "2.80" && p7 == "3.19" -> "Stock (2.80 / 3.19 GHz)"
            p3 == "2.05" && p7 == "2.09" -> "Medium UC (2.05 / 2.09 GHz)"
            p3 == "1.79" && p7 == "1.84" -> "Large UC (1.79 / 1.84 GHz)"
            p3 == "?" && p7 == "?" -> "Limits unavailable"
            else -> "Observed cap (P3 $p3 / P7 $p7 GHz)"
        }
    }

    fun analyze(
        input: List<Sample>,
        activeGameplayOnly: Boolean = false,
        gpuLoadThreshold: Double = 15.0
    ): List<RegimeSegment> {
        if (input.isEmpty()) return emptyList()
        // Build segments from the FULL chronology so filtering menus/loading
        // cannot accidentally bridge disconnected time ranges.
        val runs = mutableListOf<Run>()
        for (s in input.sortedBy { it.timeMs }) {
            val k = key(s.observedCpuLimits)
            if (runs.lastOrNull()?.key != k) runs.add(Run(k, mutableListOf(s)))
            else runs.last().samples.add(s)
        }
        // Suppress 1-2 sample transition glitches. Prefer matching surrounding
        // phases: A,X,A -> A, rather than inventing a brief new regime.
        var i = 0
        while (runs.size > 1 && i < runs.size) {
            if (runs[i].samples.size >= MIN_RUN_SAMPLES) { i++; continue }
            when {
                i > 0 && i + 1 < runs.size && runs[i-1].key == runs[i+1].key -> {
                    runs[i-1].samples.addAll(runs[i].samples)
                    runs[i-1].samples.addAll(runs[i+1].samples)
                    runs.removeAt(i+1)
                    runs.removeAt(i)
                    i = (i-1).coerceAtLeast(0)
                }
                i == 0 -> {
                    runs[1].samples.addAll(0, runs[0].samples)
                    runs.removeAt(0)
                }
                else -> {
                    runs[i-1].samples.addAll(runs[i].samples)
                    runs.removeAt(i)
                    i = (i-1).coerceAtLeast(0)
                }
            }
        }

        fun mean(xs: List<Double?>): Double? {
            val valid = xs.filterNotNull().filter { it.isFinite() }
            return if(valid.isEmpty()) null else valid.average()
        }
        fun maxValid(xs: List<Double?>): Double? =
            xs.filterNotNull().filter { it.isFinite() }.maxOrNull()
        val segments = runs.mapNotNull { run ->
            val stats = if (activeGameplayOnly)
                run.samples.filter { (it.gpuLoadPct ?: -1.0) >= gpuLoadThreshold }
            else run.samples
            if (stats.isEmpty()) null else {
                val power = mean(stats.map {
                    it.batteryNetW?.takeIf { w ->
                        w.isFinite() && w > 0.1 &&
                            it.batteryPowerStatus?.startsWith("DISCHARGING") == true
                    }
                })
                RegimeSegment(
                    index=0, key=run.key, label=label(run.key),
                    startMs=run.samples.first().timeMs,
                    endMs=run.samples.last().timeMs,
                    sampleCount=stats.size,
                    meanCpuC=mean(stats.map { it.cpuTempC }),
                    maxCpuC=maxValid(stats.map { it.cpuTempC }),
                    meanGpuC=mean(stats.map { it.gpuTempC }),
                    maxGpuC=maxValid(stats.map { it.gpuTempC }),
                    meanCpuLoadPct=mean(stats.map { it.cpuLoadPct }),
                    meanBusiestCorePct=mean(stats.map { it.busiestCorePct }),
                    meanGpuLoadPct=mean(stats.map { it.gpuLoadPct }),
                    meanRamGib=mean(stats.map { it.ramUsedGib }),
                    meanDischargeW=power,
                    projectedHours=power?.let { NOMINAL_BATTERY_WH / it }
                )
            }
        }.mapIndexed { idx, s -> s.copy(index=idx) }
        if (segments.isEmpty()) return emptyList()
        val baseline = segments.firstOrNull { it.label.startsWith("Stock (") }
            ?: segments.first()
        fun difference(a:Double?,b:Double?):Double? =
            if (a!=null && b!=null) a-b else null
        return segments.map { s ->
            if (s.index == baseline.index) s.copy(baseline=true)
            else s.copy(
                deltaMeanCpuC=difference(s.meanCpuC,baseline.meanCpuC),
                deltaMaxCpuC=difference(s.maxCpuC,baseline.maxCpuC),
                deltaMeanGpuC=difference(s.meanGpuC,baseline.meanGpuC),
                deltaMeanDischargeW=difference(s.meanDischargeW,baseline.meanDischargeW),
                deltaDischargePct=if(s.meanDischargeW!=null &&
                    baseline.meanDischargeW!=null && baseline.meanDischargeW > 0.1)
                    100.0*(s.meanDischargeW-baseline.meanDischargeW)/
                        baseline.meanDischargeW else null,
                deltaProjectedHours=difference(s.projectedHours,baseline.projectedHours)
            )
        }
    }
}
