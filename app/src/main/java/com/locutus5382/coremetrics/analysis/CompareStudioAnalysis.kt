package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * v0.8.8: offline, elapsed-time-aligned A/B observations.
 * Same elapsed interval DOES NOT establish equivalent gameplay or causality.
 * All analysis is performed on original samples (never the GPU-load chart filter).
 */
object CompareStudioAnalysis {
    data class Window(val startElapsedMs:Long,val endElapsedMs:Long) {
        init { require(startElapsedMs>=0 && endElapsedMs>startElapsedMs) }
        val seconds:Double get()=(endElapsedMs-startElapsedMs)/1000.0
    }
    data class Side(
        val session:SessionStore.Session,
        val originMs:Long,
        val points:List<SessionStore.Point>,
        val benchmark:BenchmarkAnalyzer.Report,
        val diagnostics:SessionDiagnosticsAnalyzer.Result,
        val observedCaps:List<String>
    ) {
        fun median(metric:SessionDiagnosticsAnalyzer.Metric):Double? =
            diagnostics.distribution(metric).median
        fun p95(metric:SessionDiagnosticsAnalyzer.Metric):Double? =
            diagnostics.distribution(metric).p95
    }
    data class Result(
        val window:Window,val a:Side,val b:Side,
        val sharedAvailableMs:Long,
        val observationsUsable:Boolean,
        val warnings:List<String>
    ) {
        val evidenceLabel:String get()=when {
            !observationsUsable -> "INSUFFICIENT DATA"
            warnings.isNotEmpty() -> "LIMITED COMPARABILITY"
            else -> "CONDITIONS UNVERIFIED"
        }
    }
    fun duration(points:List<SessionStore.Point>):Long =
        if(points.size>=2)(points.last().timeMs-points.first().timeMs).coerceAtLeast(0L) else 0L
    fun sharedDuration(a:List<SessionStore.Point>,b:List<SessionStore.Point>):Long =
        min(duration(a),duration(b))
    fun selection(points:List<SessionStore.Point>,window:Window):List<SessionStore.Point> {
        val start=points.firstOrNull()?.timeMs ?: return emptyList()
        return points.filter {
            val t=it.timeMs-start
            t>=window.startElapsedMs && t<=window.endElapsedMs
        }
    }
    private fun side(
        session:SessionStore.Session,original:List<SessionStore.Point>,window:Window
    ):Side {
        val source=selection(original,window)
        require(source.isNotEmpty()) { "No samples in selected window" }
        val report=BenchmarkAnalyzer.analyze(session,source,0)
        val diag=SessionDiagnosticsAnalyzer.analyze(source,report)
        val caps=source.map { SessionRegimeAnalyzer.key(it.observedCpuLimits) }
            .filterNot { it.contains("?") }.distinct()
            .map { SessionRegimeAnalyzer.label(it) }
        return Side(session,original.first().timeMs,source,report,diag,caps)
    }
    fun analyze(
        sessionA:SessionStore.Session,originalA:List<SessionStore.Point>,
        sessionB:SessionStore.Session,originalB:List<SessionStore.Point>,window:Window
    ):Result {
        require(sessionA.id!=sessionB.id) { "Select different sessions" }
        val shared=sharedDuration(originalA,originalB)
        require(shared>=20_000L) { "Shared recording duration is too short" }
        require(window.endElapsedMs<=shared) {
            "Comparison must use the shared elapsed-time overlap"
        }
        val a=side(sessionA,originalA,window)
        val b=side(sessionB,originalB,window)
        val usable=a.points.size>=6 && b.points.size>=6 &&
            duration(a.points)>=20_000L && duration(b.points)>=20_000L
        val warnings=buildList {
            if(!usable)add("Too few valid timestamped samples or less than 20 s of measured time")
            if(sessionA.status !in setOf("COMPLETED","IMPORTED_COMPLETED") ||
                sessionB.status !in setOf("COMPLETED","IMPORTED_COMPLETED"))
                add("At least one recording was interrupted, ongoing or not fully completed")
            fun diff(metric:SessionDiagnosticsAnalyzer.Metric):Double? {
                val x=a.median(metric);val y=b.median(metric)
                return if(x!=null&&y!=null)abs(y-x) else null
            }
            // Different sampling cadences weight sample quantiles differently.
            val cadenceA=a.diagnostics.cadenceMs
            val cadenceB=b.diagnostics.cadenceMs
            if(cadenceA>0L && cadenceB>0L &&
                maxOf(cadenceA,cadenceB).toDouble()/
                minOf(cadenceA,cadenceB).toDouble()>=1.5)
                add("Sampling cadences differ substantially (A "+
                    cadenceA+" ms, B "+cadenceB+" ms); sample percentiles differ in resolution")
            val load=diff(SessionDiagnosticsAnalyzer.Metric.GPU_LOAD)
            if(load==null)add("GPU utilization is unavailable in one selected window")
            else if(load>10.0)add("Median GPU load differs by more than 10 percentage points")
            val cpuLoad=diff(SessionDiagnosticsAnalyzer.Metric.CPU_LOAD)
            if(cpuLoad!=null&&cpuLoad>10.0)
                add("Median CPU load differs by more than 10 percentage points")
            val firstA=a.points.firstOrNull()?.cpu
            val firstB=b.points.firstOrNull()?.cpu
            if(firstA!=null&&firstB!=null&&firstA.isFinite()&&firstB.isFinite() &&
                abs(firstA-firstB)>5.0)
                add("Initial measured CPU sensor temperature differs by over 5 °C")
            if(sessionA.graphicsDriver.isNullOrBlank() ||
                sessionB.graphicsDriver.isNullOrBlank() ||
                sessionA.resolutionScale.isNullOrBlank() ||
                sessionB.resolutionScale.isNullOrBlank())
                add("Graphics-driver or render-scale metadata is not recorded for both sessions")
            if(!sessionA.graphicsDriver.isNullOrBlank() &&
                !sessionB.graphicsDriver.isNullOrBlank() &&
                sessionA.graphicsDriver!=sessionB.graphicsDriver)
                add("Graphics driver tags differ")
            if(!sessionA.resolutionScale.isNullOrBlank() &&
                !sessionB.resolutionScale.isNullOrBlank() &&
                sessionA.resolutionScale!=sessionB.resolutionScale)
                add("Render scale tags differ")
            if(a.benchmark.energy.powerCondition!=BenchmarkAnalyzer.PowerCondition.DISCHARGING ||
                b.benchmark.energy.powerCondition!=BenchmarkAnalyzer.PowerCondition.DISCHARGING)
                add("At least one window is not exclusively measured during battery discharge")
            if(a.benchmark.energy.coveragePct<80.0 || b.benchmark.energy.coveragePct<80.0)
                add("Less than 80% valid discharge coverage in at least one window")
            if(a.benchmark.quality.coveragePct<85.0 ||
                b.benchmark.quality.coveragePct<85.0)
                add("Telemetry timestamp coverage under 85% in at least one window")
            if(a.observedCaps.isEmpty()||b.observedCaps.isEmpty())
                add("CPU frequency caps were not available in both windows")
            if(a.observedCaps.size>1||b.observedCaps.size>1)
                add("CPU frequency ceilings changed within a selected window")
        }
        return Result(window,a,b,shared,usable,warnings)
    }
    fun delta(a:Double?,b:Double?):Double? =
        if(a!=null&&b!=null&&a.isFinite()&&b.isFinite())b-a else null

