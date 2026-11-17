package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Tiny static drawing primitive, allocated only when the HUD changes style
 * or opacity. No screenshots, RenderEffect, real-time blur, or hardware read.
 * DUKO Lens uses a translucent fallback because global blur-behind would
 * blur the GAME, not just the HUD, and is not safe on all vendor ROMs.
 */
internal class HudFrameDrawable(
    private val theme:String,
    private val opacity:Float,
    private val density:Float
):Drawable(){
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=1.2f*density
        color=Color.rgb(97,216,244)
    }
    private val accents=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=2f*density
        color=Color.rgb(61,222,247)
    }
    private val shape=Path()
    private var activeAlpha=255

    override fun onBoundsChange(bounds:Rect) {
        super.onBoundsChange(bounds)
        val w=bounds.width().toFloat()
        val h=bounds.height().toFloat()
        shape.reset()
        val id=HudLayoutPolicy.normalize(theme)
        val chamfer=when(id) {
            HudLayoutPolicy.STRIKER->min(15*density,h*.22f)
            HudLayoutPolicy.TACTICAL->min(9*density,h*.10f)
            else->0f
        }
        shape.moveTo(chamfer,0f)
        shape.lineTo(w-chamfer,0f)
        shape.lineTo(w,h.coerceAtLeast(chamfer)-h+chamfer)
        shape.lineTo(w,h-chamfer)
        shape.lineTo(w-chamfer,h)
        shape.lineTo(chamfer,h)
        shape.lineTo(0f,h-chamfer)
        shape.lineTo(0f,chamfer)
        shape.close()
        val alpha=(opacity.coerceIn(.30f,.90f)*255).toInt()
        val base=when(id) {
            HudLayoutPolicy.STRIKER->intArrayOf(
                Color.argb(alpha,3,17,31),
                Color.argb(alpha,7,40,65))
            HudLayoutPolicy.STRIP->intArrayOf(
                Color.argb(alpha,3,13,28),
                Color.argb(alpha,15,29,51))
            HudLayoutPolicy.TACTICAL->intArrayOf(
                Color.argb(alpha,6,17,34),
                Color.argb(alpha,10,31,51))
            HudLayoutPolicy.DUKO->intArrayOf(
                Color.argb(alpha,17,24,35),
                Color.argb(alpha,31,40,54))
            else->intArrayOf(Color.TRANSPARENT,Color.TRANSPARENT)
        }
        fill.shader=LinearGradient(0f,0f,w,h,base,null,Shader.TileMode.CLAMP)
    }
    override fun draw(canvas:Canvas) {
        val id=HudLayoutPolicy.normalize(theme)
        if(id==HudLayoutPolicy.GHOST)return
        val r=bounds
        if(r.isEmpty)return
        val save=canvas.save()
        canvas.translate(r.left.toFloat(),r.top.toFloat())
        fill.alpha=activeAlpha
        canvas.drawPath(shape,fill)
        val w=r.width().toFloat();val h=r.height().toFloat()
        when(id) {
            HudLayoutPolicy.STRIKER->{
                line.color=Color.rgb(71,221,252);line.alpha=activeAlpha
                canvas.drawPath(shape,line)
                val x=w*.67f
                accents.alpha=activeAlpha
                canvas.drawLine(x+10*density,2*density,
                    x-10*density,h-2*density,accents)
                canvas.drawLine(9*density,3*density,33*density,3*density,accents)
            }
            HudLayoutPolicy.TACTICAL->{
                line.color=Color.rgb(120,171,200);line.alpha=activeAlpha
                canvas.drawPath(shape,line)
                accents.alpha=activeAlpha
                canvas.drawLine(6*density,3*density,32*density,3*density,accents)
                canvas.drawLine(w-32*density,h-3*density,
                    w-6*density,h-3*density,accents)
            }
            HudLayoutPolicy.STRIP->{
                line.color=Color.rgb(73,125,161);line.alpha=activeAlpha
                canvas.drawPath(shape,line)
            }
            HudLayoutPolicy.DUKO->{
                // Four precise laboratory framing brackets, not a neon box.
                val inset=3*density
                val arm=12*density
                line.color=Color.argb(185,214,229,244)
                line.strokeWidth=1*density
                line.alpha=activeAlpha
                for(x in arrayOf(inset,w-inset))for(y in arrayOf(inset,h-inset)){
                    val dx=if(x<w/2)arm else -arm
                    val dy=if(y<h/2)arm else -arm
                    canvas.drawLine(x,y,x+dx,y,line)
                    canvas.drawLine(x,y,x,y+dy,line)
                }
            }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int) {activeAlpha=alpha.coerceIn(0,255);invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?) {fill.colorFilter=colorFilter}
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}

/** Only the tiny 2dp marker is visually present; parent is a 44dp tap target. */
internal class HudEdgeLineDrawable(
    private val density:Float,
    private val leftEdge:Boolean
):Drawable(){
    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(90,218,250)}
    private val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(255,91,106)}
    override fun draw(canvas:Canvas) {
        val b=bounds
        val x=if(leftEdge)b.left+1.5f*density else b.right-1.5f*density
        canvas.drawRect(x-1*density,b.top+8*density,
            x+1*density,b.bottom-8*density,line)
        canvas.drawCircle(x,b.centerY().toFloat(),3.5f*density,dot)
    }
    override fun setAlpha(alpha:Int){line.alpha=alpha;dot.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){}
    @Deprecated("Deprecated in Java")override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}
