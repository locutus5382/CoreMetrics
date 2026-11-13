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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Brand palette fixed across ALL surfaces (no neon green/pink/purple). */
internal val cobaltNavy=Color(0xFF050B1E)
internal val cobalt=Color(0xFF2563EB)
internal val solarOrange=Color(0xFFFF6B00)
internal val solarGold=Color(0xFFFFC700)
internal val iceBlue=Color(0xFFB6E6FC)

@Composable
internal fun PureGlassBackdrop() {
    val style=LocalSurfaceFinish.current
    Canvas(Modifier.fillMaxSize()) {
        fun orb(center:Offset,radius:Float,color:Color,alpha:Float) {
            drawCircle(Brush.radialGradient(
                listOf(color.copy(alpha=alpha),color.copy(alpha=alpha*.24f),
                    Color.Transparent),center=center,radius=radius),
                center=center,radius=radius)
        }
        val w=size.width;val h=size.height
        val r=size.minDimension
        when(style) {
            SurfaceFinish.FROSTED -> {
                // ORIGINAL frosted ambient-orb identity intentionally unchanged.
                orb(Offset(w*.14f,h*.18f),r*.84f,cobalt,.34f)
                orb(Offset(w*.71f,h*.64f),r*.82f,solarOrange,.22f)
                orb(Offset(w*.87f,h*.14f),r*.64f,solarGold,.19f)
                orb(Offset(w*.28f,h*.90f),r*.86f,Color(0xFF0EA5E9),.21f)
            }
            SurfaceFinish.CRYSTAL_AERO -> {
                orb(Offset(w*.14f,h*.12f),r*.69f,cobalt,.42f)
                orb(Offset(w*.75f,h*.88f),r*.71f,solarOrange,.28f)
                orb(Offset(w*.82f,h*.15f),r*.60f,coreCyan,.31f)
                // STATIC silk ribbons: light passes behind translucent panes,
                // no live blur, shaders, camera capture or looping animation.
                fun ribbon(c:Color,low:Float,thick:Float) {
                    val path=Path().apply {
                        moveTo(-w*.12f,h*low)
                        cubicTo(w*.18f,h*(low-.30f),w*.46f,h*(low+.20f),
                            w*.68f,h*(low-.07f))
                        cubicTo(w*.84f,h*(low-.30f),w*.93f,h*(low-.04f),
                            w*1.15f,h*(low-.28f))
                    }
                    drawPath(path,Brush.linearGradient(listOf(
                        c.copy(alpha=.02f),c.copy(alpha=.16f),
                        Color.White.copy(alpha=.08f),c.copy(alpha=.03f))),
                        style=Stroke(width=thick,cap=StrokeCap.Round))
                }
                ribbon(cobalt,.60f,r*.14f)
                ribbon(coreCyan,.44f,r*.08f)
                ribbon(solarOrange,.92f,r*.13f)
                ribbon(solarGold,.78f,r*.05f)
            }
            SurfaceFinish.SOLAR_PULSE -> {
                orb(Offset(w*.75f,h*.27f),r*.76f,solarOrange,.16f)
                orb(Offset(w*.15f,h*.82f),r*.69f,cobalt,.31f)
                drawLine(solarOrange.copy(alpha=.12f),
                    Offset(0f,h*.12f),Offset(w,h*.12f),2.dp.toPx())
            }
            SurfaceFinish.COBALT_MATRIX -> {
                // Matte instrument-grid, safe static geometry.
                val spacing=56.dp.toPx()
                var x=0f
                while(x<w) {
                    drawLine(coreCyan.copy(alpha=.065f),
                        Offset(x,0f),Offset(x,h),1.dp.toPx())
                    x+=spacing
                }
                var y=0f
                while(y<h) {
                    drawLine(coreCyan.copy(alpha=.065f),
                        Offset(0f,y),Offset(w,y),1.dp.toPx())
                    y+=spacing
                }
                orb(Offset(w*.82f,h*.21f),r*.50f,cobalt,.18f)
            }
        }
    }
}

