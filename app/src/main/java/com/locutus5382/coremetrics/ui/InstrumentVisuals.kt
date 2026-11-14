package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val coreNight = Color(0xFF050B1E)
internal val coreCard = Color(0x32143C81)
internal val coreBorder = Color(0xAA9EC6ED)
internal val coreCyan = Color(0xFF93C5FD)
internal val coreMint = Color(0xFF60DCCF)
internal val coreAmber = Color(0xFFFFC700)
internal val coreText = Color(0xFFECF6FC)
internal val coreSecondary = Color(0xFFC3D7ED)

internal data class LiveTracePoint(
    val timeMs: Long,
    val cpuTemp: Double?,
    val gpuTemp: Double?,
    val cpuLoad: Double?,
    val gpuLoad: Double?
)

@Composable
internal fun InstrumentKpi(
    title: String, value: String?, detail: String,
    color: Color = coreCyan, modifier: Modifier = Modifier
) {
    val finish=LocalSurfaceFinish.current
    val panel=if(finish==SurfaceFinish.FROSTED)
        modifier.pureGlass(15) else modifier.cmMetricSurface(accent=color)
    Column(panel.padding(horizontal=12.dp,vertical=11.dp)) {
        Text(title.uppercase(),color=coreSecondary,
            style=MaterialTheme.typography.labelSmall,letterSpacing=0.7.sp)
        Spacer(Modifier.height(5.dp))
        Text(value ?: "—",color=if(value==null) coreSecondary else color,
            style=MaterialTheme.typography.headlineSmall,
            fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(detail,color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

/** UI-only 60-second traces, fed by the existing recording status bridge. */
@Composable
internal fun LiveScope(points: List<LiveTracePoint>) {
    val latest = points.lastOrNull()?.timeMs ?: 0L
    val recent = points.filter { it.timeMs >= latest-60_000L }
    Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("LIVE HISTORY · 60 SEC", color=coreText,
                style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
            Text("CPU",color=coreCyan,style=MaterialTheme.typography.labelSmall)
            Text("GPU",color=coreMint,style=MaterialTheme.typography.labelSmall)
        }
        ScopeTrack(recent,metric=0)
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("UTILIZATION",color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            Text("CPU",color=coreCyan,style=MaterialTheme.typography.labelSmall)
            Text("GPU",color=coreAmber,style=MaterialTheme.typography.labelSmall)
        }
        ScopeTrack(recent,metric=1)
        Text("Displayed points are 2–5 s telemetry samples, not individual frames.",
            style=MaterialTheme.typography.labelSmall,color=coreSecondary)
    }
}

@Composable
private fun ScopeTrack(points: List<LiveTracePoint>, metric: Int) {
    val finish=LocalSurfaceFinish.current
    val frame=when(finish) {
        SurfaceFinish.FROSTED->Color(0x6C070E17)
        SurfaceFinish.CRYSTAL_AERO->Color(0x580A2446)
        SurfaceFinish.SOLAR_PULSE->Color(0xC0102342)
        SurfaceFinish.COBALT_MATRIX->Color(0xFF071321)
    }
    Canvas(Modifier.fillMaxWidth().height(75.dp).background(frame,
        RoundedCornerShape(if(finish==SurfaceFinish.COBALT_MATRIX)4.dp else 9.dp))) {
        val w=size.width
        val h=size.height
        for (i in 1..3) {
            val y=h*i/4f
            drawLine(if(finish==SurfaceFinish.COBALT_MATRIX)
                coreCyan.copy(alpha=.22f) else Color(0x44516A81),
                Offset(0f,y),Offset(w,y),1f)
        }
        val t0=points.firstOrNull()?.timeMs ?: 0L
        val period=(points.lastOrNull()?.timeMs ?: t0)-t0
        fun trace(values: List<Double?>,color:Color) {
            if (period <= 0L) return
            val p=Path()
            var had=false
            for (i in points.indices) {
                val v=values[i]
                if (v==null || !v.isFinite()) { had=false;continue }
                val x=(points[i].timeMs-t0).toFloat()/period*w
                val y=(if(metric==0) ((90-v)/70.0) else ((100-v)/100.0))
                    .coerceIn(0.0,1.0).toFloat()*h
                if (!had) p.moveTo(x,y) else p.lineTo(x,y)
                had=true
            }
            drawPath(p,color,style=Stroke(width=2.dp.toPx()))
        }
        if (metric==0) {
            trace(points.map { it.cpuTemp },coreCyan)
            trace(points.map { it.gpuTemp },coreMint)
        } else {
            trace(points.map { it.cpuLoad },coreCyan)
            trace(points.map { it.gpuLoad },coreAmber)
        }
    }
}


/** Eight individual /proc/stat deltas. Policy clocks are separate measurements. */
@Composable
internal fun PerCoreLoadMatrix(encoded: String?) {
    val values = encoded?.split(';')?.mapNotNull { row ->
        val bits = row.split(':')
        if (bits.size == 2) bits[0] to bits[1].toIntOrNull() else null
    }?.toMap().orEmpty()
    val shape = RoundedCornerShape(15.dp)
    Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp)) {
        Text("CPU TOPOLOGY  ·  PER-CORE LOAD",color=coreText,
            style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (0..7).forEach { core ->
                val load = values["cpu$core"]
                val col = when {
                    core <= 2 -> coreCyan
                    core <= 6 -> coreMint
                    else -> coreAmber
                }
                Column(Modifier.weight(1f)) {
                    Text("C$core",color=coreSecondary,
                        style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace)
                    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                        val bw = size.width * .55f
                        val x = (size.width-bw)/2f
                        val radius=CornerRadius(5.dp.toPx())
                        drawRoundRect(Color(0xFF263849),topLeft=Offset(x,0f),
                            size=Size(bw,size.height),cornerRadius=radius)
                        if(load!=null) {
                            val amt = load.coerceIn(0,100)/100f
                            val height = size.height*amt
                            drawRoundRect(col,topLeft=Offset(x,size.height-height),
                                size=Size(bw,height.coerceAtLeast(1f)),cornerRadius=radius)
                        }
                    }
                    Text(load?.let { "$it%" } ?: "—",color=if(load==null) coreSecondary else col,
                        style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("C0–2 efficiency · C3–6 performance · C7 prime. Source: per-core /proc/stat.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
