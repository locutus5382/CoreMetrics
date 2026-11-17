package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Precision chrome for the five overlay styles, inspired by the visual
 * references supplied for CoreMetrics. Static canvas geometry and gradients
 * are cached on bounds changes; no bitmaps, screenshots, blur filters, nested
 * Compose tree or per-frame temporary arrays.
 *
 * The Android overlay is translucent glass, not a fabricated real-time
 * blur of the game content. The latter is vendor-dependent / expensive.
 */
internal class HudFrameDrawable(
    private val theme:String,
    private val opacity:Float,
    private val density:Float
):Drawable() {
    private val id=HudLayoutPolicy.normalize(theme)
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
    private val scrim=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
    private val hair=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=1f*density}
    private val glow=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=3f*density}
    private val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=1.9f*density}
    private val marks=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=.7f*density}
    private val path=Path()
    private val inset=Path()
    private val cell=RectF()
    private var globalAlpha=255
    private var w=0f
    private var h=0f
    private val cyan=Color.rgb(79,224,252)

    override fun onBoundsChange(bounds:Rect) {
        super.onBoundsChange(bounds)
        w=bounds.width().toFloat();h=bounds.height().toFloat()
        if(w<=0f || h<=0f)return
        path.reset();inset.reset()
        val cut=when(id) {
            HudLayoutPolicy.STRIKER->min(14*density,h*.18f)
            HudLayoutPolicy.TACTICAL->min(7*density,h*.10f)
            else->if(id==HudLayoutPolicy.STRIP)min(3*density,h*.05f) else 0f
        }
        fun polygon(out:Path,l:Float,t:Float,r:Float,b:Float,c:Float){
            out.moveTo(l+c,t)
            out.lineTo(r-c,t);out.lineTo(r,t+c)
            out.lineTo(r,b-c);out.lineTo(r-c,b)
            out.lineTo(l+c,b);out.lineTo(l,b-c)
            out.lineTo(l,t+c);out.close()
        }
        polygon(path,0f,0f,w,h,cut)
        polygon(inset,3*density,3*density,w-3*density,h-3*density,
            (cut-2*density).coerceAtLeast(0f))
        val a=(opacity.coerceIn(.30f,.90f)*255).toInt()
        val stops=when(id) {
            HudLayoutPolicy.STRIKER->intArrayOf(
                Color.argb(a,3,12,29),Color.argb(a,13,37,65),
                Color.argb(a,5,18,38))
            HudLayoutPolicy.TACTICAL->intArrayOf(
                Color.argb(a,6,14,29),Color.argb(a,15,36,65),
                Color.argb(a,6,18,33))
            HudLayoutPolicy.STRIP->intArrayOf(
                Color.argb(a,3,9,22),Color.argb(a,13,28,45),
                Color.argb(a,4,11,25))
            HudLayoutPolicy.DUKO->intArrayOf(
                Color.argb(a,13,19,29),Color.argb(a,27,36,51),
                Color.argb(a,12,20,31))
            else->intArrayOf(
                Color.argb((a*.57f).toInt(),2,8,20),
                Color.argb((a*.60f).toInt(),4,10,21),
                Color.argb((a*.57f).toInt(),2,8,20))
        }
        fill.shader=LinearGradient(0f,0f,w,h*.9f,stops,null,
            Shader.TileMode.CLAMP)
        scrim.shader=LinearGradient(0f,0f,w,0f,intArrayOf(
            Color.TRANSPARENT,
            Color.argb((a*.45f).toInt(),2,8,20),
            Color.argb((a*.72f).toInt(),2,8,19)
        ),null,Shader.TileMode.CLAMP)
        cell.set(4*density,4*density,w-4*density,h-4*density)
    }

    override fun draw(canvas:Canvas) {
        if(w<=0f || h<=0f)return
        val n=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        fill.alpha=globalAlpha
        canvas.drawPath(path,fill)
        if(id==HudLayoutPolicy.GHOST){
            // Borderless, but readable over the bright sky in Eden.
            canvas.restoreToCount(n)
            return
        }
        hair.alpha=globalAlpha
        glow.alpha=globalAlpha
        accent.alpha=globalAlpha
        marks.alpha=globalAlpha
        if(id==HudLayoutPolicy.DUKO) {
            // Camera-monitor lens: restrained monochrome optical rails,
            // four independent L-brackets and low-contrast registration marks.
            val pale=Color.rgb(215,230,244)
            marks.color=Color.argb(70,202,220,238)
            for(i in 1..12){
                val x=(w*i)/13f
                val hh=if(i%3==0)4.2f*density else 2.1f*density
                canvas.drawLine(x,1.4f*density,x,1.4f*density+hh,marks)
                canvas.drawLine(x,h-1.4f*density,x,h-1.4f*density-hh,marks)
            }
            hair.color=Color.argb(205,215,230,244)
            hair.strokeWidth=1.1f*density
            val pad=5*density;val arm=11*density
            for(x in floatArrayOf(pad,w-pad))
                for(y in floatArrayOf(pad,h-pad)){
                    val dx=if(x<w/2)arm else -arm
                    val dy=if(y<h/2)arm else -arm
                    canvas.drawLine(x,y,x+dx,y,hair)
                    canvas.drawLine(x,y,x,y+dy,hair)
                }
            marks.color=Color.argb(75,199,218,238)
            canvas.drawLine(w*.18f,h-4*density,w*.82f,h-4*density,marks)
            canvas.restoreToCount(n);return
        }
        val edgeColor=if(id==HudLayoutPolicy.TACTICAL)
            Color.rgb(118,184,211) else cyan
        glow.color=Color.argb(if(id==HudLayoutPolicy.STRIKER)48 else 25,
            Color.red(edgeColor),Color.green(edgeColor),Color.blue(edgeColor))
        canvas.drawPath(path,glow) // low-cost glow-like double stroke
        hair.color=Color.argb(if(id==HudLayoutPolicy.STRIKER)224 else 165,
            Color.red(edgeColor),Color.green(edgeColor),Color.blue(edgeColor))
        canvas.drawPath(path,hair)
        if(id==HudLayoutPolicy.STRIP) {
            // Zero wasted vertical space: a fine full-length upper shine.
            marks.color=Color.argb(110,151,219,247)
            canvas.drawLine(8*density,2*density,w-8*density,2*density,marks)
            canvas.restoreToCount(n);return
        }
        canvas.drawPath(inset,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE
            strokeWidth=.65f*density
            color=Color.argb(42,198,227,252)
        })
        accent.color=cyan
        if(id==HudLayoutPolicy.STRIKER){
            // A dark, sliced control compartment with precise cyan divider.
            // The divider is at ~75%, not over the numbers.
            cell.set(w*.745f,3*density,w-3*density,h-3*density)
            canvas.drawRect(cell,scrim)
            val x=w*.755f
            canvas.drawLine(x+8*density,4*density,
                x-8*density,h-4*density,accent)
            canvas.drawLine(11*density,3*density,36*density,3*density,accent)
            canvas.drawLine(w-41*density,h-3*density,
                w-11*density,h-3*density,accent)
            marks.color=Color.argb(110,100,201,237)
            for(i in 0..5) {
                val y=h*(i+1)/7f
                canvas.drawLine(3*density,y,6*density,y,marks)
            }
        }else{
            // Tactical pod: architectural corners and quiet horizontal
            // micro-ticks, never a rounded bubbly settings dashboard.
            accent.color=Color.rgb(92,192,231)
            canvas.drawLine(10*density,3*density,35*density,3*density,accent)
            canvas.drawLine(w-35*density,h-3*density,
                w-10*density,h-3*density,accent)
            marks.color=Color.argb(90,139,199,227)
            canvas.drawLine(w*.72f,4*density,w*.9f,4*density,marks)
        }
        canvas.restoreToCount(n)
    }
    override fun setAlpha(alpha:Int){globalAlpha=alpha.coerceIn(0,255);invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?){fill.colorFilter=colorFilter}
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
