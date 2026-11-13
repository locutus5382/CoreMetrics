package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Metric
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Distribution
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/** v0.8.6, single-session diagnostics only; read-only and no gameplay polling. */
@Composable
internal fun SessionDiagnosticsPanel(
    points: List<SessionStore.Point>,
    report: BenchmarkAnalyzer.Report,
    referenceStartMs: Long? = null,
    precomputed: SessionDiagnosticsAnalyzer.Result? = null
) {
    val analysis by produceState<SessionDiagnosticsAnalyzer.Result?>(
        initialValue = precomputed, points, report, precomputed
    ) {
        value = precomputed ?: withContext(Dispatchers.Default) {
            SessionDiagnosticsAnalyzer.analyze(points,report)
        }
    }
    Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ADVANCED SESSION DIAGNOSTICS · SELECTED WINDOW",
            color = iceBlue,fontWeight = FontWeight.Bold)
        Text("Offline evidence from the recorded session · no extra game-time polling.",
            color = coreSecondary,style = MaterialTheme.typography.labelSmall)
        val result = analysis
        if(result == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Calculating…",color=coreSecondary)
        } else {
            DiagnosticSummary(result,report)
            DiagnosticDistributionTable(result,referenceStartMs ?: points.firstOrNull()?.timeMs ?: 0L)
            DiagnosticExplorer(result,points,referenceStartMs)
            DiagnosticEvidence(result,report)
        }
    }
}

private fun fmtDiagnostic(v: Double?, digits: Int = 1): String =
    v?.takeIf { it.isFinite() }?.let {
        String.format(Locale.US,"%." + digits + "f",it)
    } ?: "—"

@Composable
private fun DiagnosticSummary(
    result: SessionDiagnosticsAnalyzer.Result,report: BenchmarkAnalyzer.Report
) {
    val cpu=result.distribution(Metric.CPU_TEMP)
    val gpu=result.gpuPressure
    val core=result.busiestCorePressure
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        DiagnosticTile("CPU MEDIAN",fmtDiagnostic(cpu.median)+" °C",
            "P95 "+fmtDiagnostic(cpu.p95)+" °C",Modifier.weight(1f))
        DiagnosticTile("GPU >=85%",
            if(gpu.observedSeconds>0.0)fmtDiagnostic(gpu.highSharePct,0)+"%" else "—",
            "Valid "+fmtDiagnostic(gpu.observedSeconds,0)+"s",Modifier.weight(1f))
        DiagnosticTile("CORE >=85%",
            if(core.observedSeconds>0.0)fmtDiagnostic(core.highSharePct,0)+"%" else "—",
            "Valid "+fmtDiagnostic(core.observedSeconds,0)+"s",Modifier.weight(1f))
        DiagnosticTile("BATTERY W",
            fmtDiagnostic(report.energy.weightedMeanW,2),
            "Time-weighted discharge",Modifier.weight(1f))
    }
}

