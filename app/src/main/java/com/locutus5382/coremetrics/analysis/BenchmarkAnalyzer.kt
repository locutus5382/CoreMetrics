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
    data class Energy(
        val wh:Double?, val measuredSeconds:Double, val coveragePct:Double,
        val meanDischargeW:Double?
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
        val frames:FrameEfficiency
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
            duration<55.0 -> "warming or cooling window (too short for steady state)"
            slope==null -> "unavailable"
            abs(slope)<=0.8 && maxTemp-minTemp<=2.5 ->
                "candidate steady-state (60-second thermal window)"
            slope>0.8 -> "warming; steady state not demonstrated"
            slope< -0.8 -> "cooling; steady state not demonstrated"
            else -> "variable; steady state not demonstrated"
        }
        return Thermal(state,slope,mean,peak,samples.size,duration)
    }

    /** Integrates only valid adjacent discharge readings. No interpolation
     * over large gaps, charging or missing data. No extrapolation to full battery.
     */
    fun analyze(session:SessionStore.Session,raw:List<SessionStore.Point>):Report {
        val pts=raw.sortedBy { it.timeMs }
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
        val validPowers=mutableListOf<Double>()
        fun discharge(p:SessionStore.Point):Double? =
            p.batteryWatts?.takeIf { it.isFinite() && it>0.0 &&
                it<100.0 && p.batteryPowerStatus?.startsWith("DISCHARGING")==true }
        for(p in pts) discharge(p)?.let { validPowers.add(it) }
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
        val capGroups=pts.map { SessionRegimeAnalyzer.key(it.observedCpuLimits) }
            .filterNot { it=="P3:?|P7:?" }
            .distinct()
        val labels=capGroups.map{SessionRegimeAnalyzer.label(it)}
        val observed=if(labels.isEmpty())"unavailable" else labels.joinToString(" | ")
        val normalized=session.configuration.lowercase(Locale.US)
        val conflict=labels.isNotEmpty() && (
            (normalized.contains("stock") && labels.any {
                it.startsWith("Medium UC") || it.startsWith("Large UC") }) ||
            (normalized.contains("medium") && labels.any {it.startsWith("Large UC")}) ||
            (normalized.contains("large") && labels.any {it.startsWith("Medium UC")})
        )
        val reasons=mutableListOf<String>()
        var score=100
        if(session.status !in setOf("COMPLETED","IMPORTED")) {
            score-=25
            reasons+="Session status is ${session.status}; recording may be incomplete"
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
        if(energyCoverage<60.0) {
            score-=10
            reasons+="Discharge-energy coverage under 60% (or charging)"
        }
        if(conflict) {
            score-=20
            reasons+="Manual profile label conflicts with observed CPU ceilings"
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
                energyCoverage,mean(validPowers)),
            thermal(pts),frames)
    }

    fun text(report:Report):String=buildString {
        val r=report
        appendLine("COREMETRICS | BENCHMARK ASSESSMENT (OBSERVATIONAL)")
        appendLine("Session #${r.session.id}: ${r.session.label} · ${r.session.configuration}")
        appendLine("Session status: ${r.session.status}; samples ${r.sampleCount}")
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
        appendLine("Thermal state: ${r.thermal.state}; " +
            "slope ${fmt(r.thermal.slopeCPerMin)} °C/min")
        appendLine("LOAD AND BATTERY NET")
        appendLine("Mean CPU/GPU load: ${fmt(r.meanCpuLoad)}% / ${fmt(r.meanGpuLoad)}%")
        appendLine("Mean battery discharge: ${fmt(r.energy.meanDischargeW)} W")
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
        appendLine("A observed limits: ${a.quality.observedCeilings}")
        appendLine("B observed limits: ${b.quality.observedCeilings}")
        appendLine("A steady-state: ${a.thermal.state}")
        appendLine("B steady-state: ${b.thermal.state}")
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
