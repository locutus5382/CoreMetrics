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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
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

/** Static painted scenery, not animated, shader-blurred or sampled. */
@Composable
internal fun PureGlassBackdrop() {
    val style=LocalSurfaceFinish.current
    if(style==SurfaceFinish.COBALT_MATRIX)return
    Canvas(Modifier.fillMaxSize()) {
        fun orb(center:Offset,radius:Float,color:Color,alpha:Float) {
            drawCircle(Brush.radialGradient(listOf(
                color.copy(alpha=alpha),color.copy(alpha=alpha*.20f),
                Color.Transparent),center=center,radius=radius),
                center=center,radius=radius)
        }
        val w=size.width;val h=size.height;val r=size.minDimension
        when(style) {
            SurfaceFinish.FROSTED -> {
                orb(Offset(w*.14f,h*.18f),r*.84f,cobalt,.34f)
                orb(Offset(w*.71f,h*.64f),r*.82f,solarOrange,.22f)
                orb(Offset(w*.87f,h*.14f),r*.64f,solarGold,.19f)
                orb(Offset(w*.28f,h*.90f),r*.86f,Color(0xFF0EA5E9),.21f)
            }
            SurfaceFinish.CRYSTAL_AERO -> {
                orb(Offset(w*.22f,h*.15f),r*.75f,cobalt,.25f)
                orb(Offset(w*.78f,h*.83f),r*.70f,solarOrange,.15f)
                val ribbons=listOf(
                    Triple(coreCyan,h*.17f,r*.045f),
                    Triple(cobalt,h*.39f,r*.07f),
                    Triple(solarOrange,h*.65f,r*.04f),
                    Triple(solarGold,h*.81f,r*.018f)
                )
                ribbons.forEachIndexed { index,(color,y,width) ->
                    val wave=Path().apply {
                        moveTo(-w*.08f,y)
                        cubicTo(w*.20f,y-h*.37f,w*.45f,y+h*.40f,
                            w*1.08f,y-h*.17f)
                    }
                    drawPath(wave,color.copy(alpha=if(index<2).45f else .32f),
                        style=Stroke(width=width,cap=StrokeCap.Round))
                    drawPath(wave,Color.White.copy(alpha=.12f),
                        style=Stroke(width=(width*.09f).coerceAtLeast(1f)))
                }
            }
            SurfaceFinish.SOLAR_PULSE -> {
                orb(Offset(w*.9f,h*.2f),r*.65f,solarOrange,.16f)
                orb(Offset(w*.2f,h*.9f),r*.56f,cobalt,.22f)
            }
            else -> Unit
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
        SurfaceFinish.CRYSTAL_AERO -> this
            // Clear panes against painted ribbons. Real refraction is NOT
            // claimed: device-wide blur would be expensive and unreliable.
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha=.10f),
                cobalt.copy(alpha=.055f),
                Color(0xFF06122A).copy(alpha=.15f)
            )),shape)
            .border(1.25.dp,Brush.linearGradient(listOf(
                Color.White.copy(alpha=.88f),
                iceBlue.copy(alpha=.32f),
                coreCyan.copy(alpha=.29f),
                Color.White.copy(alpha=.78f)
            )),shape)
        SurfaceFinish.SOLAR_PULSE -> this
            .background(Brush.linearGradient(listOf(
                Color(0xFF0A2042),Color(0xFF0D1931),
                Color(0xFF070F22))),shape)
            .border(1.25.dp,Brush.linearGradient(listOf(
                cobalt.copy(alpha=.85f),solarOrange.copy(alpha=.55f),
                solarGold.copy(alpha=.38f)
            )),shape)
        SurfaceFinish.COBALT_MATRIX -> this
            .background(Color(0xFF081226),shape)
            .border(1.dp,Color(0xFF28557F),shape)
    }
}

@Composable
internal fun PureGlassGauge(
    title:String,value:String?,progress:Float,
    accent:Color,modifier:Modifier=Modifier,subtitle:String?=null
) {
    val finish=LocalSurfaceFinish.current
    val displayAccent=when(finish) {
        SurfaceFinish.COBALT_MATRIX -> when(title) {
            "CPU" -> coreCyan
            "GPU" -> solarOrange
            "Battery W","PWR" -> solarGold
            else -> accent
        }
        SurfaceFinish.SOLAR_PULSE -> when(title){
            "CPU" -> solarOrange
            "GPU" -> coreCyan
            "Battery W","PWR" -> solarGold
            else -> cobalt
        }
        else -> accent
    }
    Box(modifier.pureGlass(18).padding(8.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(114.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(94.dp)) {
                val stroke=4.dp.toPx()
                drawArc(color=Color.White.copy(alpha=.15f),
                    startAngle=135f,sweepAngle=270f,useCenter=false,
                    style=Stroke(stroke,cap=StrokeCap.Round))
                if(finish==SurfaceFinish.COBALT_MATRIX){
                    // 28 radial instrument ticks, including missing-value view.
                    repeat(28){i->
                        val rad=(135f+i*270f/27f)*PI.toFloat()/180f
                        val radiusOuter=size.minDimension*.49f
                        val radiusInner=radiusOuter*(if(i%3==0).84f else .91f)
                        val c=Offset(size.center.x+cos(rad)*radiusOuter,
                            size.center.y+sin(rad)*radiusOuter)
                        val a=Offset(size.center.x+cos(rad)*radiusInner,
                            size.center.y+sin(rad)*radiusInner)
                        drawLine(if(value!=null &&
                            i<=((progress.coerceIn(0f,1f))*27).toInt())
                            displayAccent else Color(0xFF2C4360),
                            a,c,if(i%3==0)2.dp.toPx() else 1.dp.toPx())
                    }
                }
                if(value!=null) {
                    val sweep=270f*progress.coerceIn(0f,1f)
                    when(finish){
                        SurfaceFinish.COBALT_MATRIX -> drawArc(displayAccent,
                            135f,sweep,false,style=Stroke(stroke,cap=StrokeCap.Square))
                        SurfaceFinish.SOLAR_PULSE -> {
                            drawArc(displayAccent,135f,sweep,false,
                                style=Stroke(stroke*1.35f,cap=StrokeCap.Round))
                            drawArc(solarGold.copy(alpha=.82f),135f,270f,false,
                                style=Stroke(1.dp.toPx(),pathEffect=
                                    PathEffect.dashPathEffect(floatArrayOf(9f,7f))))
                        }
                        else -> drawArc(brush=Brush.sweepGradient(
                            listOf(displayAccent,solarGold,displayAccent)),
                            startAngle=135f,sweepAngle=sweep,useCenter=false,
                            style=Stroke(stroke,cap=StrokeCap.Round))
                    }
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(value ?: "—",color=Color(0xFFF2F7FF),
                    fontWeight=FontWeight.SemiBold,fontSize=19.sp,
                    maxLines=1)
                Text(title.uppercase(),color=iceBlue.copy(alpha=.8f),
                    style=MaterialTheme.typography.labelSmall,maxLines=1)
                subtitle?.let {
                    Text(it,color=coreSecondary,fontSize=10.sp,maxLines=1)
                }
            }
        }
    }
}
