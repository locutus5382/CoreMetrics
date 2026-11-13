package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.max

/**
 * Share/export data prepared from the SAME existing analysis results that feed
 * the on-device scorecards. No additional sampling, interpolation or FPS claims.
 * This layer is JVM-testable and has no dependencies on Android/Compose/Canvas.
 */
object ScorecardModel {
    data class ValueRow(
        val label:String, val left:String, val right:String?=null, val delta:String?=null
    )
    data class Trace(val elapsedMs:Long, val value:Double?)
    data class Series(
        val label:String, val samples:List<Trace>, val gapLimitMs:Long
    )
    data class Card(
        val type:Type, val title:String, val subtitle:String, val range:String,
        val badge:String, val badgesAreCaution:Boolean,
        val rows:List<ValueRow>, val series:List<Series>,
        val graphStartMs:Long, val graphEndMs:Long,
        val footnotes:List<String>,
        val sourceDescription:String
    )
    enum class Type { SINGLE, AB }
    fun formatted(v:Double?,unit:String="",decimals:Int=1):String =
        v?.takeIf { it.isFinite() }?.let{
            String.format(Locale.US,"%."+decimals+"f",it)+unit
        } ?: "Unavailable"
    private fun elapsed(ms:Long):String {
        val total=(ms/1000L).coerceAtLeast(0L)
        return String.format(Locale.US,"%02d:%02d",total/60,total%60)
    }
    private fun points(
        raw:List<SessionStore.Point>,originMs:Long,
        metric:SessionDiagnosticsAnalyzer.Metric
    ):List<Trace> =
        raw.map { p -> Trace(p.timeMs-originMs,
            SessionDiagnosticsAnalyzer.value(metric,p)) }

