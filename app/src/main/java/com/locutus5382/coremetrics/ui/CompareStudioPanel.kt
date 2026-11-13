package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Metric
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * v0.8.8: a single bounded shared elapsed window for every A/B number.
 * Active GPU filtering only changes visual traces, not numerical comparisons.
 * A/B sessions are NEVER declared synchronized game-world scenes.
 */
@Composable
internal fun CompareStudioPanel(
    sessionA:SessionStore.Session,pointsA:List<SessionStore.Point>,
    sessionB:SessionStore.Session,pointsB:List<SessionStore.Point>,
    activeGpuFilter:Boolean,showFullTimelines:Boolean,
    onToggleFullTimeline:(Boolean)->Unit,
    initialSkipWarmUp:Boolean,
    cursorFraction:Float,onCursorFraction:(Float)->Unit,
    onExport:(CompareStudioAnalysis.Window)->Unit
) {
    if(pointsA.size<sessionA.count || pointsB.size<sessionB.count) {
        Text("A/B analysis unavailable: a source exceeds the 12,000-sample "+
            "in-memory limit. Partial recordings must not be presented "+
            "as complete comparisons.",color=solarOrange,
            style=MaterialTheme.typography.bodySmall)
        return
    }
    val shared=remember(pointsA,pointsB) {
        CompareStudioAnalysis.sharedDuration(pointsA,pointsB)
    }
    if(shared<20_000L) {
        Text("A/B comparison requires at least 20 seconds of shared recorded duration.",
            color=solarOrange,style=MaterialTheme.typography.bodySmall)
        return
    }
    val total=shared/1000f
    val minimum=min(20f,total)
    val skipAllowed=total>=50f
    val initialStart=if(initialSkipWarmUp && skipAllowed)30f else 0f
    var draftStart by remember(sessionA.id,sessionB.id,pointsA,pointsB,initialSkipWarmUp) {
        mutableFloatStateOf(initialStart)
    }
    var draftEnd by remember(sessionA.id,sessionB.id,pointsA,pointsB,initialSkipWarmUp) {
        mutableFloatStateOf(total)
    }
    var preset by remember(sessionA.id,sessionB.id,pointsA,pointsB,initialSkipWarmUp) {
        mutableStateOf(if(initialStart>0f)"Warm-up" else "Overlap")
    }
    fun toWindow(start:Float,end:Float):CompareStudioAnalysis.Window {
        val a=(start*1000).roundToLong().coerceIn(0L,shared-1)
        val b=(end*1000).roundToLong().coerceIn(a+1,shared)
        return CompareStudioAnalysis.Window(a,b)
    }
    var applied by remember(sessionA.id,sessionB.id,pointsA,pointsB,initialSkipWarmUp) {
        mutableStateOf(toWindow(initialStart,total))
    }
    fun changeRange(from:Float,to:Float,name:String="Custom") {
        val start=from.coerceIn(0f,(total-minimum).coerceAtLeast(0f))
        val end=to.coerceIn(start+minimum,total)
        draftStart=start;draftEnd=end;applied=toWindow(start,end);preset=name
    }
    val computed by produceState<CompareStudioAnalysis.Result?>(
        initialValue=null,sessionA,pointsA,sessionB,pointsB,applied
    ) {
        value=withContext(Dispatchers.Default) {
            runCatching {
                CompareStudioAnalysis.analyze(sessionA,pointsA,sessionB,pointsB,applied)
            }.getOrNull()
        }
    }
    val result=computed?.takeIf {
        it.window==applied && it.a.session.id==sessionA.id &&
            it.b.session.id==sessionB.id
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("A/B DIAGNOSTIC SCORECARD · v0.8.8",
                color=iceBlue,fontWeight=FontWeight.Bold)
            Text("Same elapsed-time interval, not necessarily the same game scene. "+
                "All Δ values use B − A.",
                color=coreSecondary,style=MaterialTheme.typography.bodySmall)
            if(result==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                val ready=result.observationsUsable
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(result.evidenceLabel,
                        color=if(result.warnings.isEmpty()&&ready)coreMint else solarOrange,
                        fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.bodySmall)
                    Text(stamp(applied.startElapsedMs)+"–"+
                            stamp(applied.endElapsedMs)+" · "+
                            result.a.points.size+" A / "+result.b.points.size+" B",
                        color=coreSecondary,style=MaterialTheme.typography.bodySmall)
                }
                if(ready) CompareScoreRows(result)
                else Text("Widen the range or choose sessions with more valid samples.",
                    color=solarOrange,style=MaterialTheme.typography.bodySmall)
                Text("Observed P3/P7 limits · A: "+
                    result.a.observedCaps.joinToString(" / ").ifEmpty{"unavailable"}+
                    " · B: "+result.b.observedCaps.joinToString(" / ")
                        .ifEmpty{"unavailable"},
                    color=coreSecondary,style=MaterialTheme.typography.bodySmall)
                var showWarnings by remember(sessionA.id,sessionB.id) {
                    mutableStateOf(false)
                }
                if(result.warnings.isNotEmpty()) {
                    TextButton(onClick={showWarnings=!showWarnings}) {
                        Text(if(showWarnings)"Hide comparability cautions ("+
                                result.warnings.size+")"
                            else "Show "+result.warnings.size+" comparability cautions",
                            color=solarOrange)
                    }
                    if(showWarnings) result.warnings.forEach {warning ->
                        Text("• "+warning,color=coreSecondary,
                            style=MaterialTheme.typography.labelSmall)
                    }
                } else Text("No major differences detected by available metadata; "+
                    "scene equivalence and frame-rate performance remain unverified.",
                    color=coreSecondary,style=MaterialTheme.typography.labelSmall)
                Text("A/B integrity: "+result.a.benchmark.quality.score+"/100 and "+
                    result.b.benchmark.quality.score+"/100 · discharge coverage "+
                    fmt(result.a.benchmark.energy.coveragePct)+"% / "+
                    fmt(result.b.benchmark.energy.coveragePct)+"%",
                    color=coreSecondary,style=MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick={onExport(applied)},
                    enabled=ready &&
                        pointsA.size>=sessionA.count && pointsB.size>=sessionB.count) {
                    Text("Export selected A/B report")
                }
                if(pointsA.size<sessionA.count || pointsB.size<sessionB.count)
                    Text("Original session exceeds 12,000 loaded samples; "+
                        "partial A/B export is disabled.",
                        color=solarOrange,style=MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.fillMaxWidth().pureGlass(14).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("SHARED-TIME RANGE SELECTION",color=iceBlue,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=preset=="Overlap",onClick={
                    changeRange(0f,total,"Overlap")
                },label={Text("Shared overlap")})
                FilterChip(selected=preset=="Warm-up",onClick={
                    changeRange(30f,total,"Warm-up")
                },enabled=skipAllowed,label={Text("Skip first 30s")})
                FilterChip(selected=preset=="Custom",onClick={preset="Custom"},
                    label={Text("Custom range")})
            }
            RangeSlider(value=draftStart..draftEnd,
                onValueChange={v ->
                    draftStart=v.start.coerceIn(0f,(total-minimum).coerceAtLeast(0f))
                    draftEnd=v.endInclusive.coerceIn(draftStart+minimum,total)
                    preset="Custom"
                },onValueChangeFinished={
                    applied=toWindow(draftStart,draftEnd)
                },valueRange=0f..total,modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Start "+stamp((draftStart*1000).roundToLong()),
                    color=coreText,style=MaterialTheme.typography.labelMedium)
                Text("Duration "+stamp(((draftEnd-draftStart)*1000).roundToLong()),
                    color=solarGold,style=MaterialTheme.typography.labelMedium)
                Text("End "+stamp((draftEnd*1000).roundToLong()),
                    color=coreText,style=MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={changeRange(draftStart-5f,draftEnd)},
                    enabled=draftStart>0.1f) {Text("Start −5s")}
                OutlinedButton(onClick={changeRange(draftStart+5f,draftEnd)},
                    enabled=draftEnd-draftStart>minimum+0.1f) {Text("Start +5s")}
                OutlinedButton(onClick={changeRange(draftStart,draftEnd-5f)},
                    enabled=draftEnd-draftStart>minimum+0.1f) {Text("End −5s")}
                OutlinedButton(onClick={changeRange(draftStart,draftEnd+5f)},
                    enabled=draftEnd<total-0.1f) {Text("End +5s")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Show unmatched full timelines (visual only)",
                    Modifier.weight(1f),color=coreSecondary,
                    style=MaterialTheme.typography.bodySmall)
                Switch(showFullTimelines,onCheckedChange=onToggleFullTimeline)
            }
            Text("Range handles update instantly; calculations run after release. "+
                "Full timelines show unmatched tails but never include them in A/B deltas.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        val displayMax=if(showFullTimelines) max(
            CompareStudioAnalysis.duration(pointsA),
            CompareStudioAnalysis.duration(pointsB)) else shared
        val chartA=remember(pointsA,activeGpuFilter,displayMax) {
            pointsA.filter {
                it.timeMs-pointsA.first().timeMs<=displayMax &&
                    (!activeGpuFilter || (it.gpuLoad?:-1.0)>=15.0)
            }
        }
        val chartB=remember(pointsB,activeGpuFilter,displayMax) {
            pointsB.filter {
                it.timeMs-pointsB.first().timeMs<=displayMax &&
                    (!activeGpuFilter || (it.gpuLoad?:-1.0)>=15.0)
            }
        }
        if(chartA.size>=2&&chartB.size>=2) {
            CompareScrubPanel(chartA,chartB,
                fraction=cursorFraction,onFractionChange=onCursorFraction,
                originalStartA=pointsA.first().timeMs,
                originalStartB=pointsB.first().timeMs,
                axisDurationMs=displayMax,
                selectedStartElapsedMs=(draftStart*1000).roundToLong(),
                selectedEndElapsedMs=(draftEnd*1000).roundToLong())
        } else Text("GPU≥15% chart filter removed too many samples. "+
            "Disable the filter to view the full measured chronology; "+
            "the scorecard always uses the complete selected window.",
            color=solarOrange,style=MaterialTheme.typography.bodySmall)
        Text(if(activeGpuFilter)
            "DISPLAY FILTER ONLY: GPU load ≥15%. All comparison statistics "+
                "still use all recorded points within the selected window."
            else "Charts show unfiltered recorded values with original elapsed-time origins.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompareScoreRows(result:CompareStudioAnalysis.Result) {
    val a=result.a;val b=result.b
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompareTextCell("MEASUREMENT",1.6f,true)
            CompareTextCell("A",.7f,true)
            CompareTextCell("B",.7f,true)
            CompareTextCell("B − A",.8f,true)
        }
        fun rows():List<Pair<String,Pair<Double?,Double?>>> = listOf(
            "CPU median °C" to (a.median(Metric.CPU_TEMP) to b.median(Metric.CPU_TEMP)),
            "CPU P95 °C" to (a.p95(Metric.CPU_TEMP) to b.p95(Metric.CPU_TEMP)),
            "GPU median °C" to (a.median(Metric.GPU_TEMP) to b.median(Metric.GPU_TEMP)),
            "GPU P95 °C" to (a.p95(Metric.GPU_TEMP) to b.p95(Metric.GPU_TEMP)),
            "Power avg W" to (a.benchmark.energy.weightedMeanW to
                b.benchmark.energy.weightedMeanW),
            "CPU load P50 %" to (a.median(Metric.CPU_LOAD) to b.median(Metric.CPU_LOAD)),
            "GPU load P50 %" to (a.median(Metric.GPU_LOAD) to b.median(Metric.GPU_LOAD))
        )
        rows().forEach { (label,v) ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                CompareTextCell(label,1.6f,false)
                CompareTextCell(fmt(v.first),.7f,false)
                CompareTextCell(fmt(v.second),.7f,false)
                CompareTextCell(signed(CompareStudioAnalysis.delta(v.first,v.second)),.8f,false)
            }
        }
        Text("Power = integrated time-weighted battery discharge, "+
            "not median sample power. All other P50/P95 values are distributions "+
            "of valid recorded observations.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun RowScope.CompareTextCell(s:String,weight:Float,header:Boolean) {
    Text(s,Modifier.weight(weight),color=if(header)coreSecondary else coreText,
        style=MaterialTheme.typography.bodySmall,maxLines=1)
}
private fun fmt(v:Double?):String =
    v?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.US,"%.2f",it) } ?: "—"
private fun signed(v:Double?):String =
    v?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.US,"%+.2f",it) } ?: "—"
private fun stamp(ms:Long):String {
    val sec=(ms/1000L).coerceAtLeast(0)
    return String.format(Locale.US,"%02d:%02d",sec/60,sec%60)
}
