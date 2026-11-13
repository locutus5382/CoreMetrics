package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline, read-only benchmark statistics. Uses persisted raw samples only.
 * Neither absent frame data nor battery-terminal watts are called game FPS/power.
 */
object BenchmarkAnalyzer {
    data class Quality(
        val score:Int, val category:String, val reasons:List<String>,
        val coveragePct:Double, val typicalIntervalMs:Long, val gapCount:Int,
        val observedCeilings:String, val labelConflict:Boolean
    )
    enum class PowerCondition(val label:String) {
        DISCHARGING("Discharging"), PLUGGED_IN("Plugged In"),
        MIXED("Mixed plugged/discharging"), UNKNOWN("Power status unavailable")
    }
    data class Energy(
        val wh:Double?, val measuredSeconds:Double, val coveragePct:Double,
        val meanDischargeW:Double?,
        val milliWhPerMinute:Double? = null,
        val weightedMeanW:Double? = null,
        val powerCondition:PowerCondition = PowerCondition.UNKNOWN
    )
    data class Thermal(
        val state:String, val slopeCPerMin:Double?,
        val lastMinuteMeanC:Double?, val lastMinutePeakC:Double?,
        val samples:Int, val spanSeconds:Double
    )
    data class FrameEfficiency(
        val presentedFps:Double?, val fpsPerBatteryW:Double?,
        val batteryJoulesPerPresentedFrame:Double?, val note:String
    )
    data class Report(
        val session:SessionStore.Session, val sampleCount:Int, val elapsedSeconds:Double,
        val meanCpuC:Double?, val peakCpuC:Double?, val meanGpuC:Double?,
        val meanCpuLoad:Double?, val meanGpuLoad:Double?,
        val quality:Quality, val energy:Energy, val thermal:Thermal,
        val frames:FrameEfficiency, val skippedWarmUpSeconds:Int = 0
    )
    private fun mean(v:List<Double?>):Double? {
        val values=v.filterNotNull().filter { it.isFinite() }
        return if(values.isEmpty())null else values.average()
    }
    private fun median(values:List<Long>):Long {
        if(values.isEmpty())return 5_000L
        val sorted=values.sorted()
        return sorted[sorted.size/2].coerceAtLeast(100L)
    }
    private fun fmt(d:Double?,digits:Int=2):String =
        d?.takeIf { it.isFinite() }?.let {
            String.format(Locale.US,"%."+digits+"f",it)
        } ?: "unavailable"

    /**
     * Robust linear slope, degrees Celsius per minute, on the last 60s of
     * sampled temperature. Does NOT diagnose thermal throttling from one sensor.
     */
    private fun thermal(points:List<SessionStore.Point>):Thermal {
        if(points.isEmpty())return Thermal("unavailable",null,null,null,0,0.0)
        val end=points.last().timeMs
        val samples=points.filter { end-it.timeMs<=60_000L &&
            it.cpu!=null && it.cpu!!.isFinite() }
        if(samples.isEmpty())return Thermal("unavailable",null,null,null,0,0.0)
        val duration=(samples.last().timeMs-samples.first().timeMs)/1000.0
        val mean=mean(samples.map { it.cpu })
        val peak=samples.mapNotNull { it.cpu }.maxOrNull()
        if(samples.size<12 || duration<30.0)
            return Thermal("insufficient thermal history",null,mean,peak,
                samples.size,duration)
        val t0=samples.first().timeMs
        val ts=samples.map {(it.timeMs-t0)/60_000.0}
        val temps=samples.map {it.cpu!!}
        val midT=ts.average()
        val midTemp=temps.average()
        val covariance=ts.indices.sumOf {(ts[it]-midT)*(temps[it]-midTemp)}
        val variance=ts.sumOf {(it-midT)*(it-midT)}
        val slope=if(variance>0.0)covariance/variance else null
        val minTemp=temps.minOrNull()?:midTemp
        val maxTemp=temps.maxOrNull()?:midTemp
        val state=when {
            duration<55.0 -> "last-minute trend window too short to characterize"
            slope==null -> "unavailable"
            abs(slope)<=0.8 && maxTemp-minTemp<=2.5 ->
                "low variation in final 60s (not proof of equilibrium)"
            slope>0.8 -> "rising in final 60s"
            slope< -0.8 -> "falling in final 60s"
            else -> "variable final-minute temperature"
        }
        return Thermal(state,slope,mean,peak,samples.size,duration)
    }

