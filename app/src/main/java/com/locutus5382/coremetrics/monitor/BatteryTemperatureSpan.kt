package com.locutus5382.coremetrics.monitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import kotlin.math.ceil

/**
 * One inline BAT T token in the existing telemetry TextView. Width contains
 * the same small inset in NORMAL/WARM/HOT, preventing warning-on width jumps.
 * Normal draws *no* outline. Warm and Hot draw a 1dp rounded stroke only;
 * no additional View, WindowManager window or background fill is allocated.
 */
internal class BatteryTemperatureSpan(
    private val state:BatteryTemperatureAdvisory.State,
    density:Float
):ReplacementSpan() {
    private val padding=(density*4f).coerceAtLeast(2f)
    private val stroke=(density*1f).coerceAtLeast(1f)
    private val radius=(density*3f).coerceAtLeast(2f)
    private val normal=Color.rgb(241,249,255)
    private val yellow=Color.rgb(234,179,8)
    private val orange=Color.rgb(249,115,22)

    private fun accent():Int=when(state) {
        BatteryTemperatureAdvisory.State.WARM -> yellow
        BatteryTemperatureAdvisory.State.HOT -> orange
        else -> normal
    }

    override fun getSize(paint:Paint,text:CharSequence,start:Int,end:Int,
        fm:Paint.FontMetricsInt?):Int =
        ceil(paint.measureText(text,start,end)+padding*2f).toInt()

    override fun draw(canvas:Canvas,text:CharSequence,start:Int,end:Int,
        x:Float,top:Int,y:Int,bottom:Int,paint:Paint) {
        val oldColor=paint.color
        val oldStyle=paint.style
        val oldWidth=paint.strokeWidth
        val color=accent()
        val textWidth=paint.measureText(text,start,end)
        paint.color=color
        paint.style=Paint.Style.FILL
        canvas.drawText(text,start,end,x+padding,y.toFloat(),paint)
        if(state==BatteryTemperatureAdvisory.State.WARM ||
            state==BatteryTemperatureAdvisory.State.HOT) {
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=stroke
            paint.color=color
            val inset=stroke/2f
            canvas.drawRoundRect(RectF(
                x+inset,top.toFloat()+inset,
                x+padding*2f+textWidth-inset,
                bottom.toFloat()-inset),radius,radius,paint)
        }
        paint.color=oldColor
        paint.style=oldStyle
        paint.strokeWidth=oldWidth
    }
}
