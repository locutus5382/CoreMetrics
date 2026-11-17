package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Independently framed low-profile diagnostic lane for Tactical Pod.
 * Draw resources are constructed once and reused. Never introduces a
 * recording, telemetry or animation loop. Applied only on theme change.
 */
internal class HudTacticalCellDrawable(
    private val lane:Int,
    private val density:Float
):Drawable() {
    private val plate=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
    private val border=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=.8f*density
        color=Color.argb(112,132,192,222)
    }
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL
        color=when(lane) {
            0->Color.rgb(246,148,72)
            1->Color.rgb(75,204,236)
            else->Color.rgb(124,154,239)
        }
    }
    private val inset=RectF()
    private var opacity=255
    override fun onBoundsChange(r:Rect){
        super.onBoundsChange(r)
        inset.set(r.left+1.5f*density,r.top+1.2f*density,
            r.right-1.5f*density,r.bottom-1.2f*density)
        plate.shader=LinearGradient(r.left.toFloat(),r.top.toFloat(),
            r.right.toFloat(),r.bottom.toFloat(),
            intArrayOf(Color.argb(196,17,39,61),Color.argb(176,7,18,39)),
            null,Shader.TileMode.CLAMP)
    }
    override fun draw(canvas:Canvas){
        if(inset.isEmpty)return
        plate.alpha=opacity
        border.alpha=(opacity*112)/255
        edge.alpha=(opacity*218)/255
        val radius=4*density
        canvas.drawRoundRect(inset,radius,radius,plate)
        canvas.drawRoundRect(inset,radius,radius,border)
        canvas.drawRoundRect(inset.left,inset.top+4*density,
            inset.left+2.1f*density,inset.bottom-4*density,
            .8f*density,.8f*density,edge)
        canvas.drawRect(inset.left+9*density,inset.top+1*density,
            inset.left+32*density,inset.top+1.5f*density,edge)
    }
    override fun setAlpha(alpha:Int){opacity=alpha.coerceIn(0,255);invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){plate.colorFilter=filter}
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}