    fun report(result:Result):String=buildString {
        require(result.observationsUsable) {
            "Each selected window needs 6+ samples spanning 20+ seconds"
        }
        val r=result;val a=r.a;val b=r.b
        fun fmt(x:Double?,unit:String=""):String =
            x?.takeIf { it.isFinite() }
                ?.let { String.format(Locale.US,"%.2f",it)+unit } ?: "unavailable"
        appendLine("COREMETRICS v0.8.8 | SELECTED A/B DIAGNOSTIC COMPARISON")
        appendLine("Window: elapsed "+
            fmt(r.window.startElapsedMs/1000.0)+" to "+
            fmt(r.window.endElapsedMs/1000.0)+" s from each ORIGINAL recording start")
        appendLine("Shared measured timeline: "+fmt(r.sharedAvailableMs/1000.0)+" s")
        appendLine("Window selection is relative, not a synchronization of gameplay scenes")
        appendLine("A: Session #"+a.session.id+" / "+a.session.configuration+
            " / "+a.session.status+" / "+a.points.size+" samples")
        appendLine("B: Session #"+b.session.id+" / "+b.session.configuration+
            " / "+b.session.status+" / "+b.points.size+" samples")
        appendLine("A/B game notes: "+(a.session.note?:"none")+" / "+(b.session.note?:"none"))
        appendLine("A/B driver tags: "+(a.session.graphicsDriver?:"unknown")+" / "+
            (b.session.graphicsDriver?:"unknown"))
        appendLine("A/B render scales: "+(a.session.resolutionScale?:"unknown")+" / "+
            (b.session.resolutionScale?:"unknown"))
        appendLine("Absolute first/last selected samples A: "+
            a.points.first().timeMs+" - "+a.points.last().timeMs)
        appendLine("Absolute first/last selected samples B: "+
            b.points.first().timeMs+" - "+b.points.last().timeMs)
        appendLine("Observed CPU limits A: "+a.observedCaps.joinToString(" | ").ifEmpty{"unavailable"})
        appendLine("Observed CPU limits B: "+b.observedCaps.joinToString(" | ").ifEmpty{"unavailable"})
        appendLine("Evidence status: "+r.evidenceLabel)
        r.warnings.forEach { appendLine("CAUTION: "+it) }
        appendLine("Matching duration or usage does not establish identical scenes, "+
            "equal performance, or underclocking causality.")
        appendLine()
        appendLine("METRIC                                      A             B          B - A")
        val metrics=listOf(
            SessionDiagnosticsAnalyzer.Metric.CPU_TEMP,
            SessionDiagnosticsAnalyzer.Metric.GPU_TEMP,
            SessionDiagnosticsAnalyzer.Metric.CPU_LOAD,
            SessionDiagnosticsAnalyzer.Metric.GPU_LOAD,
            SessionDiagnosticsAnalyzer.Metric.BUSIEST_CORE,
            SessionDiagnosticsAnalyzer.Metric.DISCHARGE_W
        )
        for(m in metrics) {
            val x=a.median(m);val y=b.median(m)
            appendLine(m.title+" median: "+fmt(x,m.unit)+" | "+fmt(y,m.unit)+
                " | "+fmt(delta(x,y),m.unit))
            val x95=a.p95(m);val y95=b.p95(m)
            appendLine(m.title+" P95: "+fmt(x95,m.unit)+" | "+fmt(y95,m.unit)+
                " | "+fmt(delta(x95,y95),m.unit))
        }
        val wa=a.benchmark.energy.weightedMeanW
        val wb=b.benchmark.energy.weightedMeanW
        appendLine("Time-weighted battery net discharge: "+fmt(wa," W")+" | "+
            fmt(wb," W")+" | "+fmt(delta(wa,wb)," W"))
        appendLine("Discharge coverage A/B: "+
            fmt(a.benchmark.energy.coveragePct,"%")+" / "+
            fmt(b.benchmark.energy.coveragePct,"%"))
        appendLine("Comparable power rate is W, not total Wh across unequal observed coverage.")
        appendLine()
        appendLine("---- SELECTED A: DETAIL ----")
        append(BenchmarkAnalyzer.text(a.benchmark))
        append(SessionDiagnosticsAnalyzer.text(a.diagnostics,a.originMs))
        appendLine()
        appendLine("---- SELECTED B: DETAIL ----")
        append(BenchmarkAnalyzer.text(b.benchmark))
        append(SessionDiagnosticsAnalyzer.text(b.diagnostics,b.originMs))
        appendLine()
        appendLine("Source sessions remain unchanged. No interpolation over recording gaps, "+
            "no game FPS or claim of better gameplay performance.")
    }
}
