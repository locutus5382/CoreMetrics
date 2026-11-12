package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * CoreMetrics flat glass padlock for the 38dp HUD action and 48dp recovery chip.
 *
 * Simple vector strokes, dark-navy translucent glass and one cobalt/solar
 * status accent. No emoji fonts, bitmaps, animation, GPU shaders or allocations
 * outside this view's own draw lifecycle. It never changes the touch window
 * geometry or LOCK/UNLOCK behavior.
 */
internal class FlatLockChipDrawable(private val locked:Boolean):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin=Paint.Join.ROUND
        strokeCap=Paint.Cap.ROUND
    }
    private val rect=RectF()
    private val arch=Path()
    private var opacity=255
    private val accent=if(locked) Color.rgb(255,199,71) else Color.rgb(97,207,255)
    private fun rgba(base:Int,alpha:Int):Int =
        Color.argb(alpha*opacity/255,Color.red(base),Color.green(base),Color.blue(base))
    override fun draw(canvas:Canvas) {
        if(bounds.width()<=0 || bounds.height()<=0)return
        val previous=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        canvas.scale(bounds.width()/48f,bounds.height()/48f)

        // HUD-style single-layer frosted chip: no bulky raised button shadow.
        rect.set(1.25f,1.25f,46.75f,46.75f)
        paint.style=Paint.Style.FILL
        paint.color=rgba(Color.rgb(8,23,45),209)
        canvas.drawRoundRect(rect,10f,10f,paint)
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=1f
        paint.color=rgba(Color.rgb(154,215,248),143)
        canvas.drawRoundRect(rect,10f,10f,paint)

        // Restrained luminous upper hairline; matches cobalt glass HUD border.
        paint.color=rgba(Color.rgb(199,236,255),104)
        canvas.drawLine(12f,5.3f,36f,5.3f,paint)

        // Crisp geometric lock body (not a platform-dependent emoji).
        paint.style=Paint.Style.FILL
        paint.color=rgba(accent,24)
        rect.set(16f,23.1f,32f,35f)
        canvas.drawRoundRect(rect,3.2f,3.2f,paint)
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=2f
        paint.color=rgba(accent,255)
        canvas.drawRoundRect(rect,3.2f,3.2f,paint)

        arch.reset()
        if(locked) {
            arch.moveTo(19.3f,23.1f)
            arch.lineTo(19.3f,19.7f)
            arch.cubicTo(19.3f,13.2f,28.7f,13.2f,28.7f,19.7f)
            arch.lineTo(28.7f,23.1f)
        } else {
            arch.moveTo(19.3f,23.1f)
            arch.lineTo(19.3f,19.6f)
            arch.cubicTo(19.3f,14f,23.2f,12.6f,27f,13.6f)
            arch.cubicTo(31.3f,14.6f,32.8f,17.9f,32.8f,20.0f)
        }
        paint.strokeWidth=2.4f
        canvas.drawPath(arch,paint)

        paint.style=Paint.Style.FILL
        canvas.drawCircle(24f,28f,1.4f,paint)
        paint.strokeWidth=1.6f
        paint.style=Paint.Style.STROKE
        canvas.drawLine(24f,29.8f,24f,32f,paint)

        // Tiny state indicator: Solar gold when locked, cobalt when open.
        paint.style=Paint.Style.FILL
        paint.color=rgba(accent,235)
        canvas.drawCircle(38.3f,10f,2.0f,paint)

        canvas.restoreToCount(previous)
    }
    override fun setAlpha(alpha:Int) {
        opacity=alpha.coerceIn(0,255)
        invalidateSelf()
    }
    override fun setColorFilter(filter:ColorFilter?) {
        paint.colorFilter=filter
        invalidateSelf()
    }
    @Deprecated("Use Drawable#getOpacity only for legacy APIs")
    override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}
