package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs

/** Offline selected-segment calculations; never modifies the source recording. */
object SegmentAnalysis {
    data class Range(val startMs:Long,val endMs:Long) {
        init { require(endMs>startMs) { "Selection end must follow start" } }
        val durationSeconds:Double get()=(endMs-startMs)/1000.0
    }
    data class Stability(
        val title:String, val threshold:String, val percentOfValidTime:Double?,
        val measuredSeconds:Double, val meanAbsoluteStep:Double?,
        val comparedSteps:Int, val typicalStepSeconds:Double?,
        val validSamples:Int, val unit:String
    )
    data class Bundle(
        val session:SessionStore.Session, val sourceOriginMs:Long,
        val range:Range, val points:List<SessionStore.Point>,
        val benchmark:BenchmarkAnalyzer.Report,
        val diagnostics:SessionDiagnosticsAnalyzer.Result,
        val stability:List<Stability>
    ) {
        val validForReport:Boolean get()=points.size>=6 &&
            (points.last().timeMs-points.first().timeMs)>=20_000L
    }
    fun fullRange(raw:List<SessionStore.Point>):Range? =
        raw.takeIf { it.size>=2 && it.last().timeMs>it.first().timeMs }
            ?.let { Range(it.first().timeMs,it.last().timeMs) }

    fun window(raw:List<SessionStore.Point>,range:Range):List<SessionStore.Point> =
        raw.filter { it.timeMs in range.startMs..range.endMs }

    fun create(session:SessionStore.Session,original:List<SessionStore.Point>,
        range:Range):Bundle {
        require(original.isNotEmpty()) { "Source session has no samples" }
        val origin=original.first().timeMs
        val selected=window(original,range)
        require(selected.isNotEmpty()) { "Selected range has no samples" }
        val report=BenchmarkAnalyzer.analyze(session,selected,0)
        val diagnostics=SessionDiagnosticsAnalyzer.analyze(selected,report)
        val stability=listOf(
            stability(selected,diagnostics,SessionDiagnosticsAnalyzer.Metric.CPU_TEMP,
                "CPU temperature","±2.0 °C",2.0,false),
            stability(selected,diagnostics,SessionDiagnosticsAnalyzer.Metric.GPU_TEMP,
                "GPU temperature","±2.0 °C",2.0,false),
            stability(selected,diagnostics,SessionDiagnosticsAnalyzer.Metric.DISCHARGE_W,
                "Discharge power","±15% of median",0.15,true),
            stability(selected,diagnostics,SessionDiagnosticsAnalyzer.Metric.CPU_LOAD,
                "CPU load","±10 percentage points",10.0,false),
            stability(selected,diagnostics,SessionDiagnosticsAnalyzer.Metric.GPU_LOAD,
                "GPU load","±10 percentage points",10.0,false)
        )
        return Bundle(session,origin,range,selected,report,diagnostics,stability)
    }

    /**
     * Stable time: both endpoints of each valid contiguous interval must be
     * within the median band. Missing data, charging and gaps never count.
     * Volatility: mean absolute adjacent difference per sample STEP, not /s.
     */
    private fun stability(points:List<SessionStore.Point>,
        result:SessionDiagnosticsAnalyzer.Result,
        metric:SessionDiagnosticsAnalyzer.Metric,title:String,threshold:String,
        tolerance:Double,relative:Boolean):Stability {
        val median=result.distribution(metric).median
        val limit=median?.let { if(relative)abs(it)*tolerance else tolerance }
        var eligibleMs=0L;var stableMs=0L
        var n=0;var deltaTotal=0.0
        val intervals=mutableListOf<Long>()
        for(i in 1 until points.size) {
            val a=points[i-1];val b=points[i]
            val dt=b.timeMs-a.timeMs
            if(dt<=0L || dt>result.gapLimitMs)continue
            val va=SessionDiagnosticsAnalyzer.value(metric,a) ?:continue
            val vb=SessionDiagnosticsAnalyzer.value(metric,b) ?:continue
            eligibleMs+=dt;n++;deltaTotal+=abs(vb-va);intervals.add(dt)
            if(median!=null && limit!=null &&
                abs(va-median)<=limit+1e-9 && abs(vb-median)<=limit+1e-9)
                stableMs+=dt
        }
        val adequate=eligibleMs>=30_000L && n>=4
        return Stability(title,threshold,
            if(adequate)100.0*stableMs/eligibleMs else null,
            eligibleMs/1000.0,
            if(n>0)deltaTotal/n else null,n,
            intervals.sorted().takeIf{it.isNotEmpty()}
                ?.let{it[it.size/2]/1000.0},
            result.distribution(metric).observations,metric.unit)
    }

    fun textReport(bundle:Bundle):String=buildString {
        require(bundle.validForReport) {
            "Selection requires >=6 source samples spanning >=20 seconds"
        }
        val s=bundle.session;val r=bundle.range
        val from=(r.startMs-bundle.sourceOriginMs)/1000.0
        val to=(r.endMs-bundle.sourceOriginMs)/1000.0
        appendLine("COREMETRICS v"+ExportVersion.VERSION+" | SELECTED SESSION SEGMENT")
        appendLine("Original session ID: ${s.id}; status: ${s.status}")
        appendLine("Session: ${s.label}; game note: ${s.note ?: "unavailable"}")
        appendLine("Configuration: ${s.configuration}")
        appendLine("Graphics driver: ${s.graphicsDriver ?: "unavailable"}")
        appendLine("Resolution scale: ${s.resolutionScale ?: "unavailable"}")
        appendLine("Selection from ORIGINAL first sample: "+
            "${fmt(from)} to ${fmt(to)} seconds; inclusive timestamp bounds")
        appendLine("Absolute selection bounds: ${r.startMs} to ${r.endMs} ms")
        appendLine("Selected sample count: ${bundle.points.size}; first / last "+
            "observed epoch ms: ${bundle.points.first().timeMs} / "+
            "${bundle.points.last().timeMs}")
        appendLine("Selection span: ${fmt(r.durationSeconds)} s; observed "+
            "sample span: "+fmt((bundle.points.last().timeMs-
                bundle.points.first().timeMs)/1000.0)+" s")
        appendLine("Original session unchanged. No interpolation at boundaries, "+
            "missing observations or recording gaps.")
        appendLine()
        append(BenchmarkAnalyzer.text(bundle.benchmark))
        append(SessionDiagnosticsAnalyzer.text(bundle.diagnostics,bundle.sourceOriginMs))
        appendLine()
        appendLine("STABILITY AND VOLATILITY (SELECTED SEGMENT)")
        for(stat in bundle.stability) {
            appendLine(stat.title+": stable valid time "+
                (stat.percentOfValidTime?.let { fmt(it)+"%" } ?: "unavailable")+
                " (within ${stat.threshold} of sample median); measured "+
                fmt(stat.measuredSeconds)+" s; mean absolute change "+
                (stat.meanAbsoluteStep?.let { fmt(it)+" ${stat.unit}/sample-step" }
                    ?: "unavailable")+
                "; valid pairs ${stat.comparedSteps}; typical step "+
                (stat.typicalStepSeconds?.let { fmt(it)+" s" } ?: "n/a"))
        }
        appendLine("Stable time requires at least 30 seconds of valid adjacent "+
            "intervals and four pairs. Both endpoints must qualify.")
        appendLine("Step volatility is not time-normalized: do not compare "+
            "results sampled at different intervals.")
        appendLine("Stability is not thermal equilibrium. No claims of verified "+
            "game smoothness, throttling causation or battery health.")
    }
    private fun fmt(v:Double)=String.format(Locale.US,"%.2f",v)
}
