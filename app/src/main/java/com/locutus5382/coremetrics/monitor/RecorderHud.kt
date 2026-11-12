package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.locutus5382.coremetrics.telemetry.Snapshot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One small overlay for BOTH ARMED (no telemetry, no SQLite writes) and REC.
 *
 * On recording we merely display the existing DirectSampler output, avoiding
 * a second polling loop. The only standby callback is a recording-button click.
 * Foreground service and explicit permission are handled by RecordingService.
 */
internal data class HudOptions(
    val opacity: Float = 0.57f,
    val size: String = "regular",
    val layout: String = "pill",
    val indicatorColor: String = "red",
    val blink: Boolean = true,
    val showCpuTemp: Boolean = true,
    val showGpuTemp: Boolean = true,
    val showCpuLoad: Boolean = false,
    val showGpuLoad: Boolean = false,
    val showRam: Boolean = true,
    val showPower: Boolean = true,
    val showFps: Boolean = false,
    val showSparkline: Boolean = true
) {
    companion object {
        const val OPACITY = "hud_opacity"
        const val SIZE = "hud_size"
        const val LAYOUT = "hud_layout"
        const val INDICATOR = "hud_indicator"
        const val BLINK = "hud_blink"
        private val fields = listOf(
            "cpuTemp", "gpuTemp", "cpuLoad", "gpuLoad", "ram", "power", "fps", "sparkline")
        fun from(context: Context, intent: Intent? = null): HudOptions {
            val p = context.getSharedPreferences("coremetrics_settings", Context.MODE_PRIVATE)
            fun f(k: String, fallback: Float): Float = if(intent?.hasExtra(k)==true)
                intent.getFloatExtra(k,fallback) else p.getFloat(k,fallback)
            fun s(k: String, fallback: String): String = if(intent?.hasExtra(k)==true)
                intent.getStringExtra(k) ?: fallback else p.getString(k,fallback) ?: fallback
            fun b(k: String, fallback: Boolean): Boolean = if(intent?.hasExtra(k)==true)
                intent.getBooleanExtra(k,fallback) else p.getBoolean(k,fallback)
            return HudOptions(
                opacity=f(OPACITY,.57f).coerceIn(.30f,.90f),
                size=s(SIZE,"regular").takeIf { it in setOf("small","regular","large") } ?: "regular",
                layout=s(LAYOUT,"pill").takeIf { it in setOf("pill","pro") } ?: "pill",
                indicatorColor=s(INDICATOR,"red").takeIf {
                    it in setOf("red","orange","cyan","violet") } ?: "red",
                blink=b(BLINK,true),
                showCpuTemp=b("hud_cpuTemp",true),
                showGpuTemp=b("hud_gpuTemp",true),
                showCpuLoad=b("hud_cpuLoad",false),
                showGpuLoad=b("hud_gpuLoad",false),
                showRam=b("hud_ram",true),
                showPower=b("hud_power",true),
                showFps=b("hud_fps",false),
                showSparkline=b("hud_sparkline",true)
            )
        }
        fun addExtras(i: Intent, options: HudOptions): Intent = i.apply {
            putExtra(OPACITY,options.opacity)
            putExtra(SIZE,options.size)
            putExtra(LAYOUT,options.layout)
            putExtra(INDICATOR,options.indicatorColor)
            putExtra(BLINK,options.blink)
            for ((k,v) in listOf(
                "cpuTemp" to options.showCpuTemp,"gpuTemp" to options.showGpuTemp,
                "cpuLoad" to options.showCpuLoad,"gpuLoad" to options.showGpuLoad,
                "ram" to options.showRam,"power" to options.showPower,
                "fps" to options.showFps,"sparkline" to options.showSparkline
            )) putExtra("hud_"+k,v)
        }
    }
    fun save(context:Context):Boolean {
        val p=context.getSharedPreferences("coremetrics_settings",Context.MODE_PRIVATE)
        return p.edit().putFloat(OPACITY,opacity)
            .putString(SIZE,size).putString(LAYOUT,layout)
            .putString(INDICATOR,indicatorColor).putBoolean(BLINK,blink)
            .putBoolean("hud_cpuTemp",showCpuTemp)
            .putBoolean("hud_gpuTemp",showGpuTemp)
            .putBoolean("hud_cpuLoad",showCpuLoad)
            .putBoolean("hud_gpuLoad",showGpuLoad)
            .putBoolean("hud_ram",showRam)
            .putBoolean("hud_power",showPower)
            .putBoolean("hud_fps",showFps)
            .putBoolean("hud_sparkline",showSparkline).commit()
    }
    fun selectedCount() = listOf(showCpuTemp,showGpuTemp,showCpuLoad,
        showGpuLoad,showRam,showPower,showFps).count { it }
}