@Composable
internal fun Modifier.pureGlass(radius:Int=18):Modifier {
    val style=LocalSurfaceFinish.current
    val shape=RoundedCornerShape(
        if(style==SurfaceFinish.COBALT_MATRIX)(radius/2).coerceAtLeast(6).dp
        else radius.dp)
    return when(style) {
        SurfaceFinish.FROSTED -> this
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha=.17f),
                cobalt.copy(alpha=.13f),
                Color(0xFF091534).copy(alpha=.22f))),shape)
            .border(1.dp,Brush.linearGradient(listOf(
                Color.White.copy(alpha=.55f),
                iceBlue.copy(alpha=.22f),
                Color.White.copy(alpha=.09f))),shape)
        SurfaceFinish.CRYSTAL_AERO -> this
            // Clear material: only 8–10% surface fill, not opaque navy paint.
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha=.10f),
                coreCyan.copy(alpha=.075f),
                Color.White.copy(alpha=.045f),
                cobalt.copy(alpha=.07f))),shape)
            .border(1.25.dp,Brush.linearGradient(listOf(
                Color.White.copy(alpha=.86f),
                coreCyan.copy(alpha=.46f),
                Color.White.copy(alpha=.10f),
                iceBlue.copy(alpha=.69f))),shape)
        SurfaceFinish.SOLAR_PULSE -> this
            .background(Brush.linearGradient(listOf(
                Color(0xFF13254A),Color(0xFF091630),
                Color(0xFF07132A))),shape)
            .border(1.5.dp,Brush.linearGradient(listOf(
                solarOrange.copy(alpha=.74f),cobalt.copy(alpha=.65f),
                coreCyan.copy(alpha=.61f))),shape)
        SurfaceFinish.COBALT_MATRIX -> this
            .background(Color(0xFF081226),shape)
            .border(1.dp,Color(0xFF295174),shape)
    }
}

@Composable
internal fun PureGlassGauge(
    title:String,value:String?,progress:Float,
    accent:Color,modifier:Modifier=Modifier,subtitle:String?=null
) {
    val finish=LocalSurfaceFinish.current
    val displayAccent=when(finish) {
        SurfaceFinish.SOLAR_PULSE ->when(title) {
            "CPU"->solarOrange
            "GPU"->cobalt
            "Battery W","PWR"->solarGold
            else->coreCyan
        }
        SurfaceFinish.COBALT_MATRIX ->when(title){
            "CPU"->coreCyan
            "GPU"->cobalt
            "Battery W","PWR"->solarOrange
            else->solarGold
        }
        else->accent
    }
    Box(modifier.pureGlass(18).padding(8.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(114.dp),
            contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(103.dp)) {
                val stroke=4.dp.toPx()
                drawArc(color=Color.White.copy(alpha=.15f),
                    startAngle=135f,sweepAngle=270f,useCenter=false,
                    style=Stroke(stroke,cap=StrokeCap.Round))
                if(finish==SurfaceFinish.SOLAR_PULSE){
                    drawArc(color=solarGold.copy(alpha=.74f),
                        startAngle=135f,sweepAngle=270f,useCenter=false,
                        style=Stroke(1.5.dp.toPx(),pathEffect=
                            PathEffect.dashPathEffect(floatArrayOf(
                                7.dp.toPx(),6.dp.toPx()),0f)))
                }
                if(finish==SurfaceFinish.COBALT_MATRIX){
                    val mid=Offset(size.width/2f,size.height/2f)
                    val inner=size.minDimension*.455f
                    val outer=size.minDimension*.50f
                    for(i in 0 until 28){
                        val t=i/27f
                        val angle=Math.toRadians((135f+270f*t).toDouble())
                        val vx=cos(angle).toFloat()
                        val vy=sin(angle).toFloat()
                        val col=if(value!=null && t<=progress.coerceIn(0f,1f))
                            if(t>.75f)solarOrange else coreCyan
                            else iceBlue.copy(alpha=.24f)
                        drawLine(col,Offset(mid.x+vx*inner,mid.y+vy*inner),
                            Offset(mid.x+vx*outer,mid.y+vy*outer),
                            1.4.dp.toPx())
                    }
                }
                if(value!=null) {
                    val sweep=270f*progress.coerceIn(0f,1f)
                    if(finish==SurfaceFinish.COBALT_MATRIX ||
                        finish==SurfaceFinish.SOLAR_PULSE)
                        drawArc(color=displayAccent,startAngle=135f,
                            sweepAngle=sweep,useCenter=false,
                            style=Stroke(stroke,cap=StrokeCap.Round))
                    else drawArc(
                        brush=Brush.sweepGradient(listOf(displayAccent,
                            solarGold,displayAccent)),
                        startAngle=135f,sweepAngle=sweep,useCenter=false,
                        style=Stroke(stroke,cap=StrokeCap.Round))
                    if(finish==SurfaceFinish.COBALT_MATRIX){
                        val rad=Math.toRadians((135f+sweep).toDouble())
                        val center=Offset(size.width/2f,size.height/2f)
                        val rr=size.minDimension*.455f
                        drawCircle(solarGold,3.dp.toPx(),
                            Offset(center.x+cos(rad).toFloat()*rr,
                                center.y+sin(rad).toFloat()*rr))
                    }
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(value ?: "—",color=Color(0xFFF2F7FF),
                    fontWeight=FontWeight.SemiBold,fontSize=19.sp,maxLines=1)
                Text(title.uppercase(),color=iceBlue.copy(alpha=.8f),
                    style=MaterialTheme.typography.labelSmall,maxLines=1)
                subtitle?.let {Text(it,color=coreSecondary,fontSize=10.sp,maxLines=1)}
            }
        }
    }
}
