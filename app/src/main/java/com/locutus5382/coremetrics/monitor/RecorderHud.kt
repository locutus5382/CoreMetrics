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
import android.widget.FrameLayout
import com.locutus5382.coremetrics.telemetry.Snapshot
import com.locutus5382.coremetrics.telemetry.ThermalTrendFilter
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

/**
 * Single WindowManager overlay. Its four 30-point graphs are fed only by the
 * existing :monitor DirectSampler snapshots; the HUD never polls hardware.
 * The dock is 12dp visually, inside one 44dp touch target.
 */
internal class RecorderHud(private val context:Context) {
    private val wm=context.getSystemService(WindowManager::class.java)
    private val ui=Handler(Looper.getMainLooper())
    private val graphs=LinkedHashMap<String,GraphView>()
    private val graphCells=LinkedHashMap<String,View>()
    private var root:LinearLayout?=null
    private var pill:LinearLayout?=null
    private var drawer:LinearLayout?=null
    private var graphsRow:LinearLayout?=null
    private var dockTab:LinearLayout?=null
    private var dockVisual:View?=null
    private var mainText:TextView?=null
    private var recText:TextView?=null
    private var recDot:TextView?=null
    private var recControl:LinearLayout?=null
    private var lockControl:TextView?=null
    private var drawerControl:TextView?=null
    private var closeControl:TextView?=null
    private var params:WindowManager.LayoutParams?=null
    private var opts=HudOptions()
    private var latest:Snapshot?=null
    private val thermal=ThermalTrendFilter()
    private var thermalValue:ThermalTrendFilter.Reading?=null
    private var recording=false
    private var startedAtMs=0L
    private var blinkOn=true
    private var locked=false
    private var collapsed=false
    private var dockRight=true
    private var drawerOpen=false
    private var expandedX=0
    private var onRecord:(()->Unit)?=null
    private var onLock:(()->Unit)?=null
    private var onClose:(()->Unit)?=null
    private var onOptionsChanged:((HudOptions)->Unit)?=null
    private var disposed=false

