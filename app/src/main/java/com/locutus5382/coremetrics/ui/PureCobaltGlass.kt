package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cobalt & Solar Glass: original static Compose gradients; no expensive blur. */
internal val cobaltNavy=Color(0xFF050B1E)
internal val cobalt=Color(0xFF2563EB)
internal val solarOrange=Color(0xFFFF6B00)
internal val solarGold=Color(0xFFFFC700)
internal val iceBlue=Color(0xFFB6E6FC)

@Composable
internal fun PureGlassBackdrop() {
    val style=LocalSurfaceFinish.current
    if(style==SurfaceFinish.TACTICAL)return
    Canvas(Modifier.fillMaxSize()) {
        fun orb(center:Offset,radius:Float,color:Color,alpha:Float) {
            drawCircle(Brush.radialGradient(
                listOf(color.copy(alpha=alpha),color.copy(alpha=alpha*.24f),
                    Color.Transparent),center=center,radius=radius),
                center=center,radius=radius)
        }
        val w=size.width;val h=size.height
        val r=size.minDimension
        if(style==SurfaceFinish.FROSTED) {
            orb(Offset(w*.14f,h*.18f),r*.84f,cobalt,.34f)
            orb(Offset(w*.71f,h*.64f),r*.82f,solarOrange,.22f)
            orb(Offset(w*.87f,h*.14f),r*.64f,solarGold,.19f)
            orb(Offset(w*.28f,h*.90f),r*.86f,Color(0xFF0EA5E9),.21f)
        } else {
            // Liquid: leaner backdrop, specular surface borders carry the look.
            orb(Offset(w*.22f,h*.14f),r*.72f,cobalt,.24f)
            orb(Offset(w*.80f,h*.73f),r*.86f,solarOrange,.10f)
            orb(Offset(w*.68f,h*.13f),r*.50f,Color(0xFF9ECAFF),.13f)
        }
    }
}

@Composable
internal fun Modifier.pureGlass(radius:Int=18):Modifier {
    val shape=RoundedCornerShape(radius.dp)
    return when(LocalSurfaceFinish.current) {
        SurfaceFinish.FROSTED -> this
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha=.17f),
                cobalt.copy(alpha=.13f),
                Color(0xFF091534).copy(alpha=.22f)
            )),shape)
            .border(1.dp,Brush.linearGradient(listOf(
                Color.White.copy(alpha=.55f),
                iceBlue.copy(alpha=.22f),
                Color.White.copy(alpha=.09f)
            )),shape)

        SurfaceFinish.LIQUID -> this
            // Static highlights: no blur applied to the card's TEXT/content.
            // No time-based gradients, no global graphicsLayer allocation.
            .background(Brush.linearGradient(
                listOf(Color(0xEE1E3866),Color(0xEE07132A),
                    Color(0xED071329),Color(0xE3152A52))
            ),shape)
            .border(2.dp,Brush.linearGradient(
                listOf(Color(0xCFEAF7FF),Color(0xA66BC9F5),
                    Color(0x506C88C8),Color(0xC4DDEEFF))
            ),shape)

        SurfaceFinish.TACTICAL -> this
            .background(Color(0xFF121620),shape)
            .border(1.dp,Color(0xFF354057),shape)
    }
}

@Composable
internal fun PureGlassGauge(
    title:String,value:String?,progress:Float,
    accent:Color,modifier:Modifier=Modifier
) {
    val finish=LocalSurfaceFinish.current
    val displayAccent=if(finish==SurfaceFinish.TACTICAL)when(title) {
        "CPU" -> coreMint
        "GPU" -> Color(0xFF38BDF8)
        "Battery W","PWR" -> solarOrange
        else -> accent
    } else accent
    Box(modifier.pureGlass(18).padding(8.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(103.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(94.dp)) {
                val stroke=4.dp.toPx()
                drawArc(color=Color.White.copy(alpha=.15f),
                    startAngle=135f,sweepAngle=270f,useCenter=false,
                    style=Stroke(stroke,cap=StrokeCap.Round))
                if(value!=null) {
                    val sweep=270f*progress.coerceIn(0f,1f)
                    if(finish==SurfaceFinish.TACTICAL)drawArc(
                        color=displayAccent,startAngle=135f,
                        sweepAngle=sweep,useCenter=false,
                        style=Stroke(stroke,cap=StrokeCap.Round))
                    else drawArc(
                        brush=Brush.sweepGradient(listOf(displayAccent,
                            solarGold,displayAccent)),
                        startAngle=135f,sweepAngle=sweep,useCenter=false,
                        style=Stroke(stroke,cap=StrokeCap.Round))
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(value ?: "—",color=Color(0xFFF2F7FF),
                    fontWeight=FontWeight.SemiBold,fontSize=19.sp,
                    maxLines=1)
                Text(title.uppercase(),color=iceBlue.copy(alpha=.8f),
                    style=MaterialTheme.typography.labelSmall,maxLines=1)
            }
        }
    }
}
