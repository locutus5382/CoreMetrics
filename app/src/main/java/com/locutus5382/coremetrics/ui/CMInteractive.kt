package com.locutus5382.coremetrics.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** One shot on touch/controller press or focus. No idle timer or infinite
 * animation, and no second clickable/focusable node is introduced.
 */
internal fun Modifier.cmInteractive(
    cornerRadiusDp:Int=12,accent:Color=solarGold,
    onClick:()->Unit
):Modifier=composed {
    val src=remember{MutableInteractionSource()}
    val pressed by src.collectIsPressedAsState()
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue=if(pressed).96f else 1f,
        animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy,
            stiffness=Spring.StiffnessMedium),label="cmSpringPress")
    val shine by animateFloatAsState(
        targetValue=if(pressed||focused)1f else 0f,
        animationSpec=tween(220),label="cmSpecularGlint")
    this.graphicsLayer{scaleX=scale;scaleY=scale}
        .drawWithContent {
            drawContent()
            if(shine>.01f) {
                val radius=cornerRadiusDp.dp.toPx()
                val x=size.width*shine
                val streak=Brush.linearGradient(
                    listOf(Color.Transparent,
                        accent.copy(alpha=.13f*shine),
                        Color.White.copy(alpha=.22f*shine),
                        Color.Transparent),
                    start=Offset(x-size.width*.65f,0f),
                    end=Offset(x+size.width*.12f,size.height))
                drawRoundRect(brush=streak,
                    cornerRadius=CornerRadius(radius,radius))
                drawRoundRect(
                    brush=Brush.linearGradient(
                        listOf(Color.Transparent,
                            accent.copy(alpha=.72f*shine),
                            Color.White.copy(alpha=.88f*shine),
                            Color.Transparent),
                        start=Offset(x-size.width*.62f,0f),
                        end=Offset(x+size.width*.12f,size.height)),
                    cornerRadius=CornerRadius(radius,radius),
                    style=Stroke(width=1.5.dp.toPx()))
            }
        }
        .clickable(interactionSource=src,indication=null,role=Role.Button,
            onClick=onClick)
}

@Composable
internal fun CMActionPill(text:String,primary:Boolean=false,onClick:()->Unit) {
    val style=LocalSurfaceFinish.current
    val shape=RoundedCornerShape(if(style==SurfaceFinish.COBALT_MATRIX)9.dp else 18.dp)
    val background=when {
        style==SurfaceFinish.CRYSTAL_AERO ->Brush.linearGradient(listOf(
            if(primary)solarOrange.copy(alpha=.32f) else Color.White.copy(alpha=.15f),
            cobalt.copy(alpha=.14f)))
        style==SurfaceFinish.SOLAR_PULSE ->Brush.linearGradient(listOf(
            if(primary)Color(0xFFCB5200) else Color(0xFF2457C5),
            if(primary)solarOrange else Color(0xFF16639B)))
        style==SurfaceFinish.COBALT_MATRIX ->Brush.linearGradient(listOf(
            if(primary)cobalt else Color(0xFF081226),
            if(primary)coreCyan else Color(0xFF152F53)))
        else->Brush.linearGradient(listOf(
            if(primary)solarOrange else Color.White.copy(alpha=.13f),
            if(primary)Color(0xFFDE5700) else cobalt.copy(alpha=.12f)))
    }
    val textColor=Color.White
    Box(Modifier.background(background,shape)
        .border(1.dp,if(primary)solarGold else iceBlue.copy(alpha=.64f),shape)
        .cmInteractive(if(style==SurfaceFinish.COBALT_MATRIX)9 else 18,
            accent=if(primary)solarGold else coreCyan,onClick=onClick)
        .padding(horizontal=15.dp,vertical=11.dp)) {
        Text(text,color=textColor,fontWeight=FontWeight.Bold,maxLines=1,
            fontFamily=if(style==SurfaceFinish.COBALT_MATRIX)
                FontFamily.Monospace else FontFamily.Default)
    }
}
