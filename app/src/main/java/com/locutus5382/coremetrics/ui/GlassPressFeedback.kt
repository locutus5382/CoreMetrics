package com.locutus5382.coremetrics.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Visual modifier ONLY. It deliberately owns no click listener, so Compose
 * Buttons, gamepad activation, focus semantics and accessibility stay intact.
 * All animation stops after the 220-ms interaction, with zero idle loop.
 */
@Composable
internal fun Modifier.cmPressFeedback(
    source:MutableInteractionSource,
    radiusDp:Int=17,
    accent:Color=solarGold
):Modifier {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue=if(pressed).96f else 1f,
        animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy,
            stiffness=Spring.StiffnessMedium),label="cmPressScale")
    val sweep=remember {Animatable(0f)}
    LaunchedEffect(pressed,focused) {
        if(pressed || focused) {
            sweep.snapTo(0f)
            sweep.animateTo(1f,tween(220))
        }else sweep.snapTo(0f)
    }
    return this.graphicsLayer {scaleX=scale;scaleY=scale}
        .drawWithContent {
            drawContent()
            val p=sweep.value
            if(p>0.01f && p<1f){
                val r=CornerRadius(radiusDp.dp.toPx())
                drawRoundRect(accent.copy(alpha=.08f*(1f-p)),
                    cornerRadius=r)
                drawRoundRect(
                    brush=Brush.linearGradient(
                        listOf(Color.Transparent,
                            accent.copy(alpha=.62f*(1f-p*.35f)),
                            Color.White.copy(alpha=.90f*(1f-p*.35f)),
                            Color.Transparent),
                        start=Offset(size.width*(p-.48f),0f),
                        end=Offset(size.width*(p+.22f),size.height)),
                    cornerRadius=r,style=Stroke(1.6.dp.toPx()))
            }
        }
}