    private val blink=object:Runnable {
        override fun run() {
            if(disposed || !recording || !attached())return
            blinkOn=!blinkOn
            renderAction()
            ui.postDelayed(this,650L)
        }
    }
    private fun dp(v:Float)=(v*context.resources.displayMetrics.density).roundToInt()
    private fun windowWidth()=wm.currentWindowMetrics.bounds.width()
    private fun windowHeight()=wm.currentWindowMetrics.bounds.height()
    private fun accent():Int=when(opts.indicatorColor) {
        "orange" -> Color.rgb(255,107,0)
        "cyan" -> Color.rgb(56,189,248)
        "violet" -> Color.rgb(190,139,250)
        else -> Color.rgb(255,68,76)
    }
    private fun rounded(alpha:Float=opts.opacity):GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
            Color.argb((255*alpha).roundToInt(),7,17,40),
            Color.argb((220*alpha).roundToInt(),26,56,107))).apply {
            cornerRadius=dp(23f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1),Color.argb(150,210,234,255))
        }

    fun permissionGranted()=Settings.canDrawOverlays(context)
    fun attached()=root?.isAttachedToWindow==true
    fun isLocked()=locked

    fun setLocked(enabled:Boolean):String {
        val frame=root ?: return "HUD_NOT_ATTACHED"
        val p=params ?: return "HUD_NO_PARAMS"
        val flags=p.flags
        val alpha=p.alpha
        p.flags=if(enabled) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        // Android 12+ requires total obscuring opacity <= 0.8.
        p.alpha=if(enabled) .70f else 1f
        return try {
            wm.updateViewLayout(frame,p)
            locked=enabled
            applyAppearance()
            if(enabled)"HUD_LOCKED" else "HUD_UNLOCKED"
        }catch(e:Exception){
            p.flags=flags;p.alpha=alpha
            runCatching{wm.updateViewLayout(frame,p)}
            "HUD_LOCK_FAILED_"+e.javaClass.simpleName
        }
    }

    private fun styledLabel(value:String,size:Float=12f):TextView=TextView(context).apply {
        text=value
        textSize=size
        includeFontPadding=false
        setTextColor(Color.rgb(241,249,255))
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        gravity=Gravity.CENTER_VERTICAL
    }
    private fun action(label:String,handler:()->Unit):TextView =
        styledLabel(label,13f).apply {
            isClickable=true
            isFocusable=false
            gravity=Gravity.CENTER
            contentDescription=label
            setPadding(dp(8f),dp(7f),dp(8f),dp(7f))
            background=rounded(.35f)
            setOnClickListener{handler()}
        }

    private fun newDrawer():LinearLayout {
        val column=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9f),dp(8f),dp(9f),dp(8f))
        }
        val row1=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val toggles=listOf(
            "CPU T" to { opts.copy(showCpuTemp=!opts.showCpuTemp) },
            "GPU T" to { opts.copy(showGpuTemp=!opts.showGpuTemp) },
            "CPU %" to { opts.copy(showCpuLoad=!opts.showCpuLoad) },
            "GPU %" to { opts.copy(showGpuLoad=!opts.showGpuLoad) },
            "FPS" to { opts.copy(showFps=!opts.showFps) },
            "W" to { opts.copy(showPower=!opts.showPower) },
            "RAM" to { opts.copy(showRam=!opts.showRam) }
        )
        for((label,make) in toggles) {
            val b=action(label){changeOptions(make())}
            row1.addView(b,LinearLayout.LayoutParams(0,dp(37f),1f))
        }
        column.addView(row1)
        val row2=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        row2.addView(action("− Glass"){changeOptions(
            opts.copy(opacity=(opts.opacity-.1f).coerceAtLeast(.30f)))})
        row2.addView(styledLabel("  GLASS  ",10f))
        row2.addView(action("+ Glass"){changeOptions(
            opts.copy(opacity=(opts.opacity+.1f).coerceAtMost(.90f)))})
        row2.addView(action("◉ Graphs"){changeOptions(
            opts.copy(showSparkline=!opts.showSparkline))})
        row2.addView(action("● REC / STOP"){onRecord?.invoke()})
        column.addView(row2)
        column.addView(styledLabel(
            "Quick controls · graphs use recorder samples only · FPS needs a verified provider",
            10f))
        return column
    }

    private fun graphCell(name:String,color:Int):View {
        val container=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(3f),0,dp(4f),0)
        }
        container.addView(styledLabel(name,9f))
        val graph=GraphView(context,color)
        graphs[name]=graph
        container.addView(graph,LinearLayout.LayoutParams(dp(64f),dp(26f)))
        graphCells[name]=container
        return container
    }

    fun show(options:HudOptions,onRecordClick:()->Unit,onLockClick:()->Unit,
        onDismissClick:()->Unit,onOptionsChanged:(HudOptions)->Unit = {}):String {
        if(!permissionGranted())return "HUD_PERMISSION_MISSING"
        opts=options
        onRecord=onRecordClick
        onLock=onLockClick
        onClose=onDismissClick
        this.onOptionsChanged=onOptionsChanged
        if(attached()){applyAppearance();return "HUD_ALREADY_ATTACHED"}
        disposed=false
        locked=false;collapsed=false;drawerOpen=false
        graphs.clear();graphCells.clear()
        val line=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val label=styledLabel("",12f).apply {
            setPadding(dp(7f),dp(4f),dp(8f),dp(4f))
        }
        mainText=label
        line.addView(label,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        val rec=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            isClickable=true
            setOnClickListener{onRecord?.invoke()}
            background=rounded(.43f)
            setPadding(dp(8f),dp(5f),dp(8f),dp(5f))
        }
        recDot=styledLabel("●",12f)
        recText=styledLabel("REC",11f)
        rec.addView(recDot);rec.addView(recText)
        recControl=rec
        line.addView(rec)
        drawerControl=action("⚙"){drawerOpen=!drawerOpen;applyAppearance()}
        line.addView(drawerControl)
        lockControl=action("🔓"){onLock?.invoke()}
        line.addView(lockControl)
        closeControl=action("×"){onClose?.invoke()}
        line.addView(closeControl)
        pill=line

        val strip=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(8f),dp(2f),dp(6f),dp(7f))
        }
        strip.addView(graphCell("CPU",Color.rgb(56,189,248)))
        strip.addView(graphCell("GPU",Color.rgb(96,165,250)))
        strip.addView(graphCell("TEMP",Color.rgb(255,107,0)))
        strip.addView(graphCell("FPS",Color.rgb(255,199,0)))
        graphsRow=strip

        val tab=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            isClickable=true
            minimumWidth=dp(44f)
            minimumHeight=dp(88f)
            contentDescription="Expand docked CoreMetrics HUD"
            setOnClickListener{expand()}
        }
        val glow=View(context).apply {
            background=GradientDrawable().apply {
                cornerRadius=dp(8f).toFloat()
                setColor(Color.rgb(56,189,248))
            }
        }
        tab.addView(glow,LinearLayout.LayoutParams(dp(12f),dp(55f)))
        dockVisual=glow
        dockTab=tab

        val frame=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            addView(line)
            addView(strip)
            addView(newDrawer().also{drawer=it})
            addView(tab)
        }
        root=frame
        val p=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START
            x=(windowWidth()-dp(380f)).coerceAtLeast(0)
            y=dp(43f)
            alpha=1f
        }
        params=p
        var downX=0f;var downY=0f;var baseX=0;var baseY=0
        var moved=false
        label.setOnTouchListener { _,e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX=e.rawX;downY=e.rawY
                    baseX=p.x;baseY=p.y
                    moved=false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX=e.rawX-downX
                    val deltaY=e.rawY-downY
                    if(kotlin.math.abs(deltaX)>dp(5f) ||
                        kotlin.math.abs(deltaY)>dp(5f))moved=true
                    p.x=(baseX+deltaX.roundToInt())
                        .coerceIn(0,(windowWidth()-frame.width).coerceAtLeast(0))
                    p.y=(baseY+deltaY.roundToInt())
                        .coerceIn(0,(windowHeight()-frame.height).coerceAtLeast(0))
                    runCatching{wm.updateViewLayout(frame,p)}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if(moved) {
                        val closeLeft=p.x<=dp(16f)
                        val closeRight=p.x+frame.width>=windowWidth()-dp(16f)
                        if(closeLeft || closeRight)dock(closeRight)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        return try {
            wm.addView(frame,p)
            applyAppearance()
            "HUD_ATTACHED"
        }catch(e:Exception){
            root=null;params=null;pill=null;drawer=null;graphsRow=null
            "HUD_ADD_FAILED_"+e.javaClass.simpleName
        }
    }

    private fun dock(toRight:Boolean) {
        if(locked || !attached())return
        val frame=root ?:return
        val p=params ?:return
        expandedX=p.x
        dockRight=toRight
        collapsed=true
        drawerOpen=false
        applyAppearance()
        p.x=if(toRight) (windowWidth()-dp(44f)).coerceAtLeast(0) else 0
        runCatching{wm.updateViewLayout(frame,p)}
    }
    private fun expand() {
        if(locked || !collapsed)return
        val frame=root ?:return
        val p=params ?:return
        collapsed=false
        applyAppearance()
        val width=frame.measuredWidth.takeIf{it>0}?:dp(390f)
        p.x=if(dockRight) (windowWidth()-width-dp(12f)).coerceAtLeast(0)
            else dp(12f)
        runCatching{wm.updateViewLayout(frame,p)}
    }

    private fun changeOptions(updated:HudOptions) {
        opts=updated
        updated.save(context)
        onOptionsChanged?.invoke(updated)
        applyAppearance()
    }
    fun configure(options:HudOptions) {opts=options;applyAppearance()}

    private fun applyAppearance() {
        val frame=root ?:return
        frame.background=if(collapsed) null else rounded(
            opts.opacity*(if(locked) .90f else 1f))
        frame.setPadding(if(collapsed)0 else dp(6f),
            if(collapsed)0 else dp(4f),
            if(collapsed)0 else dp(6f),
            if(collapsed)0 else dp(4f))
        pill?.visibility=if(collapsed)View.GONE else View.VISIBLE
        dockTab?.visibility=if(collapsed)View.VISIBLE else View.GONE
        drawer?.visibility=if(!collapsed && drawerOpen)View.VISIBLE else View.GONE
        graphsRow?.visibility=if(!collapsed && recording && opts.layout=="pro" &&
            opts.showSparkline)View.VISIBLE else View.GONE
        graphCells["CPU"]?.visibility=if(opts.showCpuLoad)View.VISIBLE else View.GONE
        graphCells["GPU"]?.visibility=if(opts.showGpuLoad)View.VISIBLE else View.GONE
        graphCells["TEMP"]?.visibility=if(opts.showCpuTemp)View.VISIBLE else View.GONE
        graphCells["FPS"]?.visibility=if(opts.showFps && latest?.surfaceFps!=null)
            View.VISIBLE else View.GONE
        val scale=when(opts.size) {"small"->.82f;"large"->1.20f;else->1f}
        mainText?.textSize=12f*scale
        mainText?.maxWidth=dp(if(opts.layout=="pro")370f*scale else 300f*scale)
        recText?.textSize=11f*scale
        lockControl?.text=if(locked)"🔒" else "🔓"
        closeControl?.visibility=if(recording)View.GONE else View.VISIBLE
        val color=when(thermalValue?.direction) {
            ThermalTrendFilter.Direction.RISING -> Color.rgb(255,107,0)
            ThermalTrendFilter.Direction.FALLING -> Color.rgb(56,189,248)
            else -> Color.rgb(255,199,0)
        }
        dockVisual?.background=GradientDrawable().apply {
            cornerRadius=dp(8f).toFloat()
            setColor(color)
        }
        renderTelemetry()
        renderAction()
        if(attached())runCatching{params?.let{wm.updateViewLayout(frame,it)}}
    }

    fun mode(isRecording:Boolean,startedMs:Long=0L) {
        recording=isRecording
        startedAtMs=startedMs
        if(!isRecording) {
            latest=null;thermal.reset();thermalValue=null
            graphs.values.forEach{it.clear()}
        }
        ui.removeCallbacks(blink)
        blinkOn=true
        applyAppearance()
        if(isRecording && attached())ui.postDelayed(blink,650L)
    }

    fun update(s:Snapshot,count:Long) {
        if(!recording || !attached())return
        latest=s
        thermalValue=thermal.update(s.timeMs,s.cpuTempC)
        graphs["CPU"]?.add(s.cpuPercent?.toFloat())
        graphs["GPU"]?.add(s.gpuPercent?.toFloat())
        graphs["TEMP"]?.add(thermalValue?.smoothedC?.toFloat())
        graphs["FPS"]?.add(s.surfaceFps?.toFloat())
        applyAppearance()
    }

    private fun renderTelemetry() {
        val view=mainText ?:return
        if(!recording) {
            view.text="COREMETRICS · READY\nNo sensor sampling until REC"
            return
        }
        val s=latest
        fun fmt(n:Double?,unit:String,decimal:Int=1):String =
            n?.takeIf{it.isFinite()}?.let{
                String.format(Locale.US,"%."+decimal+"f%s",it,unit)
            } ?: "—"
        val items=mutableListOf<String>()
        if(opts.showCpuTemp) {
            val marker=when(thermalValue?.direction) {
                ThermalTrendFilter.Direction.RISING -> " ▲"
                ThermalTrendFilter.Direction.FALLING -> " ▼"
                ThermalTrendFilter.Direction.STABLE -> " —"
                else -> ""
            }
            items+="CPU "+fmt(thermalValue?.smoothedC,"°C")+marker
            if(opts.layout=="pro" && thermalValue?.rawPeakC!=null)
                items+="Peak "+fmt(thermalValue?.rawPeakC,"°C")
        }
        if(opts.showGpuTemp)items+="GPU "+fmt(s?.gpuTempC,"°C")
        if(opts.showCpuLoad)items+="CPU "+(s?.cpuPercent?.let{"$it%"}?:"—")
        if(opts.showGpuLoad)items+="GPU "+(s?.gpuPercent?.let{"$it%"}?:"—")
        if(opts.showRam)items+="RAM "+fmt(s?.ramGiB," GiB")
        if(opts.showPower)items+="BAT "+fmt(s?.signedBatteryWatts," W")
        if(opts.showFps)items+="FPS "+fmt(s?.surfaceFps,"",0)
        view.text=if(items.isEmpty())"REC · telemetry"
            else if(opts.layout=="pro")items.chunked(3).joinToString("\n"){
                it.joinToString(" · ")
            } else items.joinToString(" · ")
    }

    private fun renderAction() {
        recDot?.text=if(recording && opts.blink && !blinkOn)"○ " else "● "
        recDot?.setTextColor(accent())
        val elapsed=((System.currentTimeMillis()-startedAtMs)/1000).coerceAtLeast(0)
        recText?.text=if(recording)
            String.format(Locale.US,"STOP %02d:%02d",elapsed/60,elapsed%60)
            else "REC"
    }

    fun hide():String {
        disposed=true
        ui.removeCallbacks(blink)
        val frame=root ?:return "HUD_NOT_ATTACHED"
        return try {
            wm.removeViewImmediate(frame)
            root=null;pill=null;drawer=null;graphsRow=null;dockTab=null
            params=null;mainText=null;recText=null;recDot=null
            graphs.clear();graphCells.clear()
            recording=false;locked=false;collapsed=false
            "HUD_REMOVED"
        }catch(e:Exception){
            if(!frame.isAttachedToWindow) {
                root=null;params=null;graphs.clear();graphCells.clear()
                "HUD_REMOVED_AFTER_EXCEPTION"
            }else "HUD_REMOVE_FAILED_"+e.javaClass.simpleName
        }
    }

    /** Each graph tracks 30 recorded samples (including null gaps). */
    private class GraphView(context:Context,color:Int):View(context) {
        private val history=ArrayDeque<Float?>()
        private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color;strokeWidth=resources.displayMetrics.density*1.65f
            style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND
        }
        fun clear(){history.clear();invalidate()}
        fun add(value:Float?) {
            history.addLast(value?.takeIf{it.isFinite()})
            while(history.size>30)history.removeFirst()
            invalidate()
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            if(history.size<2)return
            val data=history.toList()
            val valid=data.filterNotNull()
            if(valid.size<2)return
            val min=valid.minOrNull()?:return
            val max=valid.maxOrNull()?:return
            val range=(max-min).coerceAtLeast(4f)
            val base=min-range*.15f
            val heightRange=range*1.3f
            val path=Path()
            var wasValid=false
            data.forEachIndexed { index,item ->
                if(item==null){wasValid=false;return@forEachIndexed}
                val x=index*width.toFloat()/(data.size-1)
                val y=(height*(1f-(item-base)/heightRange)).coerceIn(0f,height.toFloat())
                if(!wasValid)path.moveTo(x,y) else path.lineTo(x,y)
                wasValid=true
            }
            canvas.drawPath(path,pen)
        }
    }
}
