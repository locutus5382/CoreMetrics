package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * v0.8.6: post-recording, read-only session diagnostics.
 * No additional hardware reads, database writes, polling or inferred FPS.
 * An observation is not a diagnosis of throttling, poor battery health,
 * emulator scene equivalence or a confirmed single-thread bottleneck.
 */
object SessionDiagnosticsAnalyzer {
    const val NOMINAL_BATTERY_WH = 30.8

    enum class Metric(val title: String, val unit: String) {
        CPU_TEMP("CPU sensor temperature", "°C"),
        GPU_TEMP("GPU sensor temperature", "°C"),
        BATTERY_TEMP("Battery temperature", "°C"),
        CPU_LOAD("Total CPU utilization", "%"),
        BUSIEST_CORE("Busiest observed core", "%"),
        GPU_LOAD("GPU utilization", "%"),
        DISCHARGE_W("Battery terminal discharge", "W"),
        P3_CLOCK("Performance cluster clock", "GHz"),
        P7_CLOCK("Prime core clock", "GHz"),
        GPU_CLOCK("GPU clock", "MHz"),
        BATTERY_PCT("Battery level", "%")
    }

    data class Reading(
        val timeMs: Long, val value: Double, val rolling60: Double?,
        val segment: Int
    )
    data class Distribution(
        val metric: Metric, val observations: Int, val totalSamples: Int,
        val median: Double?, val p95: Double?, val peak: Double?,
        val peakTimeMs: Long?, val final60Median: Double?,
        val readings: List<Reading>
    ) {
        val validPct: Double
            get() = if (totalSamples > 0) 100.0 * observations / totalSamples else 0.0
    }
    data class Pressure(
        val thresholdPct: Double, val observedSeconds: Double,
        val highSeconds: Double, val longestHighSeconds: Double
    ) {
        val highSharePct: Double
            get() = if (observedSeconds > 0.0) 100.0 * highSeconds / observedSeconds else 0.0
    }
    data class Result(
        val distributions: List<Distribution>,
        val cadenceMs: Long, val gapLimitMs: Long, val gapCount: Int,
        val busiestCorePressure: Pressure, val gpuPressure: Pressure,
        val initialCpuSlopeCPerMin: Double?, val finalCpuSlopeCPerMin: Double?,
        val meanCpuBatteryDeltaC: Double?,
        val observedCapChanges: Int, val observedCapReadings: Int,
        val remainingHours: Double?, val fullChargeHours: Double?,
        val typicalDischargeW: Double?, val runtimeNote: String,
        val measuredBatteryPct: Int?
    ) {
        fun distribution(metric: Metric): Distribution =
            distributions.first { it.metric == metric }
    }