    fun single(bundle:SegmentAnalysis.Bundle):Card {
        require(bundle.validForReport) { "Insufficient data for selected PNG scorecard" }
        val session=bundle.session
        val result=bundle.diagnostics
        val energy=bundle.benchmark.energy
        val start=bundle.range.startMs-bundle.sourceOriginMs
        val end=bundle.range.endMs-bundle.sourceOriginMs
        val originalFirst=bundle.points.first().timeMs
        val rows=listOf(
            ValueRow("CPU temperature P50 / P95",
                formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.CPU_TEMP).median," °C")+
                " / "+formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.CPU_TEMP).p95," °C")),
            ValueRow("GPU temperature P50 / P95",
                formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.GPU_TEMP).median," °C")+
                " / "+formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.GPU_TEMP).p95," °C")),
            ValueRow("CPU / GPU load P50",
                formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.CPU_LOAD).median,"%")+
                " / "+formatted(result.distribution(SessionDiagnosticsAnalyzer.Metric.GPU_LOAD).median,"%")),
            ValueRow("Mean battery discharge (time-weighted)",
                formatted(energy.weightedMeanW," W",2)),
            ValueRow("Discharge coverage",
                formatted(energy.coveragePct,"%",0)+" / "+
                energy.powerCondition.label),
            ValueRow("Telemetry integrity",
                bundle.benchmark.quality.score.toString()+"/100 · "+
                bundle.benchmark.quality.gapCount.toString()+" gaps"),
            ValueRow("CPU within ±2°C of window P50",
                bundle.stability.firstOrNull{it.title=="CPU temperature"}
                    ?.percentOfValidTime?.let{formatted(it,"%",0)}
                    ?: "Unavailable")
        )
        val bad=energy.coveragePct<80.0 ||
            bundle.benchmark.quality.coveragePct<85.0 ||
            session.status !in setOf("COMPLETED","IMPORTED_COMPLETED")
        return Card(
            type=Type.SINGLE,title="SINGLE-SESSION DIAGNOSTICS",
            subtitle="Session #"+session.id+" | "+session.label+
                " | "+session.configuration,
            range=elapsed(start)+" - "+elapsed(end)+
                " of original recording",badge=if(bad)
                "CHECK RECORDING QUALITY" else "MEASURED WINDOW",
            badgesAreCaution=bad,rows=rows,
            series=listOf(
                Series("CPU temperature",points(bundle.points,bundle.sourceOriginMs,
                    SessionDiagnosticsAnalyzer.Metric.CPU_TEMP),result.gapLimitMs),
                Series("GPU temperature",points(bundle.points,bundle.sourceOriginMs,
                    SessionDiagnosticsAnalyzer.Metric.GPU_TEMP),result.gapLimitMs)
            ),
            graphStartMs=start,graphEndMs=max(start+1L,end),
            footnotes=listOf(
                "Source samples: "+bundle.points.size+
                    " | observed first: "+elapsed(originalFirst-bundle.sourceOriginMs)+
                    " | actual sample coverage: "+
                    formatted(bundle.benchmark.quality.coveragePct,"%",0),
                "P95 is an upper-tail sample percentile. It is not a measure of FPS or stutter.",
                "Battery watts are terminal discharge measurements, not CPU/GPU chip power.",
                "Within ±2°C of median is not thermal equilibrium. Gaps are not interpolated."
            ),
            sourceDescription="CoreMetrics v0.9.0 · Local/offline · Original data unchanged"
        )
    }

    fun comparison(result:CompareStudioAnalysis.Result):Card {
        require(result.observationsUsable) {
            "Insufficient A/B source samples for selected PNG scorecard"
        }
        val a=result.a;val b=result.b
        val metrics=listOf(
            "CPU temp P50" to SessionDiagnosticsAnalyzer.Metric.CPU_TEMP,
            "CPU temp P95" to SessionDiagnosticsAnalyzer.Metric.CPU_TEMP,
            "GPU temp P50" to SessionDiagnosticsAnalyzer.Metric.GPU_TEMP,
            "GPU temp P95" to SessionDiagnosticsAnalyzer.Metric.GPU_TEMP,
            "CPU load P50" to SessionDiagnosticsAnalyzer.Metric.CPU_LOAD,
            "GPU load P50" to SessionDiagnosticsAnalyzer.Metric.GPU_LOAD
        )
        val rows=metrics.map{(name,metric)->
            val p95=name.endsWith("P95")
            val unit=if(metric==SessionDiagnosticsAnalyzer.Metric.CPU_LOAD||
                metric==SessionDiagnosticsAnalyzer.Metric.GPU_LOAD)"%" else " °C"
            val left=if(p95)a.p95(metric) else a.median(metric)
            val right=if(p95)b.p95(metric) else b.median(metric)
            ValueRow(name,formatted(left,unit),formatted(right,unit),
                CompareStudioAnalysis.delta(left,right)?.let{
                    (if(it>0) "+" else "")+formatted(it,unit)
                } ?: "Unavailable")
        }+ValueRow("Mean battery discharge",
            formatted(a.benchmark.energy.weightedMeanW," W",2),
            formatted(b.benchmark.energy.weightedMeanW," W",2),
            CompareStudioAnalysis.delta(a.benchmark.energy.weightedMeanW,
                b.benchmark.energy.weightedMeanW)?.let{
                (if(it>0)"+" else "")+formatted(it," W",2)
            } ?: "Unavailable")
        val caution=result.warnings.firstOrNull()?.let{
            "Caution (1 of "+result.warnings.size+"): "+it
        } ?: "No measured differences flagged; scene equivalence still unverified."
        val notes=listOf(
            "Same elapsed range does NOT prove equivalent scenes, workload or FPS.",
            "Deltas are B minus A. Lower power alone is not proof of higher game efficiency.",
            caution,
            "A/B discharge coverage: "+
                formatted(a.benchmark.energy.coveragePct,"%",0)+" / "+
                formatted(b.benchmark.energy.coveragePct,"%",0)+
                (if(result.warnings.size>1)
                    " · plus "+(result.warnings.size-1)+" cautions in TXT" else "")
        )
        return Card(
            type=Type.AB,title="A/B DIAGNOSTIC COMPARISON",
            subtitle="A #"+a.session.id+" ("+a.session.configuration+")  /  B #"+
                b.session.id+" ("+b.session.configuration+")",
            range=elapsed(result.window.startElapsedMs)+" - "+
                elapsed(result.window.endElapsedMs)+" · shared elapsed time",
            badge=result.evidenceLabel,
            badgesAreCaution=true,rows=rows,
            series=listOf(
                Series("CPU temp A",points(a.points,a.originMs,
                    SessionDiagnosticsAnalyzer.Metric.CPU_TEMP),a.diagnostics.gapLimitMs),
                Series("CPU temp B",points(b.points,b.originMs,
                    SessionDiagnosticsAnalyzer.Metric.CPU_TEMP),b.diagnostics.gapLimitMs)
            ),graphStartMs=result.window.startElapsedMs,
            graphEndMs=result.window.endElapsedMs,
            footnotes=notes,
            sourceDescription="CoreMetrics v0.9.0 · Observational comparison, not measured FPS"
        )
    }
}
