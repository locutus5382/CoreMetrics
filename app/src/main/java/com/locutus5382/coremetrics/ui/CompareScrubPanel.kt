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
import androidx.compose.ui.layout.onSizeChanged
import com.locutus5382.coremetrics.analysis.ChartTraceReducer
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.analysis.DischargeSample
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Elapsed alignment, NOT synchronization by a shared game event. */
internal object CompareTimeCursor {
    fun nearest(points:List<SessionStore.Point>, elapsed:Long, toleranceMs:Long=10_000L,
        originalOriginMs:Long?=null)
        :SessionStore.Point? {
        if(points.isEmpty())return null
        val start=originalOriginMs ?: points.first().timeMs
        val target=start+elapsed
        if(target<points.first().timeMs || target>points.last().timeMs)return null
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
    CPU_LOAD("CPU load","%"),BUSIEST_CORE("Busiest core","%"),
    P3_CLOCK("P3 clock"," GHz"),P7_CLOCK("P7 clock"," GHz"),
    POWER("Discharge W"," W"),FPS("Layer FPS"," FPS")
}

private fun reading(p:SessionStore.Point, metric:Metric):Double? = when(metric) {
    Metric.CPU_TEMP -> p.cpu
    Metric.GPU_TEMP -> p.gpu
    Metric.CPU_LOAD -> p.cpuLoad
    Metric.GPU_LOAD -> p.gpuLoad
    Metric.BUSIEST_CORE -> p.busiestCorePct
    Metric.P3_CLOCK -> SessionDiagnosticsAnalyzer.clockGHz(p.cpuClocks,3)
    Metric.P7_CLOCK -> SessionDiagnosticsAnalyzer.clockGHz(p.cpuClocks,7)
    Metric.POWER -> DischargeSample.watts(p.batteryWatts,p.batteryPowerStatus)
    Metric.FPS -> p.sfFps
}?.takeIf { it.isFinite() }

private fun f(value:Double?):String =
    value?.let { String.format(Locale.US,"%.1f",it) } ?: "—"
private fun fMetric(value:Double?,metric:Metric):String =
    value?.let {
        String.format(Locale.US,
            if(metric==Metric.P3_CLOCK||metric==Metric.P7_CLOCK)"%.2f" else "%.1f",it)
    } ?: "—"

@Composable
internal fun CompareScrubPanel(
    a:List<SessionStore.Point>,b:List<SessionStore.Point>,
    fraction:Float,onFractionChange:(Float)->Unit,
    originalStartA:Long?=null,originalStartB:Long?=null,
    axisDurationMs:Long?=null,
    selectedStartElapsedMs:Long=0L,
    selectedEndElapsedMs:Long?=null
) {
    if(a.isEmpty() || b.isEmpty())return
    var metric by remember { mutableStateOf(Metric.CPU_TEMP) }
    val selectableMetrics=remember(a,b) {
        fun hasValid(metric:Metric)=a.any{reading(it,metric)!=null} ||
            b.any{reading(it,metric)!=null}
        Metric.values().filter {
            when(it) {
                Metric.FPS,Metric.P3_CLOCK,Metric.P7_CLOCK,Metric.BUSIEST_CORE ->
                    hasValid(it)
                else -> true
            }
        }
    }
    LaunchedEffect(selectableMetrics) {
        if(metric !in selectableMetrics)metric=Metric.CPU_TEMP
    }
    val originA=originalStartA ?: a.first().timeMs
    val originB=originalStartB ?: b.first().timeMs
    val duration=(axisDurationMs ?: max(a.last().timeMs-originA,
        b.last().timeMs-originB)).coerceAtLeast(1L)
    val endBound=(selectedEndElapsedMs ?: duration).coerceAtMost(duration)
    val startBound=selectedStartElapsedMs.coerceIn(0L,endBound)
    val elapsed=(duration*fraction).toLong()
    val cursorA=remember(a,originA,startBound,endBound) {
        a.filter { it.timeMs-originA in startBound..endBound }
    }
    val cursorB=remember(b,originB,startBound,endBound) {
        b.filter { it.timeMs-originB in startBound..endBound }
    }
    val left=CompareTimeCursor.nearest(cursorA,elapsed,originalOriginMs=originA)
    val right=CompareTimeCursor.nearest(cursorB,elapsed,originalOriginMs=originB)
    val vals=remember(a,b,metric) {
        buildList {
            a.mapNotNullTo(this){reading(it,metric)}
            b.mapNotNullTo(this){reading(it,metric)}
        }
    }
    val available=vals.isNotEmpty()
    val bounds=remember(vals) {
        val lower=vals.minOrNull()?:0.0
        val upper=vals.maxOrNull()?:1.0
        val pad=max(1.0,(upper-lower)*0.1)
        (lower-pad) to (upper+pad)
    }
    val floor=bounds.first;val ceil=bounds.second
    var chartWidth by remember{mutableIntStateOf(900)}
    var chartHeight by remember{mutableIntStateOf(300)}
    // Only visual traces are reduced; cursor and A/B statistics keep every
    // original point, independent of chart-only GPU/FPS filters.
    val traceA=remember(a,metric,chartWidth,originA,duration) {
        ChartTraceReducer.reduce(a.map{p->
            ChartTraceReducer.Sample(p.timeMs,reading(p,metric))},
            originA,originA+duration,chartWidth,15_000L)
    }
    val traceB=remember(b,metric,chartWidth,originB,duration) {
        ChartTraceReducer.reduce(b.map{p->
            ChartTraceReducer.Sample(p.timeMs,reading(p,metric))},
            originB,originB+duration,chartWidth,15_000L)
    }

    // Geometry is stable while the shared elapsed-time cursor moves.
    // In particular, dragging no longer allocates a new Path per frame.
    val tracePaths=remember(traceA,traceB,originA,originB,duration,
        chartWidth,chartHeight,bounds) {
        ChartPathCache.build(traceA,originA,duration.toDouble(),
            chartWidth.toFloat(),chartHeight.toFloat(),floor,ceil-floor,15_000L) to
        ChartPathCache.build(traceB,originB,duration.toDouble(),
            chartWidth.toFloat(),chartHeight.toFloat(),floor,ceil-floor,15_000L)
    }

    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("SYNCHRONIZED ELAPSED-TIME COMPARISON",
            color=iceBlue,fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            // Skip the unavailable FPS selector without rescanning every
            // recorded point on each chart drag/recomposition.
            selectableMetrics.forEach { candidate ->
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
            .onSizeChanged{chartWidth=it.width.coerceAtLeast(1)
                chartHeight=it.height.coerceAtLeast(1)}
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    onFractionChange((point.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart={point ->
                    onFractionChange((point.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f))
                },onDrag={change,_ ->
                    onFractionChange((change.position.x/size.width.coerceAtLeast(1))
                        .coerceIn(0f,1f))
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
                drawPath(tracePaths.first,coreCyan,
                    style=Stroke(width=2.5.dp.toPx()))
                drawPath(tracePaths.second,solarGold,
                    style=Stroke(width=2.5.dp.toPx()))
            }
            // Selected analysis bounds are painted independently of the cursor.
            // An unmatched tail may be shown visually, never added to A/B statistics.
            val xStart=(startBound.toDouble()/duration*w).toFloat().coerceIn(0f,w)
            val xEnd=(endBound.toDouble()/duration*w).toFloat().coerceIn(xStart,w)
            drawRect(Color(0xA0030917),Offset(0f,0f),
                androidx.compose.ui.geometry.Size(xStart,h))
            drawRect(Color(0xA0030917),Offset(xEnd,0f),
                androidx.compose.ui.geometry.Size(w-xEnd,h))
            drawLine(solarGold,Offset(xStart,0f),Offset(xStart,h),1.6.dp.toPx())
            drawLine(solarGold,Offset(xEnd,0f),Offset(xEnd,h),1.6.dp.toPx())
            val x=fraction*w
            drawLine(solarGold.copy(alpha=.95f),Offset(x,0f),Offset(x,h),
                strokeWidth=2.dp.toPx())
            drawCircle(solarGold,radius=4.dp.toPx(),center=Offset(x,6.dp.toPx()))
        }
        if(!available)Text("This metric was not measured in either session.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Slider(value=fraction,onValueChange=onFractionChange,valueRange=0f..1f)
        Text("Cursor: "+(elapsed/1000)+" s from original REC · tap/drag chart or use slider with controller",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CursorCard("A",left,metric,Modifier.weight(1f))
            CursorCard("B",right,metric,Modifier.weight(1f))
        }
        Text("Δ B − A: "+(if(left!=null && right!=null &&
            reading(left,metric)!=null && reading(right,metric)!=null)
            fMetric(reading(right,metric)!!-reading(left,metric)!!,metric)+metric.unit
            else "unavailable"),
            color=solarGold,fontWeight=FontWeight.Bold)
        Text("Busiest core is the busiest measured interval core (core ID can change); "+
            "P3/P7 are observed clocks, not governor caps or proof of throttling.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Battery-W excludes charging, unknown status and invalid watts. " +
            "Closest measured points within ±10 seconds. A gap, ended run or " +
            "missing sensor is not interpolated. Time origins use each original run's first sample.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CursorCard(label:String,point:SessionStore.Point?,
    metric:Metric,modifier:Modifier) {
    Column(modifier.pureGlass(12).padding(10.dp)) {
        Text(label,color=if(label=="A") coreCyan else solarGold,
            fontWeight=FontWeight.Bold)
        Text(metric.title+": "+fMetric(point?.let{reading(it,metric)},metric)+
            (if(point==null) "" else metric.unit),
            color=coreText,style=MaterialTheme.typography.bodyMedium)
        Text("CPU "+f(point?.cpu)+"°  GPU "+f(point?.gpu)+"°",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Battery discharge "+f(point?.let{
            DischargeSample.watts(it.batteryWatts,it.batteryPowerStatus)
        })+" W",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