@Composable
private fun DiagnosticTile(label:String,value:String,note:String,modifier:Modifier) {
    Column(modifier.background(Color(0x380B2038),RoundedCornerShape(9.dp))
        .padding(8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,color=coreSecondary,style=MaterialTheme.typography.labelSmall,maxLines=1)
        Text(value,color=coreText,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall,maxLines=1)
        Text(note,color=coreSecondary,style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
}

private fun digits(metric:Metric):Int=if(metric==Metric.DISCHARGE_W)2 else 1

@Composable
private fun DiagnosticDistributionTable(result:SessionDiagnosticsAnalyzer.Result,originMs:Long) {
    val rows=listOf(Metric.CPU_TEMP,Metric.GPU_TEMP,Metric.CPU_LOAD,
        Metric.BUSIEST_CORE,Metric.GPU_LOAD,Metric.DISCHARGE_W,Metric.BATTERY_TEMP)
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("DISTRIBUTION · MEDIAN / P95 / PEAK",color=iceBlue,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            DiagnosticCell("METRIC",1.6f,true)
            DiagnosticCell("P50",.7f,true)
            DiagnosticCell("P95",.7f,true)
            DiagnosticCell("PEAK",.7f,true)
            DiagnosticCell("FINAL 1M",.85f,true)
        }
        rows.forEach {metric ->
            val stat=result.distribution(metric)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                DiagnosticCell(when(metric) {
                    Metric.CPU_TEMP->"CPU °C"
                    Metric.GPU_TEMP->"GPU °C"
                    Metric.CPU_LOAD->"CPU %"
                    Metric.BUSIEST_CORE->"Busiest %"
                    Metric.GPU_LOAD->"GPU %"
                    Metric.DISCHARGE_W->"Power W"
                    else->"Battery °C"
                },1.6f,false)
                DiagnosticCell(fmtDiagnostic(stat.median,digits(metric)),.7f,false)
                DiagnosticCell(fmtDiagnostic(stat.p95,digits(metric)),.7f,false)
                DiagnosticCell(fmtDiagnostic(stat.peak,digits(metric)),.7f,false)
                DiagnosticCell(fmtDiagnostic(stat.final60Median,digits(metric)),.85f,false)
            }
        }
        val peaks=rows.map{result.distribution(it)}.filter{it.peakTimeMs!=null}
        if(peaks.isNotEmpty()) Text("Peak timestamps: "+
            peaks.joinToString(" · ") {
                it.metric.name.lowercase(Locale.US).replace("_"," ")+" @"+
                    ((it.peakTimeMs!!-originMs)/1000L)+"s"
            },color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("P95 marks the upper 5% tail of valid readings, not necessarily " +
            "a sustained spike. Final minute requires 20s+ contiguous valid history.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RowScope.DiagnosticCell(value:String,weight:Float,heading:Boolean) {
    Text(value,Modifier.weight(weight),color=if(heading)coreSecondary else coreText,
        style=MaterialTheme.typography.labelSmall,maxLines=1)
}

@Composable
private fun DiagnosticExplorer(
    result:SessionDiagnosticsAnalyzer.Result,
    points:List<SessionStore.Point>,referenceStartMs:Long?
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var prime by rememberSaveable { mutableStateOf(false) }
    var cursor by remember(points) { mutableFloatStateOf(.5f) }
    val modes=listOf("Thermal","CPU clocks","Core balance","GPU","Power")
    val primary=when(mode) {
        0->Metric.CPU_TEMP
        1->if(prime)Metric.P7_CLOCK else Metric.P3_CLOCK
        2->Metric.BUSIEST_CORE
        3->Metric.GPU_LOAD
        else->Metric.DISCHARGE_W
    }
    val secondary=when(mode) {
        0->Metric.GPU_TEMP
        1->Metric.CPU_TEMP
        2->Metric.CPU_LOAD
        3->Metric.GPU_CLOCK
        else->Metric.BATTERY_PCT
    }
    val start=points.minOfOrNull{it.timeMs} ?: 0L
    val end=points.maxOfOrNull{it.timeMs} ?: start
    val origin=referenceStartMs ?: start
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("DIAGNOSTIC EXPLORER",color=iceBlue,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            modes.forEachIndexed {index,label ->
                OutlinedButton(onClick={mode=index},
                    contentPadding=PaddingValues(horizontal=10.dp,vertical=3.dp)) {
                    Text(label,color=if(mode==index)solarGold else coreText,
                        style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        if(mode==1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={prime=false}) {
                Text("P3 performance",color=if(!prime)solarGold else coreSecondary)
            }
            TextButton(onClick={prime=true}) {
                Text("P7 prime",color=if(prime)solarGold else coreSecondary)
            }
        }
        if(end<=start) Text("At least two timestamped samples required.",
            color=coreSecondary)
        else {
            DiagnosticTrack(result.distribution(primary),start,end,origin,cursor,
                onCursor={cursor=it},result.gapLimitMs)
            DiagnosticTrack(result.distribution(secondary),start,end,origin,cursor,
                onCursor={cursor=it},result.gapLimitMs)
        }
        Text(when(mode) {
            0->"Highest matching CPU sensor is not necessarily socket temperature."
            1->"Actual CPU clocks scale with demand. Compare against observed caps; clock drops do not establish thermal throttling."
            2->"Busiest-core load is not prime-core residency or proof of a single-thread frame bottleneck."
            3->"Sustained GPU pressure plus clock and temperature trends are more informative than isolated peaks."
            else->"Only verified battery-terminal discharge; charging, missing readings and recording gaps excluded."
        },color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DiagnosticTrack(
    stat:Distribution,start:Long,end:Long,origin:Long,cursor:Float,
    onCursor:(Float)->Unit,gapLimit:Long
) {
    val readings=stat.readings
    val minimum=readings.minOfOrNull{it.value} ?: 0.0
    val maximum=readings.maxOfOrNull{it.value} ?: 1.0
    val spread=max(.08,maximum-minimum)
    val bottom=minimum-spread*.15
    val span=spread*1.3
    val duration=(end-start).coerceAtLeast(1L).toDouble()
    val selectedMs=start+(duration*cursor).toLong()
    val nearest=remember(stat,selectedMs) {
        readings.minByOrNull { abs(it.timeMs-selectedMs) }
            ?.takeIf { abs(it.timeMs-selectedMs) <= gapLimit.coerceAtMost(8_000L) }
    }
    val accent=when(stat.metric) {
        Metric.CPU_TEMP,Metric.GPU_TEMP,Metric.BATTERY_TEMP->solarOrange
        Metric.DISCHARGE_W->solarGold
        Metric.P3_CLOCK,Metric.P7_CLOCK,Metric.GPU_CLOCK->coreCyan
        else->Color(0xFF38BDF8)
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(stat.metric.title,color=accent,style=MaterialTheme.typography.labelMedium)
            Text("Valid "+stat.observations+"/"+stat.totalSamples,
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(118.dp)
            .background(Color(0x61091A30),RoundedCornerShape(9.dp))
            .pointerInput(start,end) {
                detectTapGestures{offset ->
                    onCursor((offset.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f))
                }
            }
            .pointerInput(start,end) {
                detectDragGestures{change,_ ->
                    onCursor((change.position.x/size.width.coerceAtLeast(1))
                        .coerceIn(0f,1f))
                }
            }) {
            fun x(t:Long)=((t-start).toDouble()/duration*size.width).toFloat()
            fun y(v:Double)=((1.0-(v-bottom)/span)*size.height).toFloat()
            for(i in 1..3) drawLine(Color.White.copy(alpha=.10f),
                Offset(0f,size.height*i/4f),Offset(size.width,size.height*i/4f),1.dp.toPx())
            val raw=Path()
            val smooth=Path()
            var prior:SessionDiagnosticsAnalyzer.Reading?=null
            var priorSmooth:SessionDiagnosticsAnalyzer.Reading?=null
            for(p in readings) {
                val px=x(p.timeMs)
                if(prior==null || p.segment!=prior!!.segment || p.timeMs-prior!!.timeMs>gapLimit)
                    raw.moveTo(px,y(p.value))
                else raw.lineTo(px,y(p.value))
                prior=p
                val median=p.rolling60
                if(median==null)priorSmooth=null
                else {
                    if(priorSmooth==null || p.segment!=priorSmooth!!.segment ||
                        p.timeMs-priorSmooth!!.timeMs>gapLimit)
                        smooth.moveTo(px,y(median))
                    else smooth.lineTo(px,y(median))
                    priorSmooth=p
                }
            }
            drawPath(raw,accent.copy(alpha=.68f),style=Stroke(1.6.dp.toPx()))
            drawPath(smooth,coreMint,style=Stroke(2.3.dp.toPx()))
            stat.p95?.let{p95 ->
                drawLine(solarGold.copy(alpha=.85f),Offset(0f,y(p95)),
                    Offset(size.width,y(p95)),1.2.dp.toPx(),
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,7f),0f))
                // A constant signal has P95 == median: no false "spike".
                // Bound drawn marker count on long recorded sessions.
                if(p95 > (stat.median ?: p95)+1e-6) {
                    val spikes=readings.filter { it.value>=p95 }
                    val stride=max(1,(spikes.size+119)/120)
                    spikes.forEachIndexed { index,p ->
                        if(index%stride==0) drawCircle(solarGold,radius=2.5.dp.toPx(),
                            center=Offset(x(p.timeMs),y(p.value)))
                    }
                }
            }
            val peakT=stat.peakTimeMs
            val peak=stat.peak
            if(peakT!=null && peak!=null) {
                val px=x(peakT);val py=y(peak);val d=5.dp.toPx()
                val diamond=Path().apply{
                    moveTo(px,py-d);lineTo(px+d,py);lineTo(px,py+d)
                    lineTo(px-d,py);close()
                }
                drawPath(diamond,Color(0xFFFF455A))
            }
            val cx=cursor.coerceIn(0f,1f)*size.width
            drawLine(Color.White.copy(alpha=.8f),Offset(cx,0f),
                Offset(cx,size.height),1.dp.toPx())
        }
        Text("P50 "+fmtDiagnostic(stat.median,digits(stat.metric))+
            " · P95 "+fmtDiagnostic(stat.p95,digits(stat.metric))+
            " · peak "+fmtDiagnostic(stat.peak,digits(stat.metric))+
            " "+stat.metric.unit+
            (stat.peakTimeMs?.let{" @"+((it-origin)/1000L)+"s"} ?: ""),
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("At "+((selectedMs-origin)/1000L)+"s: "+
            fmtDiagnostic(nearest?.value,digits(stat.metric))+
            " "+stat.metric.unit+" · 60s median "+
            fmtDiagnostic(nearest?.rolling60,digits(stat.metric)),
            color=accent,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DiagnosticEvidence(
    result:SessionDiagnosticsAnalyzer.Result,report:BenchmarkAnalyzer.Report
) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("EVIDENCE & LIMITATIONS",color=iceBlue,fontWeight=FontWeight.Bold)
        val g=result.gpuPressure
        val c=result.busiestCorePressure
        Text("GPU >=85%: "+if(g.observedSeconds>0)
            fmtDiagnostic(g.highSharePct)+"% of valid time; longest "+
            fmtDiagnostic(g.longestHighSeconds,0)+" s" else "unavailable",
            color=coreText,style=MaterialTheme.typography.bodySmall)
        Text("Busiest core >=85%: "+if(c.observedSeconds>0)
            fmtDiagnostic(c.highSharePct)+"% of valid time; longest "+
            fmtDiagnostic(c.longestHighSeconds,0)+" s" else "unavailable",
            color=coreText,style=MaterialTheme.typography.bodySmall)
        Text("Observed CPU cap changes: "+result.observedCapChanges+
            " · valid cap snapshots: "+result.observedCapReadings,
            color=coreText,style=MaterialTheme.typography.bodySmall)
        Text("CPU temperature slope · first 3 min "+
            fmtDiagnostic(result.initialCpuSlopeCPerMin,2)+" °C/min · final 60s "+
            fmtDiagnostic(result.finalCpuSlopeCPerMin,2)+" °C/min",
            color=coreText,style=MaterialTheme.typography.bodySmall)
        Text("Mean CPU-sensor / battery-temperature difference: "+
            fmtDiagnostic(result.meanCpuBatteryDeltaC)+" °C (not battery health)",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        // Do not make a short recording's nominal-capacity projection look
        // like a validated battery fuel-gauge forecast.
        var runtimeExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick={runtimeExpanded=!runtimeExpanded}) {
            Text(if(runtimeExpanded)
                "Hide theoretical battery runtime"
                else "Show theoretical battery runtime (low confidence)",
                color=coreSecondary)
        }
        if(runtimeExpanded) {
            Text("Remaining "+fmtDiagnostic(result.remainingHours,1)+
                " h at "+(result.measuredBatteryPct?.toString()?:"—")+"% · full "+
                fmtDiagnostic(result.fullChargeHours,1)+" h",
                color=coreText,style=MaterialTheme.typography.bodySmall)
            Text(result.runtimeNote+" Nominal capacity and present workload "+
                "are assumptions; not a verified remaining-life estimate.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        Text("Telemetry integrity "+report.quality.score+"/100 · sampling coverage "+
            fmtDiagnostic(report.quality.coveragePct)+"% · recording gaps "+
            result.gapCount+" · discharge coverage "+
            fmtDiagnostic(report.energy.coveragePct)+"%",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Observed CPU/GPU behavior does not prove thermal throttling, " +
            "game-frame bottlenecks or tuner identity. Missing data remain missing.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