    /** Integrates only valid adjacent discharge readings. No interpolation
     * over large gaps, charging or missing data. No extrapolation to full battery.
     */
    fun analyze(session:SessionStore.Session,raw:List<SessionStore.Point>,
        skipFirstSeconds:Int=0):Report {
        // Relative to first recorded sample, NOT guaranteed game world start.
        val sorted=raw.sortedBy { it.timeMs }
        val skip=skipFirstSeconds.coerceIn(0,300)
        val origin=sorted.firstOrNull()?.timeMs ?: 0L
        val pts=if(skip==0)sorted else sorted.filter {
            it.timeMs-origin>=skip*1000L
        }
        val statuses=sorted.mapNotNull{it.batteryPowerStatus}
        val sawPlugged=statuses.any{
            it.startsWith("CHARGING_OR_EXTERNAL_POWER") ||
                it.startsWith("FULL") || it.startsWith("PLUGGED")
        }
        val sawDischarge=statuses.any{it.startsWith("DISCHARGING")}
        val condition=when{
            sawPlugged && sawDischarge->PowerCondition.MIXED
            sawPlugged ->PowerCondition.PLUGGED_IN
            sawDischarge ->PowerCondition.DISCHARGING
            else->PowerCondition.UNKNOWN
        }
        val elapsed=if(pts.size>=2)
            (pts.last().timeMs-pts.first().timeMs).coerceAtLeast(0L)/1000.0
            else 0.0
        val deltas=pts.zipWithNext().map { (a,b)->b.timeMs-a.timeMs }
            .filter { it>0L }
        val typical=median(deltas)
        val gapLimit=max(6_000L,typical*3)
        var gapCount=0
        var coveredMs=0L
        var dischargeMs=0L
        var wh=0.0
        fun discharge(p:SessionStore.Point):Double? =
            DischargeSample.watts(p.batteryWatts,p.batteryPowerStatus)
        for(i in 1 until pts.size) {
            val prev=pts[i-1];val next=pts[i]
            val dt=next.timeMs-prev.timeMs
            if(dt<=0)continue
            if(dt>gapLimit) {gapCount++;continue}
            coveredMs+=dt
            val a=discharge(prev);val b=discharge(next)
            if(a!=null && b!=null) {
                dischargeMs+=dt
                wh+=(a+b)/2.0*dt/3_600_000.0
            }
        }
        val durationMs=if(pts.size>=2)pts.last().timeMs-pts.first().timeMs else 0L
        val coverage=if(durationMs>0L)100.0*coveredMs/durationMs else 0.0
        val energyCoverage=if(durationMs>0L)100.0*dischargeMs/durationMs else 0.0
        // All three figures derive from exactly the same time-integrated
        // valid discharge intervals, not an unweighted point average.
        val weightedW=if(dischargeMs>0L)wh*3_600_000.0/dischargeMs else null
        val mWhPerMinute=if(dischargeMs>0L)wh*60_000_000.0/dischargeMs else null
        val capGroups=pts.map { SessionRegimeAnalyzer.key(it.observedCpuLimits) }
            .filterNot { it=="P3:?|P7:?" }
            .distinct()
        val labels=capGroups.map{SessionRegimeAnalyzer.label(it)}
        val observed=if(labels.isEmpty())"unavailable" else labels.joinToString(" | ")
        val conflict=V08Analytics.profileConflict(session.configuration,labels)
        val reasons=mutableListOf<String>()
        var score=100
        if(session.status !in setOf("COMPLETED","IMPORTED_COMPLETED")) {
            score-=25
            reasons+="Session status is ${session.status}; source may be interrupted, " +
                "incomplete or its original status may be unknown"
        }
        if(elapsed<60.0) {
            score-=20
            reasons+="Less than 60 s of telemetry"
        }
        if(pts.size<20) {
            score-=10
            reasons+="Fewer than 20 samples"
        }
        if(coverage<90.0) {
            score-=20
            reasons+="Sampling gaps reduce time coverage to ${fmt(coverage,0)}%"
        }
        if(mean(pts.map {it.cpu})==null) {
            score-=10
            reasons+="CPU temperature missing"
        }
        if(condition==PowerCondition.PLUGGED_IN ||
            condition==PowerCondition.MIXED) {
            reasons+="Power conditions: ${condition.label}; " +
                "charging/external intervals excluded from battery discharge"
        }
        if(energyCoverage<60.0) {
            score-=10
            reasons+="Discharge-energy coverage under 60% (or charging)"
        }
        if(conflict) {
            score-=20
            reasons+="Manual profile label may not match observed CPU " +
                "ceilings; generic Underclocking may still mean GPU-only tuning"
        }
        if(labels.isEmpty())reasons+="Observed CPU frequency limits unavailable"
        else reasons+="Observed ceilings are not authenticated tuner identity"
        if(pts.none{it.sfFps!=null})reasons+="Game FPS is unavailable; cannot assess gaming smoothness"
        score=score.coerceIn(0,100)
        val level=when {
            score>=85 -> "GOOD INPUT (observational only)"
            score>=60 -> "LIMITED COMPARISON"
            else -> "INSUFFICIENT / CAUTION"
        }
        // Never infer game efficiency from sparse or unverified SurfaceFlinger
        // FPS snapshots. Future verified frame-time series needs its own analyzer.
        val frames=FrameEfficiency(null,null,null,
            "Game FPS, FPS/W and joules/frame unavailable: source not independently " +
            "verified; one/two-second telemetry cannot provide 1% lows.")
        return Report(session,pts.size,elapsed,
            mean(pts.map {it.cpu}),pts.mapNotNull{it.cpu}.maxOrNull(),
            mean(pts.map {it.gpu}),mean(pts.map {it.cpuLoad}),
            mean(pts.map {it.gpuLoad}),
            Quality(score,level,reasons,coverage,typical,gapCount,observed,conflict),
            Energy(if(dischargeMs>0L)wh else null,dischargeMs/1000.0,
                energyCoverage,weightedW,mWhPerMinute,weightedW,condition),
            thermal(pts),frames,skip)
    }

