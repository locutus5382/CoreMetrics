package com.locutus5382.coremetrics.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    val showSparkline: Boolean = false, // legacy preference ignored; no HUD graphs
    val adaptiveColors: Boolean = false
) {
    companion object {
        const val OPACITY = "hud_opacity"
        const val SIZE = "hud_size"
        const val LAYOUT = "hud_layout"
        const val INDICATOR = "hud_indicator"
        const val BLINK = "hud_blink"
        const val ADAPTIVE = "hud_adaptive_colors"
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
                showSparkline=false,
                adaptiveColors=b(ADAPTIVE,false)
            )
        }
        fun addExtras(i: Intent, options: HudOptions): Intent = i.apply {
            putExtra(OPACITY,options.opacity)
            putExtra(SIZE,options.size)
            putExtra(LAYOUT,options.layout)
            putExtra(INDICATOR,options.indicatorColor)
            putExtra(BLINK,options.blink)
            putExtra(ADAPTIVE,options.adaptiveColors)
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
            .putBoolean("hud_sparkline",false)
            .putBoolean(ADAPTIVE,adaptiveColors).commit()
    }
    fun selectedCount() = listOf(showCpuTemp,showGpuTemp,showCpuLoad,
        showGpuLoad,showRam,showPower,showFps).count { it }
}

/**
 * Single WindowManager telemetry overlay, no parallel hardware polling.
 * The dock is 12dp visually inside one 44dp touch target. Charts live in
 * post-session analysis rather than unreadable in-game micrographs.
 */
internal class RecorderHud(private val context:Context) {
    private val wm=context.getSystemService(WindowManager::class.java)
    private val ui=Handler(Looper.getMainLooper())
    private var root:LinearLayout?=null
    private var pill:LinearLayout?=null
    private var drawer:LinearLayout?=null
    private var dockTab:LinearLayout?=null
    private var dockVisual:View?=null
    private var mainText:TextView?=null
    private var recText:TextView?=null
    private var recDot:TextView?=null
    private var recControl:LinearLayout?=null
    private var lockControl:TextView?=null
    // A separate bounded touchable overlay is mandatory: FLAG_NOT_TOUCHABLE
    // on the telemetry window also makes ALL its child icons untouchable.
    private var unlockWindow:TextView?=null
    private var unlockParams:WindowManager.LayoutParams?=null
    private var drawerControl:TextView?=null
    private val drawerSwitches=LinkedHashMap<String,TextView>()
    private var opacityLabel:TextView?=null
    private var drawerRecControl:TextView?=null
    private var closeControl:TextView?=null
    private var minimizeControl:TextView?=null
    private val metricLevels=mutableMapOf<String,HudSeverity.Level>()
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
    private var onUnlock:(()->Unit)?=null
    private var onClose:(()->Unit)?=null
    private var onOptionsChanged:((HudOptions)->Unit)?=null
    private var disposed=false

    private fun shouldBlink() =
        !disposed && recording && opts.blink && !collapsed && visible()

