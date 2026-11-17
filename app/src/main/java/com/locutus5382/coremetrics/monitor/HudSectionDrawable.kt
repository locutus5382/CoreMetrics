package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * A lightweight instrument-row panel, created only during HUD configuration.
 * The same colors/padding are used for each telemetry update; the background
 * never allocates bitmap blur, off-screen surfaces or shader frames.
 */
internal class HudSectionDrawable(
    private val density:Float,
    private val channel:Int
):Drawable() {
    private val background=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rail=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=when(channel) {
            0 -> Color.rgb(243,134,70)   // thermal amber instrument stripe
            1 -> Color.rgb(85,205,245)   // utilization cyan
            else -> Color.rgb(62,206,193) // resources teal
        }
    }
    private val outline=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=.8f*density
        color=Color.argb(90,102,164,210)
    }
    private var opacity=255

    override fun onBoundsChange(bounds:Rect) {
        super.onBoundsChange(bounds)
        val a=if(channel==0)110 else 95
        background.shader=LinearGradient(
            bounds.left.toFloat(),bounds.top.toFloat(),
            bounds.right.toFloat(),bounds.bottom.toFloat(),
            Color.argb(a,23,46,76),
            Color.argb(65,9,24,44),
            Shader.TileMode.CLAMP
        )
    }
    override fun draw(canvas:Canvas) {
        val r=bounds
        if(r.isEmpty)return
        val left=r.left.toFloat();val top=r.top.toFloat()
        val right=r.right.toFloat();val bottom=r.bottom.toFloat()
        val rad=5*density
        background.alpha=opacity
        canvas.drawRoundRect(left,top,right,bottom,rad,rad,background)
        outline.alpha=(opacity*.65f).toInt()
        canvas.drawRoundRect(left+.5f*density,top+.5f*density,
            right-.5f*density,bottom-.5f*density,rad,rad,outline)
        rail.alpha=opacity
        canvas.drawRoundRect(left,top+5*density,left+2.5f*density,
            bottom-5*density,1.2f*density,1.2f*density,rail)
    }
    override fun setAlpha(alpha:Int){opacity=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?){}
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}