    fun text(report:Report):String=buildString {
        val r=report
        appendLine("COREMETRICS | BENCHMARK ASSESSMENT (OBSERVATIONAL)")
        appendLine("Session #${r.session.id}: ${r.session.label} · ${r.session.configuration}")
        appendLine("Session status: ${r.session.status}; samples ${r.sampleCount}")
        r.session.note?.let{ appendLine("Game/track note: "+it) }
        appendLine("Manual driver tag: "+(r.session.graphicsDriver?:"unavailable"))
        appendLine("Manual resolution tag: "+(r.session.resolutionScale?:"unavailable"))
        if(r.skippedWarmUpSeconds>0)appendLine(
            "Analysis excludes first ${r.skippedWarmUpSeconds} s after first recorded " +
            "sample (not verified game-loading completion). Original data unchanged.")
        appendLine("Duration (first-to-last sample): ${fmt(r.elapsedSeconds,1)} s")
        appendLine("QUALITY / COVERAGE")
        appendLine("Heuristic data quality: ${r.quality.score}/100 — ${r.quality.category}")
        appendLine("Coverage: ${fmt(r.quality.coveragePct,1)}%; " +
            "typical interval: ${r.quality.typicalIntervalMs} ms; gaps: ${r.quality.gapCount}")
        appendLine("Observed CPU ceilings: ${r.quality.observedCeilings}")
        if(r.quality.labelConflict)appendLine(
            "IMPORTANT: configuration LABEL conflicts with observed CPU caps")
        r.quality.reasons.forEach { appendLine("- "+it) }
        appendLine("THERMAL")
        appendLine("CPU avg / peak: ${fmt(r.meanCpuC)} / ${fmt(r.peakCpuC)} °C")
        appendLine("GPU mean: ${fmt(r.meanGpuC)} °C")
        appendLine("Last-window CPU mean / peak: " +
            "${fmt(r.thermal.lastMinuteMeanC)} / ${fmt(r.thermal.lastMinutePeakC)} °C")
        appendLine("Observed final-minute temperature trend: ${r.thermal.state}; " +
            "slope ${fmt(r.thermal.slopeCPerMin)} °C/min")
        appendLine("LOAD AND BATTERY NET")
        appendLine("Power conditions: ${r.energy.powerCondition.label}")
        appendLine("Mean CPU/GPU load: ${fmt(r.meanCpuLoad)}% / ${fmt(r.meanGpuLoad)}%")
        appendLine("Time-weighted battery-terminal discharge: "+
            "${fmt(r.energy.weightedMeanW)} W")
        appendLine("Normalized discharge: "+
            "${fmt(r.energy.milliWhPerMinute)} mWh/min")
        appendLine("Integrated discharge energy: ${fmt(r.energy.wh,4)} Wh")
        appendLine("Valid discharge integration: ${fmt(r.energy.measuredSeconds,1)} s; " +
            "coverage: ${fmt(r.energy.coveragePct,1)}%")
        appendLine("Battery measurements are battery-terminal net power, NOT wall or SoC-only watts.")
        appendLine("FPS / STUTTER / EFFICIENCY")
        appendLine(r.frames.note)
        appendLine("This report makes no claim of identical in-game scenes, tuner causality, " +
            "thermal throttling, or statistical significance.")
    }