    private val blink=object:Runnable {
        override fun run() {
            if(!shouldBlink())return
            blinkOn=!blinkOn
            renderAction()
            ui.postDelayed(this,650L)
        }
    }
    /** Centralize scheduling to avoid duplicate callbacks across dock/expand,
     * option changes, app-initiated configurations and REC transitions. */
    private fun syncBlink() {
        ui.removeCallbacks(blink)
        blinkOn=true
        renderAction()
        if(shouldBlink())ui.postDelayed(blink,650L)
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
            cornerRadius=dp(if(opts.layout=="pro")13f else 23f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1),Color.argb(150,210,234,255))
        }

    fun permissionGranted()=Settings.canDrawOverlays(context)
    /** Record only identity/ownership, never game labels or sample data. */
    fun windowIdentity():String =
        "root="+(root?.let{System.identityHashCode(it).toString()}?:"none")+
        " attached="+attached()+
        " unlock="+(unlockWindow?.let{System.identityHashCode(it).toString()}?:"none")+
        " unlockAttached="+(unlockWindow?.isAttachedToWindow==true)

    /** Service teardown removes all callbacks even if WindowManager rejects
     * immediate detach. A future Service can safely rebind this same owner. */
    fun unbindCallbacks() {
        onRecord=null;onLock=null;onUnlock=null;onClose=null
        onOptionsChanged={}
    }

    private fun removeTracked(view:View):Boolean = try {
        wm.removeViewImmediate(view)
        true
    }catch(_:Exception){
        // Do not discard a still-attached window after a failed removal.
        !view.isAttachedToWindow && view.parent==null
    }

    fun attached()=root?.isAttachedToWindow==true
    fun visible()=attached() && root?.visibility==View.VISIBLE
    fun isLocked()=locked
    /** Hide only the UI. Keep the recorder/lease/foreground service alive. */
    fun setVisible(show:Boolean):String {
        val view=root ?:return "HUD_NOT_ATTACHED"
        view.visibility=if(show)View.VISIBLE else View.GONE
        unlockWindow?.visibility=if(show)View.VISIBLE else View.GONE
        if(show)applyAppearance()
        syncBlink()
        return if(show)"HUD_VISIBLE" else "HUD_HIDDEN"
    }

    /** Positions the independent unlock handle exactly above the original lock.
     * Only 48 dp of screen can intercept taps; the rest of the HUD is click-through.
     */
    private fun makeUnlockHandle(): Pair<TextView,WindowManager.LayoutParams> {
        val main=params ?:error("HUD_NO_PARAMS")
        val icon=lockControl
        val oldX=(main.x+(pill?.left?:0)+(icon?.left?:dp(280f)))-dp(6f)
        val oldY=main.y+(pill?.top?:0)+(icon?.top?:0)-dp(5f)
        val handle=styledLabel("",11f).apply {
            gravity=Gravity.CENTER
            background=FlatLockChipDrawable(locked=true)
            contentDescription="Unlock HUD; make the telemetry controls touchable"
            isClickable=true
            isFocusable=false
            setOnClickListener { onUnlock?.invoke() }
        }
        val p=WindowManager.LayoutParams(dp(48f),dp(48f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START
            x=oldX.coerceIn(0,(windowWidth()-dp(48f)).coerceAtLeast(0))
            y=oldY.coerceIn(0,(windowHeight()-dp(48f)).coerceAtLeast(0))
            alpha=1f
        }
        return handle to p
    }

    fun setLocked(enabled:Boolean):String {
        val frame=root ?:return "HUD_NOT_ATTACHED"
        val p=params ?:return "HUD_NO_PARAMS"
        if(locked==enabled)return if(enabled)"HUD_ALREADY_LOCKED" else "HUD_ALREADY_UNLOCKED"
        if(enabled) {
            // Add recovery control FIRST. If Android refuses another overlay,
            // do not strand the user behind an untouchable main HUD.
            val (chip,chipParams)=try { makeUnlockHandle() }
                catch(e:Exception){return "HUD_UNLOCK_HANDLE_CREATE_FAILED_"+e.javaClass.simpleName}
            if(unlockWindow!=null) {
                val existing=unlockWindow!!
                if(!removeTracked(existing))
                    return "HUD_LOCK_REFUSED_UNRESOLVED_UNLOCK_CHIP"
                unlockWindow=null;unlockParams=null
            }
            try { wm.addView(chip,chipParams) }
            catch(e:Exception){return "HUD_UNLOCK_HANDLE_ADD_FAILED_"+e.javaClass.simpleName}
            // Track before any later operation can throw.
            unlockWindow=chip;unlockParams=chipParams
            val oldFlags=p.flags
            val oldAlpha=p.alpha
            val drawerWasOpen=drawerOpen
            p.flags=p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            p.alpha=.70f
            try {
                wm.updateViewLayout(frame,p)
                unlockWindow=chip
                unlockParams=chipParams
                locked=true
                // The user must see only the compact HUD when touch passthrough
                // is active. Hide an open settings drawer without losing its
                // saved choices. The separate unlock chip stays touchable.
                drawerOpen=false
                applyAppearance()
                return "HUD_LOCKED_WITH_TOUCHABLE_UNLOCK"
            }catch(e:Exception){
                // Restore both interaction and visual state if *either*
                // WindowManager update or the appearance update fails.
                locked=false
                drawerOpen=drawerWasOpen
                if(removeTracked(chip)) {
                    unlockWindow=null
                    unlockParams=null
                }
                p.flags=oldFlags
                p.alpha=oldAlpha
                runCatching{wm.updateViewLayout(frame,p)}
                runCatching{applyAppearance()}
                return "HUD_LOCK_FAILED_"+e.javaClass.simpleName
            }
        }
        // Restore the main window before removing the recovery control.
        val oldFlags=p.flags
        val oldAlpha=p.alpha
        p.flags=p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        p.alpha=1f
        return try {
            wm.updateViewLayout(frame,p)
            locked=false
            val chipGone=unlockWindow?.let{removeTracked(it)}?:true
            if(chipGone){unlockWindow=null;unlockParams=null}
            applyAppearance()
            if(chipGone)"HUD_UNLOCKED_TOUCH_CONTROLS_RESTORED"
            else "HUD_UNLOCK_CHIP_REMOVE_FAILED_RETAINED"
        }catch(e:Exception){
            p.flags=oldFlags;p.alpha=oldAlpha
            runCatching{wm.updateViewLayout(frame,p)}
            "HUD_UNLOCK_FAILED_"+e.javaClass.simpleName
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

    /** Clear state communication: enabled = cyan frosted, disabled = muted,
     * almost borderless, REC = red. The native drawer does not poll telemetry. */
    private fun paintDrawerToggle(view:TextView,label:String,enabled:Boolean) {
        view.text=(if(enabled)"✓ " else "○ ")+label
        view.contentDescription=label+(if(enabled)" enabled" else " disabled")
        view.setTextColor(if(enabled)Color.WHITE
            else Color.argb(160,188,212,236))
        view.typeface=Typeface.create("sans-serif-medium",
            if(enabled)Typeface.BOLD else Typeface.NORMAL)
        val orange=label=="CPU T" || label=="RAM"
        view.background=GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if(enabled && orange)intArrayOf(
                Color.argb(219,255,107,0),Color.argb(195,209,65,0))
            else if(enabled)intArrayOf(
                Color.argb(216,37,99,235),Color.argb(191,25,72,192))
            else intArrayOf(
                Color.argb(39,7,17,40),Color.argb(36,7,17,40))
        ).apply {
            cornerRadius=dp(12f).toFloat()
            if(enabled)setStroke(dp(1.5f).coerceAtLeast(1),
                Color.argb(235,242,247,255))
            else setStroke(0,Color.TRANSPARENT)
        }
    }

    private fun refreshDrawerControls() {
        val selected=mapOf(
            "CPU T" to opts.showCpuTemp,"GPU T" to opts.showGpuTemp,
            "CPU %" to opts.showCpuLoad,"GPU %" to opts.showGpuLoad,
            "W" to opts.showPower,
            "RAM" to opts.showRam)
        drawerSwitches.forEach { (name,view) ->
            paintDrawerToggle(view,name,selected[name]==true)
        }
        opacityLabel?.text=String.format(Locale.US,
            " Glass %d%% ",(opts.opacity*100f).roundToInt())
        drawerRecControl?.let { button ->
            button.text=if(recording)"● STOP" else "● REC"
            button.setTextColor(Color.WHITE)
            button.background=GradientDrawable().apply {
                cornerRadius=dp(12f).toFloat()
                setColor(if(recording)Color.rgb(154,35,48)
                    else Color.rgb(60,109,133))
                setStroke(dp(1f).coerceAtLeast(1),
                    if(recording)Color.rgb(255,106,115)
                    else Color.rgb(113,204,238))
            }
        }
        drawerControl?.let { gear ->
            gear.text=if(drawerOpen)"▴" else "⚙"
            gear.contentDescription=if(drawerOpen)
                "Close quick HUD controls" else "Open quick HUD controls"
            gear.background=if(drawerOpen)GradientDrawable().apply{
                cornerRadius=dp(12f).toFloat()
                setColor(Color.rgb(11,80,101))
                setStroke(dp(1f).coerceAtLeast(1),Color.rgb(94,224,255))
            } else rounded(.35f)
        }
    }

    /** Two compact 4-control rows, plus a separate opacity footer.
     * A verified SurfaceFlinger provider is mandatory before offering FPS. */
    private fun newDrawer():LinearLayout {
        val column=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7f),dp(7f),dp(7f),dp(7f))
        }
        fun makeRow()=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        fun addChip(row:LinearLayout,label:String,handler:()->Unit):TextView {
            val chip=action(label,handler).apply {
                textSize=11f
                setPadding(dp(6f),dp(5f),dp(6f),dp(5f))
            }
            row.addView(chip,LinearLayout.LayoutParams(
                0,dp(39f),1f).apply {marginEnd=dp(4f)})
            return chip
        }
        val top=makeRow()
        drawerSwitches["CPU T"]=addChip(top,"CPU T") {
            changeOptions(opts.copy(showCpuTemp=!opts.showCpuTemp))
        }
        drawerSwitches["GPU T"]=addChip(top,"GPU T") {
            changeOptions(opts.copy(showGpuTemp=!opts.showGpuTemp))
        }
        drawerSwitches["CPU %"]=addChip(top,"CPU %") {
            changeOptions(opts.copy(showCpuLoad=!opts.showCpuLoad))
        }
        drawerSwitches["GPU %"]=addChip(top,"GPU %") {
            changeOptions(opts.copy(showGpuLoad=!opts.showGpuLoad))
        }
        column.addView(top)
        val bottom=makeRow()
        drawerSwitches["RAM"]=addChip(bottom,"RAM") {
            changeOptions(opts.copy(showRam=!opts.showRam))
        }
        drawerSwitches["W"]=addChip(bottom,"W") {
            changeOptions(opts.copy(showPower=!opts.showPower))
        }
        addChip(bottom,"Dock") {
            dock((params?.x?:0)+(root?.width?:0)/2>=windowWidth()/2)
        }
        drawerRecControl=addChip(bottom,"REC") {
            onRecord?.invoke()
        }
        column.addView(bottom,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(43f)))
        val opacity=makeRow()
        opacity.addView(action("−"){changeOptions(
            opts.copy(opacity=(opts.opacity-.1f).coerceAtLeast(.30f)))},
            LinearLayout.LayoutParams(dp(45f),dp(33f)))
        opacityLabel=styledLabel("",11f).also { label ->
            label.gravity=Gravity.CENTER
            opacity.addView(label,LinearLayout.LayoutParams(dp(105f),dp(33f)))
        }
        opacity.addView(action("+"){changeOptions(
            opts.copy(opacity=(opts.opacity+.1f).coerceAtMost(.90f)))},
            LinearLayout.LayoutParams(dp(45f),dp(33f)))
        opacity.addView(styledLabel(
            "  ✓ enabled · ○ hidden",9f))
        column.addView(opacity)
        refreshDrawerControls()
        return column
    }

    fun show(options:HudOptions,onRecordClick:()->Unit,onLockClick:()->Unit,
        onUnlockClick:()->Unit,onDismissClick:()->Unit,
        onOptionsChanged:(HudOptions)->Unit = {}):String {
        if(!permissionGranted())return "HUD_PERMISSION_MISSING"
        // Never construct another WindowManager view while a prior view or
        // unlock chip is still tracked. This survives Service recreation within
        // the same :monitor PID via MonitorHudOwner.
        if(attached()) {
            opts=options
            onRecord=onRecordClick;onLock=onLockClick
            onUnlock=onUnlockClick;onClose=onDismissClick
            this.onOptionsChanged=onOptionsChanged
            return try {
                setVisible(true)
                applyAppearance()
                "HUD_ALREADY_ATTACHED"
            }catch(e:Exception){"HUD_REBIND_FAILED_"+e.javaClass.simpleName}
        }
        if(root!=null || unlockWindow!=null) {
            val cleaned=hide()
            if(root!=null || unlockWindow!=null)
                return "HUD_OWNERSHIP_UNRESOLVED_"+cleaned
        }
        opts=options
        onRecord=onRecordClick;onLock=onLockClick
        onUnlock=onUnlockClick;onClose=onDismissClick
        this.onOptionsChanged=onOptionsChanged
        disposed=false
        locked=false;collapsed=false;drawerOpen=false
        drawerSwitches.clear()
        opacityLabel=null;drawerRecControl=null
        val line=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val label=styledLabel("",12f).apply {
            setPadding(dp(7f),dp(4f),dp(8f),dp(4f))
        }
        mainText=label
        // A weighted telemetry label shrinks BEFORE settings/lock/REC do.
        // Previously a long metric string could push the ⚙ button off-screen.
        line.addView(label,LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
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
        minimizeControl=action("‹"){dock(
            (params?.x?:0)+(root?.width?:0)/2>=windowWidth()/2)
        }.apply {
            contentDescription="Dock HUD to nearest edge; tap edge tab to restore"
        }
        line.addView(minimizeControl)
        lockControl=action(""){onLock?.invoke()}.apply {
            background=FlatLockChipDrawable(locked=false)
            setPadding(0,0,0,0)
            minimumWidth=dp(38f)
            minimumHeight=dp(38f)
            contentDescription="Lock HUD: let touches pass through to the game"
        }
        line.addView(lockControl)
        closeControl=action("×"){onClose?.invoke()}
        line.addView(closeControl)
        pill=line

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
            addView(newDrawer().also{drawer=it})
            addView(tab)
        }
        root=frame
        // Fix the panel width to the available viewport. The two settings
        // rows scroll internally instead of expanding the whole overlay beyond
        // the screen and hiding its settings/unlock controls.
        val hudWidth=minOf(dp(475f),
            (windowWidth()-dp(24f)).coerceAtLeast(dp(190f)))
        val p=WindowManager.LayoutParams(
            hudWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START
            x=(windowWidth()-hudWidth-dp(12f)).coerceAtLeast(0)
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
            // Actual pill width is measured after attach. Keep the initial HUD
            // wholly on screen without a second overlay or busy positioning loop.
            frame.post {
                if(root===frame && attached() && !collapsed) {
                    val safe=(windowWidth()-frame.width-dp(12f)).coerceAtLeast(0)
                    if(p.x>safe) {
                        p.x=safe
                        runCatching{wm.updateViewLayout(frame,p)}
                    }
                }
            }
            "HUD_ATTACHED"
        }catch(e:Exception){
            // addView might have succeeded before appearance/positioning
            // failed. Do not forget its WindowManager handle.
            val cleaned=hide()
            "HUD_ADD_FAILED_"+e.javaClass.simpleName+" cleanup="+cleaned
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
        syncBlink()
        p.x=if(toRight) (windowWidth()-dp(44f)).coerceAtLeast(0) else 0
        runCatching{wm.updateViewLayout(frame,p)}
    }
    private fun expand() {
        if(locked || !collapsed)return
        val frame=root ?:return
        val p=params ?:return
        collapsed=false
        applyAppearance()
        syncBlink()
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
        syncBlink()
    }
    fun configure(options:HudOptions) {
        opts=options
        applyAppearance()
        syncBlink()
    }
    private fun repositionUnlockControl() {
        val chip=unlockWindow ?:return
        val cp=unlockParams ?:return
        val p=params ?:return
        val icon=lockControl ?:return
        cp.x=(p.x+(pill?.left?:0)+icon.left-dp(6f))
            .coerceIn(0,(windowWidth()-dp(48f)).coerceAtLeast(0))
        cp.y=(p.y+(pill?.top?:0)+icon.top-dp(5f))
            .coerceIn(0,(windowHeight()-dp(48f)).coerceAtLeast(0))
        runCatching {wm.updateViewLayout(chip,cp)}
    }

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
        refreshDrawerControls()
        val scale=when(opts.size) {"small"->.82f;"large"->1.20f;else->1f}
        mainText?.textSize=12f*scale
        // All enabled metrics stay visible in short lines, with REC, gear,
        // dock and unlock controls remaining fixed-size touch targets.
        mainText?.maxWidth=dp(460f*scale)
        mainText?.setSingleLine(false)
        mainText?.maxLines=5
        mainText?.ellipsize=null
        recText?.textSize=11f*scale
        // The unlocked main control and separate locked recovery chip share the
        // same vector linework, without an OS-dependent emoji glyph.
        // Retain the icon's layout width so the separate unlock chip is exactly
        // where the user expects it, but it cannot receive main-window touches.
        lockControl?.visibility=if(locked)View.INVISIBLE else View.VISIBLE
        closeControl?.visibility=if(recording)View.GONE else View.VISIBLE
        val color=when(thermalValue?.direction) {
            ThermalTrendFilter.Direction.RISING -> Color.rgb(255,107,0)
            ThermalTrendFilter.Direction.FALLING -> Color.rgb(56,189,248)
            else -> Color.rgb(255,199,0)
        }
        // The dock tab's GradientDrawable is allocated when the view is
        // constructed; just update its color on appearance changes.
        (dockVisual?.background as? GradientDrawable)?.setColor(color)
        renderTelemetry()
        renderAction()
        if(attached())runCatching{params?.let{wm.updateViewLayout(frame,it)}}
        if(drawerOpen && !collapsed)frame.post {
            if(root===frame && attached() && drawerOpen && !collapsed) {
                val p=params ?:return@post
                val safeX=(windowWidth()-frame.width-dp(8f)).coerceAtLeast(0)
                if(p.x>safeX) {
                    p.x=safeX
                    runCatching{wm.updateViewLayout(frame,p)}
                }
                if(locked)repositionUnlockControl()
            }
        }
        if(locked)frame.post{if(root===frame)repositionUnlockControl()}
    }

    fun mode(isRecording:Boolean,startedMs:Long=0L) {
        recording=isRecording
        startedAtMs=startedMs
        if(!isRecording) {
            latest=null;thermal.reset();thermalValue=null
            metricLevels.clear()
        }
        applyAppearance()
        syncBlink()
    }

    fun update(s:Snapshot,count:Long) {
        if(!recording || !attached())return
        latest=s
        thermalValue=thermal.update(s.timeMs,s.cpuTempC)
        updateLevels(s)
        val thermalColor=when(thermalValue?.direction) {
            ThermalTrendFilter.Direction.RISING -> Color.rgb(255,107,0)
            ThermalTrendFilter.Direction.FALLING -> Color.rgb(56,189,248)
            else -> Color.rgb(255,199,0)
        }
        (dockVisual?.background as? GradientDrawable)?.setColor(thermalColor)
        // Rendering consumes only the recorder's current sample; never polls
        // hardware at an independent cadence.
        if(visible())renderTelemetry()
    }

    private fun severityColor(level:HudSeverity.Level):Int=when(level){
        HudSeverity.Level.ELEVATED->Color.rgb(255,199,0)
        HudSeverity.Level.HIGH->Color.rgb(255,94,104)
        else->Color.rgb(241,249,255)
    }
    private fun level(key:String,newLevel:HudSeverity.Level):HudSeverity.Level {
        metricLevels[key]=newLevel
        return newLevel
    }
    private fun updateLevels(s:Snapshot) {
        if(!opts.adaptiveColors){metricLevels.clear();return}
        level("CPU T",HudSeverity.thermal(thermalValue?.smoothedC,
            metricLevels["CPU T"]?:HudSeverity.Level.NORMAL))
        level("GPU T",HudSeverity.thermal(s.gpuTempC,
            metricLevels["GPU T"]?:HudSeverity.Level.NORMAL))
        level("CPU %",HudSeverity.usage(s.cpuPercent,
            metricLevels["CPU %"]?:HudSeverity.Level.NORMAL))
        level("GPU %",HudSeverity.usage(s.gpuPercent,
            metricLevels["GPU %"]?:HudSeverity.Level.NORMAL))
        if(opts.showRam){
            val manager=context.getSystemService(ActivityManager::class.java)
            val memory=ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(memory)
            level("RAM",if(manager==null)HudSeverity.Level.NORMAL else
                HudSeverity.ram(memory.lowMemory,memory.availMem,
                    memory.threshold,metricLevels["RAM"]?:HudSeverity.Level.NORMAL))
        }
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
        val items=mutableListOf<Pair<String,HudSeverity.Level>>()
        fun add(label:String,value:String) {
            items+=(label+" "+value) to (if(opts.adaptiveColors)
                metricLevels[label]?:HudSeverity.Level.NORMAL
                else HudSeverity.Level.NORMAL)
        }
        if(opts.showCpuTemp) {
            val direction=when(thermalValue?.direction) {
                ThermalTrendFilter.Direction.RISING -> " ▲"
                ThermalTrendFilter.Direction.FALLING -> " ▼"
                ThermalTrendFilter.Direction.STABLE -> " —"
                else -> ""
            }
            add("CPU T",fmt(thermalValue?.smoothedC,"°C")+direction)
            if(opts.layout=="pro" && thermalValue?.rawPeakC!=null)
                items+=("Peak "+fmt(thermalValue?.rawPeakC,"°C")) to
                    HudSeverity.Level.NORMAL
        }
        if(opts.showGpuTemp)add("GPU T",fmt(s?.gpuTempC,"°C"))
        if(opts.showCpuLoad)add("CPU %",s?.cpuPercent?.let{"$it%"}?:"—")
        if(opts.showGpuLoad)add("GPU %",s?.gpuPercent?.let{"$it%"}?:"—")
        if(opts.showRam)add("RAM",fmt(s?.ramGiB," GiB"))
        if(opts.showPower)add("BAT",fmt(s?.signedBatteryWatts," W"))
        if(opts.showFps && s?.surfaceFps!=null)
            add("FPS",fmt(s.surfaceFps,"",0))
        val builder=SpannableStringBuilder()
        if(items.isEmpty())builder.append("REC · telemetry")
        else items.forEachIndexed{index,pair->
            if(index>0)builder.append(
                if(items.size>2 && index%2==0)"\n" else " · ")
            val begin=builder.length
            builder.append(pair.first)
            if(pair.second!=HudSeverity.Level.NORMAL)
                builder.setSpan(ForegroundColorSpan(severityColor(pair.second)),
                    begin,builder.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.text=builder
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
        metricLevels.clear()

        // Remove BOTH windows. A failed remove retains the actual view handle
        // for a subsequent cleanup attempt; it cannot become an orphan merely
        // because this Java/Kotlin Service object was recreated.
        val chip=unlockWindow
        val chipRemoved=chip==null || removeTracked(chip)
        if(chipRemoved){unlockWindow=null;unlockParams=null}

        val frame=root
        val rootRemoved=frame==null || removeTracked(frame)
        if(rootRemoved){
            root=null;params=null;pill=null;drawer=null;dockTab=null
            mainText=null;recText=null;recDot=null
            drawerSwitches.clear()
            opacityLabel=null;drawerRecControl=null;minimizeControl=null
            recording=false;locked=false;collapsed=false
        }
        if(rootRemoved && chipRemoved){
            unbindCallbacks()
            return if(frame==null && chip==null)
                "HUD_NOT_ATTACHED" else "HUD_REMOVED"
        }
        return if(!rootRemoved)"HUD_REMOVE_FAILED_ROOT_RETAINED"
            else "HUD_REMOVE_FAILED_UNLOCK_RETAINED"
    }

}
