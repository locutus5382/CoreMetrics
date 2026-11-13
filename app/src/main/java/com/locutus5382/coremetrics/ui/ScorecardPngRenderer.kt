package com.locutus5382.coremetrics.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.locutus5382.coremetrics.analysis.ScorecardModel
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Composes one high-resolution, self-contained, OFFLINE PNG.
 * Called from Dispatchers.IO after a user explicitly chose a document URI.
 * No view screenshot, private overlay capture, main-thread bitmap drawing,
 * file cache, network request, animation or hardware sensor access.
 *
 * An offscreen ARGB_8888 bitmap is about 6.7 MB. Always recycle it in finally.
 */
internal object ScorecardPngRenderer {
    const val WIDTH=1600
    const val HEIGHT=1050

    private const val WHITE=0xFFF1F8FF.toInt()
    private const val MUTED=0xFFB8D1E6.toInt()
    private const val DARK=0xFF06152B.toInt()
    private const val CYAN=0xFF5FD9F8.toInt()
    private const val COBALT=0xFF387CF5.toInt()
    private const val ORANGE=0xFFFF8B31.toInt()
    private const val GOLD=0xFFFFC700.toInt()

    fun write(card:ScorecardModel.Card,style:SurfaceFinish,output:OutputStream) {
        val bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap)
            render(canvas,card,style)
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) {
                "Android PNG encoder failed"
            }
            output.flush()
        } finally {
            bitmap.recycle()
        }
    }

    /** The writer deliberately paints only provided measurements and caveats. */
    private fun render(canvas:Canvas,card:ScorecardModel.Card,style:SurfaceFinish) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color:Int){paint.reset();paint.isAntiAlias=true;paint.color=color
            paint.style=Paint.Style.FILL}
        fun rect(l:Float,t:Float,r:Float,b:Float,c:Int,radius:Float=0f) {
            fill(c)
            if(radius>0f)canvas.drawRoundRect(RectF(l,t,r,b),radius,radius,paint)
            else canvas.drawRect(l,t,r,b,paint)
        }
        fun stroke(l:Float,t:Float,r:Float,b:Float,c:Int,radius:Float=16f) {
            fill(c);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
            canvas.drawRoundRect(RectF(l,t,r,b),radius,radius,paint)
            paint.style=Paint.Style.FILL
        }
        fun label(value:String,x:Float,y:Float,size:Float=25f,
            color:Int=WHITE,bold:Boolean=false,maxWidth:Float=1420f,
            mono:Boolean=false) {
            fill(color)
            paint.textSize=size
            paint.typeface=when {
                mono->Typeface.create(Typeface.MONOSPACE,if(bold)Typeface.BOLD else Typeface.NORMAL)
                bold->Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                else->Typeface.create(Typeface.DEFAULT,Typeface.NORMAL)
            }
            var text=value.replace("\n"," ")
            if(paint.measureText(text)>maxWidth) {
                while(text.length>3 && paint.measureText(text+"…")>maxWidth)
                    text=text.dropLast(1)
                text+="…"
            }
            canvas.drawText(text,x,y,paint)
        }
        val matte=style==SurfaceFinish.COBALT_MATRIX
        val round=if(matte)8f else 21f
        val base=when(style) {
            SurfaceFinish.CRYSTAL_AERO -> intArrayOf(0xFF07122E.toInt(),
                0xFF16477C.toInt(),0xFF040E25.toInt())
            SurfaceFinish.SOLAR_PULSE -> intArrayOf(0xFF09162D.toInt(),
                0xFF1B3158.toInt(),0xFF09142A.toInt())
            SurfaceFinish.COBALT_MATRIX -> intArrayOf(0xFF060E1B.toInt(),
                0xFF0B1A2D.toInt(),0xFF070F1E.toInt())
            else -> intArrayOf(0xFF0A1732.toInt(),
                0xFF1A3B70.toInt(),0xFF08132D.toInt())
        }
        fill(base[0])
        paint.shader=LinearGradient(0f,0f,WIDTH.toFloat(),HEIGHT.toFloat(),
            base,null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,WIDTH.toFloat(),HEIGHT.toFloat(),paint)
        paint.shader=null
        // Static glass/cobalt identity. No animated distortion or blur shader.
        if(style==SurfaceFinish.CRYSTAL_AERO) {
            val band=Path().apply {
                moveTo(740f,0f);lineTo(1170f,0f)
                lineTo(540f,980f);lineTo(260f,1050f);close()
            }
            fill(0x262AD4FF)
            canvas.drawPath(band,paint)
        }
        if(matte) {
            fill(0x214FA4E4)
            for(x in 0..WIDTH step 80)
                canvas.drawLine(x.toFloat(),0f,x.toFloat(),HEIGHT.toFloat(),paint)
            for(y in 0..HEIGHT step 80)
                canvas.drawLine(0f,y.toFloat(),WIDTH.toFloat(),y.toFloat(),paint)
        }
        val primary=when(style) {
            SurfaceFinish.SOLAR_PULSE -> ORANGE
            SurfaceFinish.COBALT_MATRIX -> CYAN
            else -> CYAN
        }
        rect(0f,0f,WIDTH.toFloat(),8f,primary)
        label("CM  /  COREMETRICS",55f,66f,38f,primary,true,900f,matte)
        label("OFFLINE DIAGNOSTIC CARD",55f,108f,23f,MUTED,false,780f,matte)
        label(card.title,55f,166f,35f,WHITE,true,1110f,matte)
        val badgeColor=if(card.badgesAreCaution)ORANGE else CYAN
        rect(1130f,112f,1548f,172f,0xE21A344E.toInt(),16f)
        stroke(1130f,112f,1548f,172f,badgeColor,16f)
        label(card.badge,1152f,152f,22f,badgeColor,true,373f,matte)
        label(card.subtitle,56f,206f,27f,MUTED,true,1450f)
        label(card.range,56f,247f,26f,GOLD,false,1450f,matte)

        // Shared row structure means single and A/B cards remain comparable,
        // including missing-value labeling and the fixed B-A sign convention.
        val tableTop=280f
        val tableBottom=662f
        rect(44f,tableTop,1556f,tableBottom,
            when(style) {
                SurfaceFinish.CRYSTAL_AERO -> 0xA00A2044.toInt()
                SurfaceFinish.COBALT_MATRIX -> 0xEF101B2B.toInt()
                else -> 0xDB091B36.toInt()
            },round)
        stroke(44f,tableTop,1556f,tableBottom,
            if(style==SurfaceFinish.CRYSTAL_AERO)
                0xBFA8EDFF.toInt() else 0x79408FC0,round)
        val ab=card.type==ScorecardModel.Type.AB
        label(if(ab)"MEASUREMENT" else "MEASUREMENT",72f,315f,
            21f,MUTED,true,630f,matte)
        if(ab) {
            label("A",872f,315f,22f,MUTED,true,130f,matte)
            label("B",1130f,315f,22f,MUTED,true,130f,matte)
            label("B - A",1360f,315f,22f,GOLD,true,157f,matte)
        } else label("SELECTED WINDOW",904f,315f,21f,MUTED,true,520f,matte)
        rect(71f,326f,1529f,328f,0x5C87BEE7)
        card.rows.take(7).forEachIndexed { i,row ->
            val y=371f+i*41f
            if(i%2==1)rect(60f,y-31f,1540f,y+9f,0x200C6EAC)
            label(row.label,75f,y,23f,WHITE,false,if(ab)760f else 805f,matte)
            if(ab) {
                label(row.left,868f,y,24f,WHITE,true,220f,true)
                label(row.right?:"Unavailable",1126f,y,24f,WHITE,true,220f,true)
                label(row.delta?:"Unavailable",1350f,y,24f,
                    if(row.delta?.startsWith("+")==true)ORANGE else CYAN,
                    true,178f,true)
            } else label(row.left,904f,y,25f,WHITE,true,590f,true)
        }
        rect(49f,694f,1551f,882f,0xD4081B32.toInt(),round)
        stroke(49f,694f,1551f,882f,0x6B86C4EF,round)
        label(if(ab)"CPU TEMPERATURE  /  A VS B" else "RECORDED SENSOR TEMPERATURES",
            76f,731f,21f,MUTED,true,900f,matte)
        card.series.take(2).forEachIndexed { i,series ->
            val color=if(i==0)ORANGE else CYAN
            rect(1030f+i*275f,713f,1048f+i*275f,731f,color,4f)
            label(series.label,1056f+i*275f,729f,19f,WHITE,false,249f,matte)
        }
        val left=82f;val top=748f;val right=1511f;val bottom=840f
        for(i in 0..3) {
            val y=top+(bottom-top)*i/3f
            rect(left,y,right,y+1f,0x4D6590C7)
        }
        val valid=card.series.flatMap { s ->
            s.samples.mapNotNull { it.value?.takeIf(Double::isFinite) }
        }
        val lo=(valid.minOrNull()?:20.0)-3.0
        val hi=(valid.maxOrNull()?:70.0)+3.0
        val scale=max(5.0,hi-lo)
        val t0=card.graphStartMs
        val period=max(1L,card.graphEndMs-t0)
        fun xAt(t:Long)=(left+(t-t0).toDouble()/period*
            (right-left)).toFloat()
        fun yAt(t:Double)=(bottom-(t-lo)/scale*(bottom-top)).toFloat()
        canvas.save()
        canvas.clipRect(left,top,right,bottom)
        card.series.take(2).forEachIndexed { i,series ->
            val color=if(i==0)ORANGE else CYAN
            val path=Path()
            var lastTime:Long?=null
            for(p in series.samples) {
                val v=p.value
                if(v==null || !v.isFinite() || p.elapsedMs !in t0..card.graphEndMs) {
                    lastTime=null
                    continue
                }
                val x=xAt(p.elapsedMs);val y=yAt(v)
                if(lastTime==null || p.elapsedMs-lastTime!!>series.gapLimitMs)
                    path.moveTo(x,y) else path.lineTo(x,y)
                lastTime=p.elapsedMs
            }
            fill(color);paint.strokeWidth=3.5f;paint.style=Paint.Style.STROKE
            canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        }
        canvas.restore()
        label(formatTime(t0),82f,868f,18f,MUTED,false,220f,true)
        label(formatTime(card.graphEndMs),1424f,868f,18f,MUTED,false,120f,true)

        // Keep limitations legible in every shared PNG; do not strip caveats.
        label("MEASUREMENT NOTES  /  PROVENANCE",60f,926f,22f,GOLD,true,1400f,matte)
        card.footnotes.take(4).forEachIndexed{ i,note ->
            label("• "+note,64f,951f+i*23f,18f,MUTED,false,1470f,matte)
        }
        label(card.sourceDescription,63f,1044f,16f,
            0xFF9DC3E8.toInt(),false,1400f,matte)
    }
    private fun formatTime(ms:Long):String {
        val seconds=max(0L,ms/1000L)
        return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
    }
}
