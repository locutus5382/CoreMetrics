package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.analysis.DukoSessionInsight.Metric
import com.locutus5382.coremetrics.analysis.DukoSessionInsight.Report
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v0.9.9.2, pure offline comparison of 2–5 complete DUKO single-session
 * reports. Identical numeric summaries feed PNG, PDF and test assertions.
 *
 * A difference in sensor readings is NOT evidence of faster gameplay,
 * matching game scenes, lower SoC power or a superior graphics driver.
 */
object DukoComparisonInsight {
    const val MODEL_VERSION = "DUKO-COMPARISON-1"
    enum class Confidence(val label:String) {
        INSUFFICIENT("INSUFFICIENT COMPARABILITY"),
        LIMITED("LIMITED COMPARABILITY"),
        OBSERVATIONAL("OBSERVATIONAL ONLY")
    }

    data class Cell(
        val sessionId:Long,
        val p50:Double?,
        val p95:Double?,
        val coveragePct:Double,
        val deltaP50:Double?,
        val comparable:Boolean,
        val reason:String?
    )
    data class MetricRow(val metric:Metric,val cells:List<Cell>)
    data class Run(
        val report:Report,
        val warnings:List<String>,
        val confidence:Confidence,
        val deviceLabel:String,
        val testLabel:String
    )
    data class Result(
        val baselineId:Long,
        val testedVariable:CompareStudioAnalysis.TestedVariable,
        val runs:List<Run>,
        val rows:List<MetricRow>,
        val overall:Confidence,
        val commonSpanSeconds:Double,
        val observations:List<String>,
        val warnings:List<String>,
        val methodology:List<String>
    ) {
        val baseline:Run get()=runs.first { it.report.session.id==baselineId }
        val ids:List<Long> get()=runs.map { it.report.session.id }
    }

    private fun known(s:String?):String?=s?.trim()?.takeIf { it.isNotBlank() }
    private fun device(s:SessionStore.Session):List<String?> = listOf(
        known(s.deviceManufacturer)?.lowercase(Locale.ROOT),
        known(s.deviceModel)?.lowercase(Locale.ROOT),
        known(s.deviceCode)?.lowercase(Locale.ROOT),
        s.androidApi?.toString()
    )
    private fun sameDevice(a:SessionStore.Session,b:SessionStore.Session):Boolean {
        val x=device(a); val y=device(b)
        return x[0]!=null && x[1]!=null && y[0]!=null && y[1]!=null &&
            x==y
    }
    private fun label(s:SessionStore.Session):String =
        listOfNotNull(known(s.deviceManufacturer),known(s.deviceModel))
            .joinToString(" ").ifBlank { "Origin device unavailable" }

    private fun sourcesAvailable(sources:List<String>):Boolean =
        sources.isNotEmpty() && sources.all {
            it.isNotBlank() && !it.contains("UNAVAILABLE",ignoreCase=true)
        }
    private fun usableGpuSources(a:Report,b:Report):Boolean =
        sourcesAvailable(a.gpuSources) && sourcesAvailable(b.gpuSources) &&
            a.gpuSources.toSet()==b.gpuSources.toSet() &&
            (a.gpuSources+b.gpuSources).none {
                it.contains("READABLE_NOT_VALIDATED",ignoreCase=true) ||
                    it.contains("UNKNOWN",ignoreCase=true)
            }
    private fun usableBatterySources(a:Report,b:Report):Boolean =
        sourcesAvailable(a.batterySources) && sourcesAvailable(b.batterySources) &&
            a.batterySources.toSet()==b.batterySources.toSet()

