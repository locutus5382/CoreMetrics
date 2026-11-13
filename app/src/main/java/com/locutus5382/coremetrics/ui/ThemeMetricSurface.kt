package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp

/**
 * Small in-card measurement surface. The Frosted branch is deliberately
 * unaltered. The other three finishes share this primitive across Sessions,
 * diagnostics and Live pages; no additional frame-time instrumentation.
 */
@Composable
internal fun Modifier.cmMetricSurface(
    accent:Color=coreCyan,
    frostedTint:Color=Color(0x330C1D36)
):Modifier {
    val finish=LocalSurfaceFinish.current
    val pal=finish.palette
    val shape=RoundedCornerShape(if(pal.sharpCorners)5.dp else 10.dp)
    return when(finish) {
        SurfaceFinish.FROSTED -> this.background(frostedTint,
            RoundedCornerShape(9.dp))
        SurfaceFinish.CRYSTAL_AERO -> this
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha=.17f),
                Color(0xFF164479).copy(alpha=.22f),
                Color(0xFF071A39).copy(alpha=.19f))),shape)
            .border(1.dp,Brush.linearGradient(listOf(
                Color.White.copy(alpha=.71f),accent.copy(alpha=.46f),
                pal.borderSoft.copy(alpha=.62f))),shape)
        SurfaceFinish.SOLAR_PULSE -> this
            .background(Brush.linearGradient(listOf(
                accent.copy(alpha=.19f),
                Color(0xFF102B52),
                Color(0xFF0A1D3D))),shape)
            .border(1.2.dp,Brush.linearGradient(listOf(
                accent.copy(alpha=.95f),
                Color(0xFF4686DB).copy(alpha=.75f))),shape)
        SurfaceFinish.COBALT_MATRIX -> this
            .background(Color(0xFF081827),shape)
            .border(1.dp,Color(0xFF385672),shape)
            .drawWithContent {
                drawContent()
                drawLine(accent.copy(alpha=.75f),
                    Offset(6.dp.toPx(),size.height-1.7.dp.toPx()),
                    Offset(size.width*.38f,size.height-1.7.dp.toPx()),
                    1.dp.toPx())
            }
    }
}