internal class RecorderHud(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val ui = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var content: TextView? = null
    private var recordButton: LinearLayout? = null
    private var recordLabel: TextView? = null
    private var recordDot: TextView? = null
    private var closeButton: TextView? = null
    private var trend: TrendView? = null
    private var params: WindowManager.LayoutParams? = null
    private var opts = HudOptions()
    private var recording = false
    private var startedAtMs = 0L
    private var latest: Snapshot? = null
    private var blinkOn = true
    private var onToggle: (() -> Unit)? = null
    private var onClose: (() -> Unit)? = null
    private var disposed = false
    private val heartbeat = object: Runnable {
        override fun run() {
            if (disposed || !recording || !attached()) return
            blinkOn = !blinkOn
            renderAction()
            ui.postDelayed(this,650L)
        }
    }
    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
    private fun accent(): Int = when(opts.indicatorColor) {
        "orange" -> Color.rgb(255,125,48)
        "cyan" -> Color.rgb(88,211,244)
        "violet" -> Color.rgb(190,139,250)
        else -> Color.rgb(255,68,76)
    }
    fun permissionGranted() = Settings.canDrawOverlays(context)
    fun attached(): Boolean = root?.isAttachedToWindow == true

    private fun rounded(
        cornerDp: Float, alpha: Float, border: Int, dark: Boolean = true
    ): GradientDrawable {
        val a = (alpha.coerceIn(0f,1f)*255).roundToInt()
        val first=if(dark) Color.argb(a,7,17,40) else Color.argb(a,60,76,100)
        val second=if(dark) Color.argb((a*.88f).roundToInt(),23,57,114)
            else Color.argb((a*.90f).roundToInt(),25,43,82)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(first,second)).apply {
            cornerRadius=dp(cornerDp).toFloat()
            setStroke(dp(1f).coerceAtLeast(1),border)
        }
    }

    private fun applyAppearance() {
        val frame = root ?: return
        val scale=when(opts.size) { "small"->.82f;"large"->1.22f;else->1f }
        frame.background=rounded(32f,opts.opacity,Color.argb(140,211,233,255))
        frame.setPadding(dp(9f*scale),dp(6f*scale),dp(8f*scale),dp(6f*scale))
        content?.textSize=12f*scale
        content?.setLineSpacing(0f,1.06f)
        recordDot?.textSize=12f*scale
        recordLabel?.textSize=11f*scale
        closeButton?.textSize=14f*scale
        recordButton?.apply {
            background=rounded(19f,opts.opacity*.86f,Color.argb(105,
                Color.red(accent()),Color.green(accent()),Color.blue(accent())),false)
            setPadding(dp(9f*scale),dp(5f*scale),dp(9f*scale),dp(5f*scale))
        }
        recordDot?.setTextColor(accent())
        trend?.visibility=if(opts.showSparkline && opts.layout=="pro" && recording)
            View.VISIBLE else View.GONE
        content?.maxWidth=dp(if(opts.layout=="pill") 325f*scale else 375f*scale)
        renderTelemetry()
        renderAction()
        runCatching { params?.let { windows.updateViewLayout(frame,it) } }
    }

    /** Display a single view; left telemetry area drags, REC button clicks. */
    fun show(options: HudOptions,onRecordClick: () -> Unit,onDismissClick: () -> Unit): String {
        if (!permissionGranted()) return "HUD_PERMISSION_MISSING"
        opts=options
        onToggle=onRecordClick
        onClose=onDismissClick
        if(attached()) { applyAppearance();return "HUD_ALREADY_ATTACHED" }
        disposed=false
        val row=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val metrics=TextView(context).apply {
            setTextColor(Color.rgb(244,248,255))
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            includeFontPadding=false
            setPadding(dp(5f),dp(3f),dp(9f),dp(3f))
        }
        content=metrics
        row.addView(metrics,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        val spark=TrendView(context)
        trend=spark
        row.addView(spark,LinearLayout.LayoutParams(dp(61f),dp(27f)))
        val ctl=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            isClickable=true
            isFocusable=false
        }
        val dot=TextView(context).apply { text="●";includeFontPadding=false }
        val label=TextView(context).apply {
            setTextColor(Color.WHITE)
            typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
            includeFontPadding=false
        }
        recordDot=dot
        recordLabel=label
        ctl.addView(dot)
        ctl.addView(label)
        ctl.setOnClickListener { onToggle?.invoke() }
        recordButton=ctl
        row.addView(ctl)
        val close=TextView(context).apply {
            text="×"
            setTextColor(Color.argb(200,218,237,255))
            setPadding(dp(5f),0,0,0)
            isClickable=true
            setOnClickListener { onClose?.invoke() }
            contentDescription="Close CoreMetrics standby HUD"
        }
        closeButton=close
        row.addView(close)
        val pane=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            addView(row)
        }
        root=pane
        val p=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.END
            x=dp(15f)
            y=dp(40f)
            alpha=1f // background transparency only: text stays legible
        }
        params=p
        var downX=0f; var downY=0f; var baseX=0; var baseY=0
        metrics.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX=event.rawX;downY=event.rawY
                    baseX=p.x;baseY=p.y;true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x=(baseX-(event.rawX-downX)).roundToInt().coerceAtLeast(0)
                    p.y=(baseY+(event.rawY-downY)).roundToInt().coerceAtLeast(0)
                    runCatching { windows.updateViewLayout(pane,p) }
                    true
                }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        return try {
            windows.addView(pane,p)
            applyAppearance()
            "HUD_ATTACHED"
        } catch(e:Exception) {
            root=null;params=null;content=null;recordButton=null
            "HUD_ADD_FAILED_"+e.javaClass.simpleName
        }
    }

    fun configure(options: HudOptions) { opts=options;applyAppearance() }

    /** The recorder controls are authoritative; we never assume a click succeeded. */
    fun mode(isRecording:Boolean,startedMs:Long=0L) {
        recording=isRecording
        startedAtMs=startedMs
        if(!isRecording) {
            latest=null
            trend?.clear()
        }
        ui.removeCallbacks(heartbeat)
        blinkOn=true
        renderTelemetry()
        renderAction()
        if(isRecording && attached()) ui.postDelayed(heartbeat,650L)
    }

    fun update(s:Snapshot,count:Long) {
        if(!recording || !attached())return
        latest=s
        trend?.add(s.cpuTempC?.toFloat())
        renderTelemetry()
    }

    private fun renderTelemetry() {
        val view=content ?: return
        if(!recording) {
            view.text="COREMETRICS  ·  READY\nNo sampling • start when game loads"
            trend?.visibility=View.GONE
            return
        }
        val s=latest
        fun format(n:Double?,unit:String,digits:Int=1):String =
            n?.let { String.format(Locale.US,"%."+digits+"f%s",it,unit) } ?: "—"
        val items=mutableListOf<String>()
        if(opts.showCpuTemp)items+="CPU "+format(s?.cpuTempC,"°C")
        if(opts.showGpuTemp)items+="GPU "+format(s?.gpuTempC,"°C")
        if(opts.showCpuLoad)items+="CPU "+(s?.cpuPercent?.let{"$it%"} ?: "—")
        if(opts.showGpuLoad)items+="GPU "+(s?.gpuPercent?.let{"$it%"} ?: "—")
        if(opts.showRam)items+="RAM "+format(s?.ramGiB,"G")
        if(opts.showPower)items+="BAT "+format(s?.signedBatteryWatts,"W")
        if(opts.showFps)items+="FPS "+format(s?.surfaceFps,"",0)
        view.text=if(items.isEmpty()) "REC · TELEMETRY" else if(opts.layout=="pro") {
            items.chunked(3).joinToString("\n") { it.joinToString("  ·  ") }
        } else items.joinToString("  ·  ")
        trend?.visibility=if(opts.showSparkline && opts.layout=="pro") View.VISIBLE else View.GONE
    }

    private fun renderAction() {
        val dot=recordDot ?: return
        val label=recordLabel ?: return
        dot.text=if(recording && opts.blink && !blinkOn) "○ " else "● "
        dot.setTextColor(accent())
        val elapsed=((System.currentTimeMillis()-startedAtMs)/1000).coerceAtLeast(0L)
        val duration=String.format(Locale.US,"%02d:%02d",elapsed/60,elapsed%60)
        label.text=if(recording) "STOP $duration" else "REC"
        closeButton?.visibility=if(recording)View.GONE else View.VISIBLE
    }

    fun hide():String {
        disposed=true
        ui.removeCallbacks(heartbeat)
        val v=root ?: return "HUD_NOT_ATTACHED"
        return try {
            windows.removeViewImmediate(v)
            root=null;content=null;recordButton=null;recordDot=null
            recordLabel=null;params=null;trend=null
            "HUD_REMOVED"
        } catch(e:Exception) {
            if(!v.isAttachedToWindow) {
                root=null;params=null;content=null;trend=null
                "HUD_REMOVED_AFTER_EXCEPTION"
            } else "HUD_REMOVE_FAILED_"+e.javaClass.simpleName
        }
    }

    /** Small fixed-size sparkline updated only from recorded temperature samples. */
    private class TrendView(context:Context):View(context) {
        private val data=ArrayDeque<Float>()
        private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(255,207,76)
            strokeWidth=resources.displayMetrics.density*1.5f
            style=Paint.Style.STROKE
            strokeCap=Paint.Cap.ROUND
        }
        fun clear() {
            data.clear()
            invalidate()
        }
        fun add(v:Float?) {
            if(v==null || !v.isFinite())return
            data.addLast(v)
            while(data.size>22)data.removeFirst()
            invalidate()
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            if(data.size<2)return
            val vals=data.toList()
            val lo=(vals.minOrNull()?:0f)-2f
            val hi=(vals.maxOrNull()?:1f)+2f
            val p=Path()
            vals.forEachIndexed { i,v ->
                val x=i*width.toFloat()/(vals.size-1)
                val y=height*(1f-(v-lo)/(hi-lo).coerceAtLeast(1f))
                if(i==0)p.moveTo(x,y)else p.lineTo(x,y)
            }
            canvas.drawPath(p,stroke)
        }
    }
}