    fun comparison(a:Report,b:Report):String=buildString {
        appendLine("COREMETRICS | TWO-RUN A/B REPORT")
        appendLine("A: #${a.session.id} ${a.session.label} / ${a.session.configuration}")
        appendLine("B: #${b.session.id} ${b.session.label} / ${b.session.configuration}")
        appendLine("QUALITY A: ${a.quality.score}/100; B: ${b.quality.score}/100")
        appendLine("Manual graphics driver A/B: "+
            (a.session.graphicsDriver?:"unknown")+" / "+
            (b.session.graphicsDriver?:"unknown"))
        appendLine("Manual resolution scale A/B: "+
            (a.session.resolutionScale?:"unknown")+" / "+
            (b.session.resolutionScale?:"unknown"))
        if(a.session.graphicsDriver!=b.session.graphicsDriver)
            appendLine("CAUTION: Different or unknown driver tags; comparison is not causal.")
        if(a.session.resolutionScale!=b.session.resolutionScale)
            appendLine("CAUTION: Different or unknown resolution tags.")
        if(a.skippedWarmUpSeconds>0 || b.skippedWarmUpSeconds>0)
            appendLine("Warm-up excluded after first sample: A "+
                a.skippedWarmUpSeconds+" s, B "+b.skippedWarmUpSeconds+" s.")
        appendLine("Power status A/B: ${a.energy.powerCondition.label} / "+
            b.energy.powerCondition.label)
        if(a.energy.powerCondition!=PowerCondition.DISCHARGING ||
            b.energy.powerCondition!=PowerCondition.DISCHARGING)
            appendLine("CAUTION: Charging/external/unknown conditions limit battery comparisons.")
        fun delta(first:Double?,second:Double?,unit:String):String =
            if(first!=null && second!=null)fmt(second-first)+unit else "unavailable"
        appendLine("B − A mean CPU temperature: " +
            delta(a.meanCpuC,b.meanCpuC," °C"))
        appendLine("B − A mean GPU temperature: " +
            delta(a.meanGpuC,b.meanGpuC," °C"))
        appendLine("B − A mean battery-terminal watts: " +
            delta(a.energy.meanDischargeW,b.energy.meanDischargeW," W"))
        appendLine("B − A integrated energy (durations may differ): " +
            delta(a.energy.wh,b.energy.wh," Wh"))
        appendLine("B − A normalized battery discharge: " +
            delta(a.energy.milliWhPerMinute,b.energy.milliWhPerMinute," mWh/min"))
        appendLine("B − A time-weighted discharge: " +
            delta(a.energy.weightedMeanW,b.energy.weightedMeanW," W"))
        if(a.elapsedSeconds>0.0 && b.elapsedSeconds>0.0 &&
            maxOf(a.elapsedSeconds,b.elapsedSeconds)/
                minOf(a.elapsedSeconds,b.elapsedSeconds)>1.2)
            appendLine("CAUTION: Different recording durations (>20%).")
        if(a.meanGpuLoad!=null && b.meanGpuLoad!=null &&
            abs(a.meanGpuLoad-b.meanGpuLoad)>10.0)
            appendLine("CAUTION: GPU workloads differ by >10 percentage points.")
        if(a.quality.labelConflict || b.quality.labelConflict)
            appendLine("CAUTION: At least one manual configuration label conflicts with observed caps.")
        if(a.energy.coveragePct<80 || b.energy.coveragePct<80)
            appendLine("CAUTION: Discharge energy has less than 80% valid coverage.")
        appendLine("A observed limits: ${a.quality.observedCeilings}")
        appendLine("B observed limits: ${b.quality.observedCeilings}")
        appendLine("A final-minute temperature trend: ${a.thermal.state}")
        appendLine("B final-minute temperature trend: ${b.thermal.state}")
        appendLine("FPS per watt: unavailable until presented frame data is verified.")
        appendLine("A/B is observational. Different gameplay, duration, charging state, " +
            "fan mode or warm-up confound comparisons; do not infer which is faster.")
        appendLine()
        appendLine("---- SESSION A ----")
        append(text(a))
        appendLine()
        appendLine("---- SESSION B ----")
        append(text(b))
    }
}