    // scaling_cur_freq is a snapshot, NOT a governor ceiling.
    private val clockPattern = Regex("""policy(3|7)\s+([0-9]+(?:\.[0-9]+)?)\s*GHz""")
    fun clockGHz(raw: String?, policy: Int): Double? {
        if (policy != 3 && policy != 7) return null
        return clockPattern.findAll(raw.orEmpty())
            .firstOrNull { it.groupValues[1] == policy.toString() }
            ?.groupValues?.get(2)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.05..5.0 }
    }

    fun value(metric: Metric, point: SessionStore.Point): Double? {
        val raw = when(metric) {
            Metric.CPU_TEMP -> point.cpu
            Metric.GPU_TEMP -> point.gpu
            Metric.BATTERY_TEMP -> point.batteryTemp
            Metric.CPU_LOAD -> point.cpuLoad
            Metric.BUSIEST_CORE -> point.busiestCorePct
            Metric.GPU_LOAD -> point.gpuLoad
            Metric.DISCHARGE_W -> DischargeSample.watts(
                point.batteryWatts, point.batteryPowerStatus)
            Metric.P3_CLOCK -> clockGHz(point.cpuClocks, 3)
            Metric.P7_CLOCK -> clockGHz(point.cpuClocks, 7)
            Metric.GPU_CLOCK -> point.gpuMHz
            Metric.BATTERY_PCT -> point.batteryPct?.toDouble()
        }?.takeIf { it.isFinite() } ?: return null
        return when(metric) {
            Metric.CPU_LOAD, Metric.BUSIEST_CORE, Metric.GPU_LOAD,
            Metric.BATTERY_PCT -> raw.takeIf { it in 0.0..100.0 }
            Metric.CPU_TEMP, Metric.GPU_TEMP, Metric.BATTERY_TEMP ->
                raw.takeIf { it in -20.0..160.0 }
            Metric.P3_CLOCK, Metric.P7_CLOCK -> raw.takeIf { it in 0.05..5.0 }
            Metric.GPU_CLOCK -> raw.takeIf { it in 1.0..2000.0 }
            Metric.DISCHARGE_W -> raw
        }
    }

    private fun quantile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val ordered = values.sorted()
        val index = (ordered.lastIndex * percentile).coerceIn(0.0, ordered.lastIndex.toDouble())
        val lower = floor(index).toInt()
        val upper = min(ordered.lastIndex, lower + 1)
        return ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower)
    }

    private fun medianDelta(points: List<SessionStore.Point>): Long {
        val gaps = points.zipWithNext { a,b -> b.timeMs - a.timeMs }
            .filter { it > 0L }.sorted()
        return if (gaps.isEmpty()) 5_000L else gaps[gaps.size / 2]
    }

    private fun distribution(
        metric: Metric, points: List<SessionStore.Point>, gapLimit: Long
    ): Distribution {
        val readings = ArrayList<Reading>()
        val window = ArrayDeque<Pair<Long,Double>>()
        var lastMs: Long? = null
        var segment = 0
        for (p in points) {
            val prev = lastMs
            if (prev != null && (p.timeMs <= prev || p.timeMs - prev > gapLimit)) {
                segment++
                window.clear()
            }
            lastMs = p.timeMs
            val v = value(metric, p)
            if (v == null) {
                segment++
                window.clear()
                continue
            }
            window.addLast(p.timeMs to v)
            while (window.isNotEmpty() && p.timeMs - window.peekFirst().first > 60_000L)
                window.removeFirst()
            val span = if (window.isEmpty()) 0L
                else window.peekLast().first - window.peekFirst().first
            val rolling = if (window.size >= 4 && span >= 20_000L)
                quantile(window.map { it.second }, 0.5) else null
            readings.add(Reading(p.timeMs,v,rolling,segment))
        }
        val values = readings.map { it.value }
        val highest = values.maxOrNull()
        val peakAt = readings.firstOrNull { it.value == highest }?.timeMs
        return Distribution(metric,readings.size,points.size,
            quantile(values,0.5),quantile(values,0.95),highest,peakAt,
            readings.lastOrNull()?.rolling60,readings)
    }

    private fun pressure(
        metric: Metric, points: List<SessionStore.Point>, gapLimit: Long,
        threshold: Double = 85.0
    ): Pressure {
        var eligible = 0L
        var high = 0L
        var longest = 0L
        var active = 0L
        for (i in 1 until points.size) {
            val a = points[i-1]; val b = points[i]
            val dt = b.timeMs - a.timeMs
            if (dt <= 0L || dt > gapLimit) { active = 0L; continue }
            val x = value(metric,a); val y = value(metric,b)
            if (x == null || y == null) { active = 0L; continue }
            eligible += dt
            if (x >= threshold && y >= threshold) {
                high += dt
                active += dt
                longest = max(longest,active)
            } else active = 0L
        }
        return Pressure(threshold,eligible / 1000.0,high / 1000.0,longest / 1000.0)
    }

    // Linear regression over measured samples in a contiguous actual-time window.
    private fun cpuSlope(
        points: List<SessionStore.Point>, start: Long, end: Long, gapLimit: Long
    ): Double? {
        // Missing sensor readings break the evidence chain; never regress
        // through them as if an unmeasured temperature were continuous.
        if (points.any { it.timeMs in start..end &&
                value(Metric.CPU_TEMP,it) == null }) return null
        val slice = points.filter {
            it.timeMs in start..end && value(Metric.CPU_TEMP,it) != null
        }
        if (slice.size < 8 || slice.last().timeMs - slice.first().timeMs < 45_000L)
            return null
        if (slice.zipWithNext().any { (a,b) ->
                b.timeMs <= a.timeMs || b.timeMs - a.timeMs > gapLimit
            }) return null
        val x = slice.map { (it.timeMs - slice.first().timeMs) / 60_000.0 }
        val y = slice.map { value(Metric.CPU_TEMP,it)!! }
        val mx = x.average(); val my = y.average()
        val variance = x.sumOf { (it-mx)*(it-mx) }
        if (variance <= 0.0) return null
        return x.indices.sumOf { (x[it]-mx)*(y[it]-my) } / variance
    }

    fun analyze(
        input: List<SessionStore.Point>, report: BenchmarkAnalyzer.Report
    ): Result {
        val points = input.sortedBy { it.timeMs }
        val cadence = medianDelta(points)
        val gapLimit = max(6_000L,min(15_000L,cadence * 3))
        val metrics = Metric.values().map { distribution(it,points,gapLimit) }
        val gaps = points.zipWithNext().count { (a,b) ->
            b.timeMs - a.timeMs > gapLimit
        }
        var capChanges = 0; var knownCaps = 0
        var lastKey: String? = null
        points.forEach { p ->
            val key = SessionRegimeAnalyzer.key(p.observedCpuLimits)
                .takeUnless { it.contains("?") }
            if (key == null) lastKey = null
            else {
                knownCaps++
                if (lastKey != null && key != lastKey) capChanges++
                lastKey = key
            }
        }
        val deltas = points.mapNotNull { p ->
            val cpu = value(Metric.CPU_TEMP,p)
            val battery = value(Metric.BATTERY_TEMP,p)
            if (cpu == null || battery == null) null else cpu - battery
        }
        val start = points.firstOrNull()?.timeMs ?: 0L
        val end = points.lastOrNull()?.timeMs ?: 0L
        val batteryPct = points.lastOrNull()?.batteryPct?.takeIf { it in 0..100 }
        val power = report.energy.weightedMeanW
        val sufficient = report.energy.powerCondition ==
            BenchmarkAnalyzer.PowerCondition.DISCHARGING &&
            report.energy.coveragePct >= 60.0 &&
            report.energy.measuredSeconds >= 120.0 &&
            power != null && power.isFinite() && power > 0.0 &&
            batteryPct != null
        val remaining = if (sufficient) NOMINAL_BATTERY_WH *
            batteryPct!!.toDouble() / 100.0 / power!! else null
        val full = if (sufficient) NOMINAL_BATTERY_WH / power!! else null
        val reason = when {
            sufficient -> "Approximate, not a fuel-gauge prediction; assumes nominal " +
                "30.8 Wh usable capacity and this session's time-weighted power."
            report.energy.powerCondition != BenchmarkAnalyzer.PowerCondition.DISCHARGING ->
                "Runtime unavailable: recording was not exclusively discharging."
            report.energy.coveragePct < 60.0 || report.energy.measuredSeconds < 120.0 ->
                "Runtime unavailable: need at least 120 s and 60% valid discharge coverage."
            batteryPct == null -> "Runtime unavailable: final battery level missing."
            else -> "Runtime unavailable: usable discharge rate missing."
        }
        return Result(metrics,cadence,gapLimit,gaps,
            pressure(Metric.BUSIEST_CORE,points,gapLimit),
            pressure(Metric.GPU_LOAD,points,gapLimit),
            cpuSlope(points,start,start + 180_000L,gapLimit),
            cpuSlope(points,max(start,end - 60_000L),end,gapLimit),
            deltas.takeIf { it.isNotEmpty() }?.average(),
            capChanges,knownCaps,remaining,full,power,reason,batteryPct)
    }

    fun text(result: Result, originMs: Long? = null): String = buildString {
        appendLine()
        appendLine("ADVANCED SINGLE-SESSION DIAGNOSTICS (RECORDED SAMPLE WINDOW)")
        val origin = originMs ?: result.distributions.firstOrNull()
            ?.readings?.firstOrNull()?.timeMs ?: 0L
        for (stat in result.distributions) {
            fun fmt(v: Double?): String = v?.let {
                String.format(Locale.US,"%.2f",it)
            } ?: "n/a"
            append(stat.metric.title).append(": median ")
                .append(fmt(stat.median)).append(", P95 ")
                .append(fmt(stat.p95)).append(", peak ")
                .append(fmt(stat.peak)).append(" ").append(stat.metric.unit)
                .append(" at ")
                .append(stat.peakTimeMs?.let { (it-origin)/1000L } ?: "n/a")
                .append(" s; final 60-s median ").append(fmt(stat.final60Median))
                .append("; valid samples ").append(stat.observations)
                .append("/").append(stat.totalSamples).appendLine()
        }
        appendLine("Observed CPU cap changes: " + result.observedCapChanges +
            " (" + result.observedCapReadings + " valid cap snapshots)")
        appendLine("Busiest-core load >=85%: " +
            String.format(Locale.US,"%.1f%%",result.busiestCorePressure.highSharePct) +
            " of " + result.busiestCorePressure.observedSeconds + " valid seconds")
        appendLine("GPU load >=85%: " +
            String.format(Locale.US,"%.1f%%",result.gpuPressure.highSharePct) +
            " of " + result.gpuPressure.observedSeconds + " valid seconds")
        appendLine("CPU initial / final slopes (°C/min): " +
            result.initialCpuSlopeCPerMin + " / " + result.finalCpuSlopeCPerMin)
        appendLine("Mean CPU-sensor minus battery-temperature delta (°C): " +
            result.meanCpuBatteryDeltaC)
        appendLine("Estimated remaining / full hours: " +
            result.remainingHours + " / " + result.fullChargeHours)
        appendLine("Power estimate: " + result.runtimeNote)
        appendLine("Caveat: clock changes do not prove thermal throttling; high core " +
            "utilization does not prove a frame bottleneck. Temperature sensors " +
            "are not necessarily socket or chassis readings. Gaps are not bridged.")
    }
}
