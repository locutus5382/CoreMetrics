package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import com.locutus5382.coremetrics.analysis.ChartTraceReducer
import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.analysis.DischargeSample
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Offline-only, gap-aware views of actual samples; no synthetic interpolation. */
internal enum class SessionSignal(val caption:String,val unit:String,val accent:Color) {
    CPU_TEMP("CPU °C","°C",Color(0xFFFFC700)),
    GPU_TEMP("GPU °C","°C",Color(0xFFFF6B00)),
    CPU_LOAD("CPU load","%",Color(0xFF38BDF8)),
    GPU_LOAD("GPU load","%",Color(0xFF2563EB)),
    BATTERY_W("Battery net","W",Color(0xFFEFBB70)),
    RAM_GIB("RAM","GiB",Color(0xFFB6E6FC));
    fun value(p:SessionStore.Point):Double? {
        val v=when(this) {
            CPU_TEMP->p.cpu
            GPU_TEMP->p.gpu
            CPU_LOAD->p.cpuLoad
            GPU_LOAD->p.gpuLoad
            RAM_GIB->p.ram
            BATTERY_W->DischargeSample.watts(p.batteryWatts,p.batteryPowerStatus)
        }
        return v?.takeIf{it.isFinite()}
    }
}

private fun num(v:Double?,digits:Int=1):String =
    v?.takeIf{it.isFinite()}?.let{
        String.format(Locale.US,"%."+digits+"f",it)
    } ?: "—"

