package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Elapsed alignment, NOT synchronization by a shared game event. */
internal object CompareTimeCursor {
    fun nearest(points:List<SessionStore.Point>, elapsed:Long, toleranceMs:Long=10_000L)
        :SessionStore.Point? {
        if(points.isEmpty())return null
        val start=points.first().timeMs
        val target=start+elapsed
        if(target<start || target>points.last().timeMs)return null
        var lo=0
        var hi=points.lastIndex
        while(lo<hi) {
            val mid=(lo+hi)/2
            if(points[mid].timeMs<target)lo=mid+1 else hi=mid
        }
        val later=points[lo]
        val prior=points.getOrNull(lo-1)
        val best=if(prior!=null && target-prior.timeMs<later.timeMs-target)prior
            else later
        return best.takeIf { abs(target-it.timeMs)<=toleranceMs }
    }
}

private enum class Metric(val title:String, val unit:String) {
    CPU_TEMP("CPU °C","°C"),GPU_TEMP("GPU °C","°C"),GPU_LOAD("GPU load","%"),
    CPU_LOAD("CPU load","%"),POWER("Net battery W"," W"),
    FPS("Layer FPS"," FPS")
}

private fun reading(p:SessionStore.Point, metric:Metric):Double? = when(metric) {
    Metric.CPU_TEMP -> p.cpu
    Metric.GPU_TEMP -> p.gpu
    Metric.CPU_LOAD -> p.cpuLoad
    Metric.GPU_LOAD -> p.gpuLoad
    Metric.POWER -> p.batteryWatts
    Metric.FPS -> p.sfFps
}?.takeIf { it.isFinite() }

private fun f(value:Double?):String =
    value?.let { String.format(Locale.US,"%.1f",it) } ?: "—"

@Composable
internal fun CompareScrubPanel(a:List<SessionStore.Point>,b:List<SessionStore.Point>) {
    if(a.isEmpty() || b.isEmpty())return
    var metric by remember { mutableStateOf(Metric.CPU_TEMP) }
    var fraction by remember { mutableFloatStateOf(0.5f) }
    val duration=max(a.last().timeMs-a.first().timeMs,
        b.last().timeMs-b.first().timeMs).coerceAtLeast(1L)
    val elapsed=(duration*fraction).toLong()
    val left=CompareTimeCursor.nearest(a,elapsed)
    val right=CompareTimeCursor.nearest(b,elapsed)
    val vals=remember(a,b,metric) {
        (a+b).mapNotNull { reading(it,metric) }
    }
    val available=vals.isNotEmpty()
    val lower=(vals.minOrNull()?:0.0)
    val upper=(vals.maxOrNull()?:1.0)
    val pad=max(1.0,(upper-lower)*0.1)
    val floor=lower-pad
    val ceil=upper+pad

    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("SYNCHRONIZED ELAPSED-TIME COMPARISON",
            color=iceBlue,fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Metric.values().forEach { candidate ->
                FilterChip(selected=metric==candidate,onClick={metric=candidate},
                    label={Text(candidate.title)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("A · Cobalt",color=coreCyan)
            Text("B · Orange",color=solarGold)
            Text("Vertical gold = shared time cursor",color=coreSecondary)
        }
        Canvas(Modifier.fillMaxWidth().height(188.dp)
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    fraction=(point.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart={point ->
                    fraction=(point.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f)
                },onDrag={change,_ ->
                    fraction=(change.position.x/size.width.coerceAtLeast(1))
                        .coerceIn(0f,1f)
                    change.consume()
                })
            }
            .background(Color(0xC00C1933),RoundedCornerShape(12.dp))
            .border(1.dp,Color.White.copy(alpha=.18f),RoundedCornerShape(12.dp))) {
            val w=size.width
            val h=size.height
            for(i in 1..3) {
                val y=h*i/4f
                drawLine(Color(0x255AB4D4),Offset(0f,y),Offset(w,y),1f)
            }
            if(available) {
                fun drawRun(points:List<SessionStore.Point>,color:Color) {
                    if(points.size<2)return
                    val origin=points.first().timeMs
                    val path=Path()
                    var previous:Long?=null
                    var n=0
                    points.forEach { sample ->
                        val v=reading(sample,metric)
                        if(v==null) {
                            previous=null
                            return@forEach
                        }
                        val x=((sample.timeMs-origin).toFloat()/duration*w)
                            .coerceIn(0f,w)
                        val y=((ceil-v)/(ceil-floor)*h).toFloat().coerceIn(0f,h)
                        if(previous==null || sample.timeMs-previous!!>15_000L)
                            path.moveTo(x,y)
                        else path.lineTo(x,y)
                        previous=sample.timeMs
                        n++
                    }
                    if(n>1)drawPath(path,color,style=Stroke(width=2.5.dp.toPx()))
                }
                drawRun(a,coreCyan)
                drawRun(b,solarGold)
            }
            val x=fraction*w
            drawLine(solarGold.copy(alpha=.95f),Offset(x,0f),Offset(x,h),
                strokeWidth=2.dp.toPx())
            drawCircle(solarGold,radius=4.dp.toPx(),center=Offset(x,6.dp.toPx()))
        }
        if(!available)Text("This metric was not measured in either session.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Slider(value=fraction,onValueChange={fraction=it},valueRange=0f..1f)
        Text("Cursor: "+(elapsed/1000)+" s · tap/drag chart or use slider with controller",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CursorCard("A",left,metric,Modifier.weight(1f))
            CursorCard("B",right,metric,Modifier.weight(1f))
        }
        Text("Δ B − A: "+(if(left!=null && right!=null &&
            reading(left,metric)!=null && reading(right,metric)!=null)
            f(reading(right,metric)!!-reading(left,metric)!!)+metric.unit
            else "unavailable"),
            color=solarGold,fontWeight=FontWeight.Bold)
        Text("Closest measured points within ±10 seconds. A gap, ended run or " +
            "missing sensor is not interpolated. A/B times start at each run's first sample.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CursorCard(label:String,point:SessionStore.Point?,
    metric:Metric,modifier:Modifier) {
    Column(modifier.pureGlass(12).padding(10.dp)) {
        Text(label,color=if(label=="A") coreCyan else solarGold,
            fontWeight=FontWeight.Bold)
        Text(metric.title+": "+f(point?.let{reading(it,metric)})+
            (if(point==null) "" else metric.unit),
            color=coreText,style=MaterialTheme.typography.bodyMedium)
        Text("CPU "+f(point?.cpu)+"°  GPU "+f(point?.gpu)+"°",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Battery "+f(point?.batteryWatts)+" W",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
