package com.locutus5382.coremetrics.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.locutus5382.coremetrics.monitor.HudFrameDrawable
import com.locutus5382.coremetrics.monitor.HudLayoutPolicy
import com.locutus5382.coremetrics.monitor.HudOptions
import com.locutus5382.coremetrics.monitor.HudReadoutFormatter
import com.locutus5382.coremetrics.monitor.HudTacticalCellDrawable
import com.locutus5382.coremetrics.monitor.HudTextStyler

/**
 * Preview shares the LIVE HUD's actual Canvas chrome, tactical backgrounds,
 * source-aware formatter and text spans. This is intentionally a native
 * Android View embedded only in the Settings Activity—not a second
 * Compose overlay, service, WindowManager window or hardware sampler.
 */
@Composable
internal fun HudNativePreview(
    options:HudOptions,
    readings:HudReadoutFormatter.Values
) {
    val theme=HudLayoutPolicy.normalize(options.layout)
    val multiplier=when(options.size) {
        "small"->.82f;"large"->1.18f;else->1f
    }
    val widthDp=when(theme){
        HudLayoutPolicy.STRIKER->386f
        HudLayoutPolicy.TACTICAL->307f
        HudLayoutPolicy.DUKO->236f
        HudLayoutPolicy.GHOST->520f
        HudLayoutPolicy.STRIP->565f
        else->360f
    }*multiplier
    AndroidView(
        modifier=Modifier.width(widthDp.dp),
        factory={context->LinearLayout(context)},
        update={root->
            val c=root.context
            val density=c.resources.displayMetrics.density
            fun dp(x:Float)=(x*density+.5f).toInt()
            root.removeAllViews()
            root.orientation=LinearLayout.VERTICAL
            root.gravity=Gravity.CENTER_VERTICAL
            root.background=HudFrameDrawable(theme,options.opacity,density)
            root.setPadding(dp(8f),dp(7f),dp(8f),dp(7f))
            val model=HudReadoutFormatter.render(theme,readings,true)
            val band=HudLayoutPolicy.advisoryBand(43.0)
            fun label(text:String,sp:Float=12f,plain:Boolean=false):TextView {
                val result=TextView(c).apply {
                    includeFontPadding=false
                    setTextColor(Color.rgb(238,247,255))
                    textSize=sp*multiplier
                    typeface=when {
                        theme==HudLayoutPolicy.DUKO ||
                            theme==HudLayoutPolicy.STRIP->Typeface.MONOSPACE
                        theme==HudLayoutPolicy.STRIKER->
                            Typeface.create("sans-serif-condensed",
                                Typeface.NORMAL)
                        else->Typeface.create("sans-serif-medium",
                            Typeface.NORMAL)
                    }
                    setPadding(dp(4f),dp(3f),dp(4f),dp(3f))
                    isClickable=false
                    if(theme==HudLayoutPolicy.GHOST)
                        setShadowLayer(dp(2.5f).toFloat(),0f,
                            dp(1f).toFloat(),Color.BLACK)
                }
                result.text=if(plain)text else
                    HudTextStyler.style(theme,text,readings.cpuTemp,band)
                return result
            }
            fun controls(vertical:Boolean=false):LinearLayout {
                val row=LinearLayout(c).apply {
                    orientation=if(vertical)
                        LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity=Gravity.CENTER_VERTICAL or Gravity.END
                }
                val rec=label("● STOP 25:00",10.4f,true).apply {
                    setTextColor(Color.rgb(255,116,134))
                    typeface=Typeface.DEFAULT_BOLD
                }
                val plate=GradientDrawable().apply {
                    cornerRadius=dp(5f).toFloat()
                    setColor(Color.argb(190,6,19,37))
                    setStroke(dp(.8f).coerceAtLeast(1),
                        Color.argb(209,243,85,105))
                }
                rec.background=plate
                row.addView(rec)
                if(vertical){
                    row.addView(label("↔ DRAG  [LOCK]",10f,true).apply {
                        setTextColor(Color.rgb(180,217,240))
                    })
                }else{
                    row.addView(label("[LOCK]",10f,true).apply{
                        setTextColor(Color.rgb(194,227,240))
                    })
                }
                return row
            }
            when(theme) {
                HudLayoutPolicy.STRIKER->{
                    val row=LinearLayout(c).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    row.addView(label(model.primary,12.5f),
                        LinearLayout.LayoutParams(0,-2,1f))
                    row.addView(label(model.secondary,12.5f),
                        LinearLayout.LayoutParams(0,-2,1f))
                    row.addView(controls(true),
                        LinearLayout.LayoutParams(dp(105f),-2))
                    root.addView(row)
                }
                HudLayoutPolicy.TACTICAL->{
                    root.addView(label("COREMETRICS / LIVE TELEMETRY",9.3f,true)
                        .apply{setTextColor(Color.rgb(101,214,238))})
                    for((index,text) in listOf(model.primary,model.secondary,
                        model.tertiary).withIndex()){
                        val lane=label(text,12f).apply {
                            background=HudTacticalCellDrawable(index,density)
                            setPadding(dp(11f),dp(5f),dp(6f),dp(5f))
                        }
                        root.addView(lane,LinearLayout.LayoutParams(-1,-2).apply {
                            bottomMargin=dp(3f)
                        })
                    }
                    root.addView(controls(),
                        LinearLayout.LayoutParams(-1,-2))
                }
                HudLayoutPolicy.DUKO->{
                    root.addView(label(model.primary,12.8f))
                    val line=View(c).apply {
                        setBackgroundColor(Color.argb(90,200,220,236))
                    }
                    root.addView(line,LinearLayout.LayoutParams(-1,dp(.6f))
                        .apply { topMargin=dp(3f);bottomMargin=dp(3f) })
                    val bottom=LinearLayout(c).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    bottom.addView(label("● REC 25:00",11f,true)
                        .apply{setTextColor(Color.rgb(255,96,117))},
                        LinearLayout.LayoutParams(0,-2,1f))
                    bottom.addView(label("[ LOCK ]",10.5f,true))
                    root.addView(bottom)
                }
                else->{
                    val horizontal=LinearLayout(c).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    horizontal.addView(label(model.primary,if(theme==
                        HudLayoutPolicy.GHOST)12.5f else 10.8f),
                        LinearLayout.LayoutParams(0,-2,1f))
                    horizontal.addView(controls(),
                        LinearLayout.LayoutParams(-2,-2))
                    root.addView(horizontal)
                }
            }
            root.contentDescription="Illustrative preview of "+
                HudLayoutPolicy.label(theme)+
                ". Live HUD metrics come from recorder samples."
        }
    )
}
