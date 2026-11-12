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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val coreNight = Color(0xFF090D16)
internal val coreCard = Color(0xD9111C2C)
internal val coreBorder = Color(0xFF345066)
internal val coreCyan = Color(0xFF63D6EE)
internal val coreMint = Color(0xFF69E1B8)
internal val coreAmber = Color(0xFFFFC37E)
internal val coreText = Color(0xFFECF6FC)
internal val coreSecondary = Color(0xFF9FB8C7)

internal data class LiveTracePoint(
    val timeMs: Long,
    val cpuTemp: Double?,
    val gpuTemp: Double?,
    val cpuLoad: Double?,
    val gpuLoad: Double?
)

/** Original unobtrusive backdrop: no bitmap, no blur, no animation. */
@Composable
internal fun InstrumentBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val sizeRef = size.minDimension
        val upper = Offset(size.width * .86f, size.height * .08f)
        val lower = Offset(size.width * .14f, size.height * .96f)
        drawCircle(Brush.radialGradient(
            listOf(Color(0x6C21445D),Color(0x19223972),Color.Transparent),
            center=upper,radius=sizeRef*.96f),
            center=upper,radius=sizeRef*.96f)
        drawCircle(Brush.radialGradient(
            listOf(Color(0x44314A68),Color.Transparent),
            center=lower,radius=sizeRef*.82f),
            center=lower,radius=sizeRef*.82f)
        val gridStep = 58.dp.toPx()
        var x = gridStep
        while (x < size.width) {
            drawLine(Color(0x122A5E76),Offset(x,0f),Offset(x,size.height),1f)
            x += gridStep
        }
    }
}

@Composable
internal fun InstrumentKpi(
    title: String, value: String?, detail: String,
    color: Color = coreCyan, modifier: Modifier = Modifier
) {
    val r = RoundedCornerShape(15.dp)
    Column(modifier
        .background(Brush.verticalGradient(
            listOf(Color(0xDA1A2D42),Color(0xB0111927))),r)
        .border(1.dp,coreBorder,r)
        .padding(horizontal=12.dp,vertical=11.dp)) {
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
    val r = RoundedCornerShape(15.dp)
    Column(Modifier.fillMaxWidth().background(coreCard,r)
        .border(1.dp,coreBorder,r).padding(12.dp),
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
        Text("Displayed points are 1–5 s telemetry samples, not individual frames.",
            style=MaterialTheme.typography.labelSmall,color=coreSecondary)
    }
}

@Composable
private fun ScopeTrack(points: List<LiveTracePoint>, metric: Int) {
    Canvas(Modifier.fillMaxWidth().height(75.dp).background(Color(0x6C070E17),
        RoundedCornerShape(9.dp))) {
        val w=size.width
        val h=size.height
        for (i in 1..3) {
            val y=h*i/4f
            drawLine(Color(0x44516A81),Offset(0f,y),Offset(w,y),1f)
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
    Column(Modifier.fillMaxWidth().background(coreCard,shape)
        .border(1.dp,coreBorder,shape).padding(12.dp)) {
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