@Composable
internal fun SessionQuickOverview(report:BenchmarkAnalyzer.Report) {
    Column(Modifier.fillMaxWidth().pureGlass(14).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("SESSION ${report.session.id} · ${report.session.configuration} · "+
            report.session.status,color=iceBlue,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SummaryMetric("CPU AVG",num(report.meanCpuC)+"°C",Modifier.weight(1f))
            SummaryMetric("GPU AVG",num(report.meanGpuC)+"°C",Modifier.weight(1f))
            SummaryMetric("BATTERY",
                num(report.energy.weightedMeanW)+" W",Modifier.weight(1f))
            SummaryMetric("DURATION",
                num(report.elapsedSeconds,0)+" s",Modifier.weight(1f))
        }
        Text("Telemetry integrity: ${report.quality.score}/100 · "+
            "${num(report.quality.coveragePct)}% sample coverage · "+
            "${report.quality.gapCount} detected gaps",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Text("Thermal trend: "+
            (report.thermal.slopeCPerMin?.let{
                String.format(Locale.US,"%+.1f°C/min",it)
            }?:"unavailable")+" · "+report.thermal.state,
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Text("Battery energy: "+num(report.energy.wh,4)+" Wh · "+
            num(report.energy.milliWhPerMinute,2)+" mWh/min · "+
            report.energy.powerCondition.label,
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryMetric(label:String,value:String,modifier:Modifier=Modifier) {
    val finish=LocalSurfaceFinish.current
    val accent=when(label) {
        "CPU AVG"->solarOrange
        "GPU AVG"->coreCyan
        "BATTERY"->solarGold
        else->coreMint
    }
    Column(modifier.cmMetricSurface(accent=accent)
        .padding(horizontal=10.dp,vertical=8.dp)) {
        Text(label,color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text(value,color=if(finish==SurfaceFinish.SOLAR_PULSE)
                accent else coreText,
            fontWeight=FontWeight.Bold,
            fontFamily=if(finish==SurfaceFinish.COBALT_MATRIX)
                androidx.compose.ui.text.font.FontFamily.Monospace
                else androidx.compose.ui.text.font.FontFamily.Default,
            style=MaterialTheme.typography.titleMedium,maxLines=1)
    }
}

@Composable
internal fun SessionMetricTimeline(points:List<SessionStore.Point>,
    label:String="SESSION TIMELINE",referenceStartMs:Long?=null) {
    var first by remember {mutableStateOf(SessionSignal.CPU_TEMP)}
    var second by remember {mutableStateOf(SessionSignal.CPU_LOAD)}
    var cursor by remember(points) {mutableFloatStateOf(.5f)}
    val sorted=remember(points){points.sortedBy{it.timeMs}}
    Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(label,color=iceBlue,fontWeight=FontWeight.Bold)
        Text("Two synchronized tracks · tap or drag for actual recorded values",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf(first,second).forEachIndexed {index,signal->
                var opened by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Column {
                        OutlinedButton(onClick={opened=!opened},modifier=Modifier.fillMaxWidth()) {
                            Text(if(index==0)"1: ${signal.caption} ▾"
                                else "2: ${signal.caption} ▾",maxLines=1)
                        }
                        if(opened) {
                            Column(Modifier.pureGlass(8).padding(5.dp)) {
                                SessionSignal.values().forEach {candidate->
                                    TextButton(onClick={
                                        if(index==0)first=candidate else second=candidate
                                        opened=false
                                    },modifier=Modifier.fillMaxWidth()) {
                                        Text(candidate.caption,maxLines=1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(sorted.size<2) {
            Text("At least two samples are needed to draw a timeline.",
                color=coreSecondary)
        }else {
            val start=sorted.first().timeMs
            val absoluteOrigin=referenceStartMs?:start
            val end=sorted.last().timeMs
            val time=start+((end-start)*cursor).toLong()
            // A mean interval spanning a recording interruption is not a
            // valid tolerance: never show an old value in the middle of a gap.
            val nearest=CompareTimeCursor.nearest(sorted,time-start,10_000L)
            listOf(first,second).forEach {signal->
                SignalTrack(sorted,signal,cursor,{cursor=it})
                Text(signal.caption+": "+num(nearest?.let{signal.value(it)})+
                    signal.unit+" at "+(time-absoluteOrigin)/1000L+" s",
                    color=signal.accent,style=MaterialTheme.typography.bodySmall)
            }
            Text("No values are reconstructed across unavailable sensors, "+
                "charging intervals or recording gaps.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SignalTrack(points:List<SessionStore.Point>,signal:SessionSignal,
    cursor:Float,onCursor:(Float)->Unit) {
    val time0=points.first().timeMs
    val duration=(points.last().timeMs-time0).coerceAtLeast(1L).toDouble()
    val valid=remember(points,signal) {
        points.mapNotNull{signal.value(it)}
    }
    val minV=(valid.minOrNull()?:0.0)
    val maxV=(valid.maxOrNull()?:1.0)
    val range=max(maxV-minV,0.1)
    val margin=range*.12
    val bottom=minV-margin
    val scale=range+2*margin
    // Use a bounded time gap, never average duration: a 2-point recording
    // with a one-minute interruption must NOT draw a continuous line.
    val gaps=remember(points) {
        points.zipWithNext{a,b->(b.timeMs-a.timeMs).toDouble()}
            .filter{it>0.0}.sorted()
    }
    val cadence=gaps.getOrNull(gaps.size/2)?:5_000.0
    val gapLimit=if(cadence>10_000.0)15_000.0
        else max(10_000.0,cadence*2.5)
    var chartWidth by remember{mutableIntStateOf(700)}
    val reduced=remember(points,signal,chartWidth) {
        ChartTraceReducer.reduce(
            points.map{p->ChartTraceReducer.Sample(p.timeMs,signal.value(p))},
            time0,points.last().timeMs,chartWidth,gapLimit.toLong())
    }
    Column {
        Row(horizontalArrangement=Arrangement.SpaceBetween,
            modifier=Modifier.fillMaxWidth()) {
            Text(signal.caption,color=signal.accent,
                style=MaterialTheme.typography.labelMedium)
            Text(num(minV)+"–"+num(maxV)+" "+signal.unit,
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(118.dp)
            .onSizeChanged{chartWidth=it.width.coerceAtLeast(1)}
            .background(Color(0x5A07152C),RoundedCornerShape(10.dp))
            .pointerInput(time0,duration) {
                detectTapGestures{offset ->
                    onCursor((offset.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f))
                }
            }
            .pointerInput(time0,duration) {
                detectDragGestures{change,_ ->
                    onCursor((change.position.x/size.width.coerceAtLeast(1))
                        .coerceIn(0f,1f))
                }
            }) {
            for(i in 1..3)drawLine(Color.White.copy(alpha=.12f),
                Offset(0f,size.height*i/4f),
                Offset(size.width,size.height*i/4f),1.dp.toPx())
            val path=Path()
            var last:ChartTraceReducer.Sample?=null
            for(p in reduced){
                val v=p.value
                if(v==null){last=null;continue}
                val x=((p.timeMs-time0)/duration*size.width).toFloat()
                val y=((1-(v-bottom)/scale)*size.height).toFloat()
                if(last==null || (p.timeMs-last.timeMs)>gapLimit)
                    path.moveTo(x,y)
                else path.lineTo(x,y)
                last=p
            }
            drawPath(path,signal.accent,style=Stroke(2.dp.toPx()))
            val px=cursor.coerceIn(0f,1f)*size.width
            drawLine(solarGold,Offset(px,0f),Offset(px,size.height),1.4.dp.toPx())
        }
    }
}

/** Contrast two measured runs without implying synchronized game scenes. */
@Composable
internal fun CompareQuickOverview(a:BenchmarkAnalyzer.Report,
    b:BenchmarkAnalyzer.Report) {
    val warnings=remember(a,b){
        buildList {
            if(a.session.status!="COMPLETED" || b.session.status!="COMPLETED")
                add("At least one recording was interrupted or not completed")
            if(a.elapsedSeconds<=0.0 || b.elapsedSeconds<=0.0 ||
                max(a.elapsedSeconds,b.elapsedSeconds)/
                    min(a.elapsedSeconds,b.elapsedSeconds).coerceAtLeast(.001)>1.2)
                add("Recording lengths differ by more than 20%")
            if(a.meanGpuLoad!=null && b.meanGpuLoad!=null &&
                abs(a.meanGpuLoad-b.meanGpuLoad)>10.0)
                add("Mean GPU load differs by more than 10 percentage points")
            if(a.session.configuration!=b.session.configuration)
                add("Selected configurations differ (this may be intentional)")
            if(a.session.graphicsDriver!=b.session.graphicsDriver ||
                a.session.resolutionScale!=b.session.resolutionScale)
                add("Graphics driver or resolution metadata differ")
            if(a.energy.powerCondition!=BenchmarkAnalyzer.PowerCondition.DISCHARGING ||
                b.energy.powerCondition!=BenchmarkAnalyzer.PowerCondition.DISCHARGING)
                add("Not both runs exclusively on battery discharge")
            if(a.quality.score<80 || b.quality.score<80)
                add("One recording has limited telemetry integrity")
        }
    }
    Column(Modifier.fillMaxWidth().pureGlass(14).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("A/B PERFORMANCE · SUMMARY",color=iceBlue,
            fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("A" to a,"B" to b).forEach { (tag,report)->
                Column(Modifier.weight(1f).pureGlass(10).padding(10.dp)) {
                    Text("$tag · SESSION ${report.session.id} · ${report.session.status}",
                        color=if(tag=="A")coreCyan else solarGold,
                        fontWeight=FontWeight.Bold)
                    Text(report.session.configuration,color=coreText)
                    Text("Power "+num(report.energy.weightedMeanW,2)+" W · "+
                        num(report.energy.milliWhPerMinute,2)+" mWh/min",
                        color=coreText,style=MaterialTheme.typography.bodySmall)
                    Text("CPU "+num(report.meanCpuC)+"°C · GPU "+
                        num(report.meanGpuC)+"°C",
                        color=coreText,style=MaterialTheme.typography.bodySmall)
                    Text(num(report.elapsedSeconds,0)+" s · "+
                        report.sampleCount+" measured samples · "+
                        report.quality.score+"/100 integrity",
                        color=coreSecondary,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        fun delta(a:Double?,b:Double?,unit:String):String =
            if(a!=null && b!=null)
                String.format(Locale.US,"%+.2f %s",b-a,unit) else "—"
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SummaryMetric("Δ CPU AVG",delta(a.meanCpuC,b.meanCpuC,"°C"),
                Modifier.weight(1f))
            SummaryMetric("Δ GPU AVG",delta(a.meanGpuC,b.meanGpuC,"°C"),
                Modifier.weight(1f))
            SummaryMetric("Δ POWER",delta(a.energy.weightedMeanW,
                b.energy.weightedMeanW,"W"),Modifier.weight(1f))
            SummaryMetric("Δ ENERGY RATE",delta(a.energy.milliWhPerMinute,
                b.energy.milliWhPerMinute,"mWh/min"),Modifier.weight(1f))
        }
        Text("Comparability: "+
            (if(warnings.isEmpty())"PRELIMINARY — conditions look similar"
                else "LIMITED — ${warnings.size} material caveat(s)"),
            color=if(warnings.isEmpty())coreCyan else solarOrange,
            fontWeight=FontWeight.Bold)
        warnings.take(3).forEach {
            Text("• "+it,color=coreSecondary,
                style=MaterialTheme.typography.bodySmall)
        }
        val observedCpu=when {
            a.meanCpuC==null || b.meanCpuC==null->"CPU temperatures not comparable"
            a.meanCpuC < b.meanCpuC->"A had the lower recorded mean CPU temperature"
            b.meanCpuC < a.meanCpuC->"B had the lower recorded mean CPU temperature"
            else->"Mean CPU temperatures were equal"
        }
        val observedPower=when{
            a.energy.weightedMeanW==null || b.energy.weightedMeanW==null->
                "discharge power cannot be compared"
            a.energy.weightedMeanW < b.energy.weightedMeanW->
                "A had lower observed battery discharge"
            b.energy.weightedMeanW < a.energy.weightedMeanW->
                "B had lower observed battery discharge"
            else->"observed battery discharge was equal"
        }
        Text("OBSERVED: "+observedCpu+"; "+observedPower+".",
            color=iceBlue,style=MaterialTheme.typography.bodySmall)
        Text("Even matching metadata cannot prove the same in-game scene. "+
            "Integrity scores describe samples, not benchmark equivalence.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