    fun analyze(
        reports:List<Report>,baselineId:Long,
        testedVariable:CompareStudioAnalysis.TestedVariable =
            CompareStudioAnalysis.TestedVariable.UNDECLARED
    ):Result {
        require(reports.size in 2..5) { "DUKO comparisons require 2–5 recordings" }
        require(reports.map{it.session.id}.toSet().size==reports.size) {
            "Choose distinct recordings"
        }
        require(reports.any{it.session.id==baselineId}) {
            "Baseline must be one of the selected recordings"
        }
        reports.forEach {
            require(it.session.status!="RECORDING" && it.sampleCount>=6 &&
                it.observedSeconds>=20.0 &&
                it.sampleCount.toLong()==it.sourceSampleCount) {
                "Incomplete or too-short source recording #"+it.session.id
            }
        }
        // Order is user-selected, not sorted by measured values.
        val base=reports.first{it.session.id==baselineId}
        val sharedSpan=reports.minOf{it.observedSeconds}
        val aggregate=LinkedHashSet<String>()
        aggregate+="Whole recordings are compared; identical gameplay and scene timing have not been verified."
        aggregate+="The selected baseline is a reference, not a proven performance winner."
        if(testedVariable==CompareStudioAnalysis.TestedVariable.UNDECLARED)
            aggregate+="No single tested variable was declared."
        val metadata=listOf(
            Triple("Graphics driver",CompareStudioAnalysis.TestedVariable.DRIVER,
                {s:SessionStore.Session->s.graphicsDriver}),
            Triple("Performance mode",CompareStudioAnalysis.TestedVariable.PERFORMANCE,
                {s:SessionStore.Session->s.performanceMode}),
            Triple("Fan mode",CompareStudioAnalysis.TestedVariable.FAN,
                {s:SessionStore.Session->s.fanMode}),
            Triple("ClusterTune mode",CompareStudioAnalysis.TestedVariable.CLUSTERTUNE,
                {s:SessionStore.Session->s.clusterTuneMode}),
            Triple("Render scale",CompareStudioAnalysis.TestedVariable.UNDECLARED,
                {s:SessionStore.Session->s.resolutionScale})
        )
        val runs=reports.map { r ->
            val s=r.session
            val warnings=LinkedHashSet<String>()
            if(!sameDevice(base.session,s))
                warnings+="Recording origin differs or is incomplete; between-device deltas are suppressed."
            if(s.status !in setOf("COMPLETED","IMPORTED_COMPLETED"))
                warnings+="Recording was not completed cleanly."
            if(r.gapCount>0)
                warnings+=r.gapCount.toString()+" recording gaps; missing intervals cannot be reconstructed."
            if(r.sampleCount<10)
                warnings+="Very few samples; sensor percentiles are unstable."
            val a=base.cadenceMs;val b=r.cadenceMs
            if(min(a,b)>0L && max(a,b).toDouble()/min(a,b)>=1.5)
                warnings+="Sampling cadence differs from baseline; percentile resolutions differ."
            if(sharedSpan>0.0 && max(r.observedSeconds,base.observedSeconds)/
                min(r.observedSeconds,base.observedSeconds)>1.25)
                warnings+="Recording durations differ by more than 25%; workload exposure is unverified."
            if(s.label!=base.session.label)
                warnings+="Game labels differ; identical workloads cannot be assumed."
            for((name,variable,selector) in metadata) {
                val left=known(selector(base.session))
                val right=known(selector(s))
                if(left==null || right==null)
                    warnings+=name+" metadata is missing in at least one recording."
                else if(left!=right &&
                    (variable==CompareStudioAnalysis.TestedVariable.UNDECLARED ||
                        variable!=testedVariable))
                    warnings+=name+" differs outside the declared tested variable."
                else if(left==right && variable==testedVariable &&
                    testedVariable!=CompareStudioAnalysis.TestedVariable.UNDECLARED)
                    warnings+=name+" is identical despite being the declared tested variable."
            }
            if(!usableGpuSources(base,r))
                warnings+="GPU telemetry provenance differs, is missing, or is not validated."
            if(!usableBatterySources(base,r))
                warnings+="Battery current/voltage source provenance differs or is missing."
            if((base.batterySources+r.batterySources).any {
                it.contains("SNAPSHOT_AGE_UNVERIFIED")
            })warnings+="Current/voltage update synchronization is unverified; watts remain estimates."
            if(r.dischargeCoveragePct<80.0 || base.dischargeCoveragePct<80.0)
                warnings+="Under 80% valid discharge coverage in at least one recording."
            val temp=r.stat(Metric.CPU_TEMP)
            if(temp.coveragePct<80.0)
                warnings+="CPU temperature coverage is below 80%."
            if(r.cautions.any{it.contains("thermal zones")})
                warnings+="Sensor thermal-zone identity is not hardware-calibrated."
            val confidence=when {
                !sameDevice(base.session,s) -> Confidence.INSUFFICIENT
                warnings.isNotEmpty() -> Confidence.LIMITED
                else -> Confidence.OBSERVATIONAL
            }
            warnings.forEach { aggregate+="#"+s.id+": "+it }
            Run(r,warnings.toList(),confidence,label(s),
                (s.note?:s.label).take(96))
        }

        val rows=Metric.entries.map { metric ->
            val baselineStat=base.stat(metric)
            MetricRow(metric,reports.map { r ->
                val stat=r.stat(metric)
                var reason:String?=null
                val raw=stat.p50?.takeIf{it.isFinite()}
                val baseRaw=baselineStat.p50?.takeIf{it.isFinite()}
                if(raw==null || baseRaw==null)
                    reason="Sensor reading unavailable."
                else if(stat.coveragePct<80.0 || baselineStat.coveragePct<80.0)
                    reason="Below 80% sample coverage."
                else if(!sameDevice(base.session,r.session))
                    reason="Different or unknown original hardware."
                else if(metric==Metric.GPU_LOAD || metric==Metric.GPU_CLOCK) {
                    if(!usableGpuSources(base,r))
                        reason="GPU source unavailable, different or unvalidated."
                }else if(metric==Metric.POWER) {
                    if(base.dischargeCoveragePct<80.0 || r.dischargeCoveragePct<80.0)
                        reason="Below 80% valid discharging-time coverage."
                    else if(!usableBatterySources(base,r))
                        reason="Battery source unavailable or different."
                }
                val comparable=reason==null
                Cell(r.session.id,raw,stat.p95?.takeIf{it.isFinite()},
                    stat.coveragePct,
                    if(comparable && raw!=null && baseRaw!=null)raw-baseRaw else null,
                    comparable,reason)
            })
        }

        val observations=buildList {
            // Each selected run gets at most one concise *observed* fact.
            // The display thresholds below are editorial noise filters only;
            // they are not significance tests or proof of matched gameplay.
            for((index,r) in reports.withIndex()) {
                if(r.session.id==baselineId)continue
                val watched=listOf(Metric.GPU_TEMP,Metric.CPU_TEMP,
                    Metric.POWER,Metric.GPU_LOAD)
                for(metric in watched) {
                    val cell=rows.first{it.metric==metric}.cells[index]
                    val delta=cell.deltaP50?:continue
                    val minimum=if(metric==Metric.GPU_LOAD)1.0 else 0.2
                    if(!cell.comparable || abs(delta)<minimum)continue
                    val unit=if(metric==Metric.GPU_LOAD)" percentage points"
                        else " "+metric.unit
                    add("Session #"+r.session.id+" recorded "+
                        String.format(Locale.US,"%.1f",abs(delta))+unit+
                        (if(delta>0)" higher" else " lower")+
                        " median "+metric.label.lowercase(Locale.ROOT)+
                        " than baseline #"+baselineId+
                        "; different gameplay phases may explain the observation.")
                    break
                }
            }
            if(isEmpty())add("No eligible baseline-relative sensor difference "+
                "exceeded the report's display thresholds; this is not proof "+
                "that the sessions performed equally.")
            add("P50 and P95 are sampled sensor distributions, never game FPS or frame-time percentiles.")
            val eligible=rows.count { row -> row.cells.all{it.comparable} }
            add(eligible.toString()+" of "+rows.size+
                " metrics have complete baseline-relative numeric eligibility across all runs.")
            add("Longest-to-shortest observed duration: "+
                String.format(Locale.US,"%.1f",reports.maxOf{it.observedSeconds}/
                    max(0.001,sharedSpan))+"×; scenes were not synchronized.")
            val power=rows.first{it.metric==Metric.POWER}
            if(power.cells.all{it.comparable}) {
                val means=reports.mapNotNull{it.weightedDischargeW}
                if(means.size==reports.size) {
                    val spread=means.maxOrNull()!!-means.minOrNull()!!
                    add("Observed time-weighted whole-device battery discharge spans "+
                        String.format(Locale.US,"%.2f",spread)+
                        " W across runs; no energy-efficiency ranking is implied.")
                }
            }
        }.take(12)
        val overall=when {
            runs.any{it.confidence==Confidence.INSUFFICIENT} -> Confidence.INSUFFICIENT
            runs.any{it.confidence==Confidence.LIMITED} -> Confidence.LIMITED
            else -> Confidence.OBSERVATIONAL
        }
        val methodology=listOf(
            "Each run uses its full persisted chronology, up to 250,000 samples. No 12k UI preview is used.",
            "All rows show original P50/P95. Delta is current P50 minus selected baseline P50, not a speedup.",
            "A delta is hidden when device identity is missing/different, signal coverage is <80%, or required sensor provenance is incompatible.",
            "Power figures are whole-device battery-terminal net discharge, not CPU/GPU power. Only valid adjacent discharging intervals contribute to Wh.",
            "Recorded Wh totals may reflect different durations and are not ranked as game efficiency.",
            "Sampling cadence and workload phases can differ; no scene alignment, FPS, game-thread scheduling, statistical confidence interval or causal hypothesis test is claimed.",
            "Manual game, driver, profile, scale, fan and tuner fields describe user declarations, not confirmed device settings.",
            "A high observation count does not replace repeated controlled trials; every finding is observational."
        )
        return Result(baselineId,testedVariable,runs,rows,overall,sharedSpan,
            observations,aggregate.toList(),methodology)
    }
}
