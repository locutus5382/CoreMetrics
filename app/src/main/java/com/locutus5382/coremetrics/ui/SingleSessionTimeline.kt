package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlin.math.abs

/** Persisted sample timeline. Two separate, synchronized y-scales prevent
 * misleading overlays of °C, percent, watts and GiB.
 * No GPU/CPU hardware polling, interpolation or fabricated FPS.
 */
private enum class TraceMetric(val label:String,val unit:String) {
    CPU_TEMP("CPU temperature","°C"),
    GPU_TEMP("GPU temperature","°C"),
    CPU_LOAD("CPU load","%"),
    GPU_LOAD("GPU load","%"),
    BATTERY_W("Battery discharge","W"),
    RAM("RAM used","GiB");
    fun value(p:SessionStore.Point):Double? = when(this) {
        CPU_TEMP->p.cpu
        GPU_TEMP->p.gpu
        CPU_LOAD->p.cpuLoad
        GPU_LOAD->p.gpuLoad
        BATTERY_W->p.batteryWatts?.takeIf {
            it>=0.0 && p.batteryPowerStatus?.startsWith("DISCHARGING")==true
        }
        RAM->p.ram
    }?.takeIf{it.isFinite()}
}

@Composable
internal fun SingleSessionTimeline(points:List<SessionStore.Point>) {
    val ordered=remember(points){points.sortedBy{it.timeMs}}
    if(ordered.size<2){
        Text("At least two valid timestamped samples are needed for a timeline.",
            color=coreSecondary)
        return
    }
    var first by remember {mutableStateOf(TraceMetric.CPU_TEMP)}
    var second by remember {mutableStateOf(TraceMetric.CPU_LOAD)}
    var cursor by remember(ordered) {mutableFloatStateOf(.5f)}
    val start=ordered.first().timeMs
    val duration=(ordered.last().timeMs-start).coerceAtLeast(1L)
    val cursorTime=start+(duration*cursor).toLong()
    val typical=remember(ordered){
        ordered.zipWithNext().map{it.second.timeMs-it.first.timeMs}
            .filter{it>0}.sorted().let {it.getOrNull(it.size/2)?:5000L}
    }
    val tolerance=(typical*2).coerceIn(2_000L,10_000L)

    Column(Modifier.fillMaxWidth().pureGlass(17).padding(13.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("SESSION TIMELINE",color=iceBlue,
            style=MaterialTheme.typography.titleMedium)
        Text("Two synchronized tracks · actual sample timestamps · no filled gaps",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        @Composable fun selector(selected:TraceMetric,onChange:(TraceMetric)->Unit) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TraceMetric.values().forEach { metric ->
                    FilterChip(selected=selected==metric,
                        onClick={onChange(metric)},label={Text(metric.label)})
                }
            }
        }
        selector(first,{first=it})
        TraceChart(ordered,first,cursor,coreCyan,typical){cursor=it}
        selector(second,{second=it})
        TraceChart(ordered,second,cursor,solarGold,typical){cursor=it}
        Slider(value=cursor,onValueChange={cursor=it},valueRange=0f..1f)
        val sample=ordered.minByOrNull{abs(it.timeMs-cursorTime)}
            ?.takeIf{abs(it.timeMs-cursorTime)<=tolerance}
        fun fmt(v:Double?,unit:String)=v?.let {
            String.format(Locale.US,"%.2f %s",it,unit)
        }?:"unavailable"
        Text(String.format(Locale.US,"Cursor: %.1f s", (cursorTime-start)/1000.0)+
            " · "+first.label+": "+fmt(sample?.let{first.value(it)},first.unit)+
            " · "+second.label+": "+fmt(sample?.let{second.value(it)},second.unit),
            color=coreText,style=MaterialTheme.typography.bodySmall)
        Text("Gaps, unavailable sensors and charging intervals are never filled. "+
            "A cursor can have no nearby valid measurement.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TraceChart(
    points:List<SessionStore.Point>,metric:TraceMetric,cursor:Float,
    color:Color,typicalMs:Long,onCursor:(Float)->Unit
) {
    val values=remember(points,metric){points.map{metric.value(it)}}
    val valid=values.filterNotNull()
    val minValue=valid.minOrNull()
    val maxValue=valid.maxOrNull()
    val lo=if(metric==TraceMetric.CPU_LOAD||metric==TraceMetric.GPU_LOAD)0.0
        else if(minValue!=null)(minValue-2.0).coerceAtLeast(0.0) else 0.0
    val hi=if(metric==TraceMetric.CPU_LOAD||metric==TraceMetric.GPU_LOAD)100.0
        else if(maxValue!=null)maxValue+2.0 else 100.0
    val range=(hi-lo).coerceAtLeast(1.0)
    val start=points.first().timeMs
    val end=points.last().timeMs
    val elapsed=(end-start).coerceAtLeast(1L)
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(metric.label+"  ·  "+metric.unit+"  ·  "+
            (if(valid.isEmpty())"no valid data" else
                String.format(Locale.US,"%.1f–%.1f",lo,hi)),
            color=color,style=MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(118.dp)
            .background(Color(0x7C050D1E),RoundedCornerShape(9.dp))
            .border(1.dp,coreBorder.copy(alpha=.35f),RoundedCornerShape(9.dp))
            .pointerInput(points,metric){
                detectTapGestures { offset ->
                    onCursor((offset.x/size.width.coerceAtLeast(1))
                        .coerceIn(0f,1f))
                }
            }) {
            val w=size.width;val h=size.height
            repeat(4){idx->
                val y=idx*h/3f
                drawLine(coreBorder.copy(alpha=.16f),Offset(0f,y),
                    Offset(w,y),1f)
            }
            var active=false
            var previous=0L
            val path=Path()
            points.forEachIndexed {index,p ->
                val v=values[index]
                val x=((p.timeMs-start).toDouble()/elapsed*w).toFloat()
                if(v==null){active=false;return@forEachIndexed}
                val y=((hi-v)/range*h).toFloat().coerceIn(0f,h)
                if(!active || p.timeMs-previous>
                    (typicalMs*2.5).toLong().coerceAtLeast(7_000L)){
                    if(active)drawPath(path,color,style=Stroke(width=2.4f))
                    path.reset();path.moveTo(x,y)
                    active=true
                }else path.lineTo(x,y)
                previous=p.timeMs
            }
            if(active)drawPath(path,color,style=Stroke(width=2.4f))
            drawLine(solarGold,Offset(w*cursor,0f),
                Offset(w*cursor,h),1.8f)
        }
    }
}
