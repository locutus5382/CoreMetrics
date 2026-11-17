package com.locutus5382.coremetrics.monitor

import android.app.ActivityManager
import com.locutus5382.coremetrics.data.SessionStore
import org.json.JSONObject
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import android.text.Layout
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
    val layout: String = HudLayoutPolicy.STRIKER,
    val indicatorColor: String = "red",
    val blink: Boolean = true,
    val showCpuTemp: Boolean = true,
    val showGpuTemp: Boolean = true,
    val showCpuLoad: Boolean = false,
    val showGpuLoad: Boolean = false,
    val showRam: Boolean = true,
    val showPower: Boolean = true,
    val showBatteryTemp: Boolean = false,
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
            // Small shared SQL snapshot; legacy per-process preferences are
            // only a migration fallback when no HUD snapshot exists yet.
            val stored=runCatching {
                SessionStore(context).use { db ->
                    db.hudOptionsJson()?.let { JSONObject(it) }
                }
            }.getOrNull()
            fun f(k:String,fallback:Float):Float {
                val legacy=p.getFloat(k,fallback)
                return if(intent?.hasExtra(k)==true)intent.getFloatExtra(k,legacy)
                    else if(stored?.has(k)==true)
                        stored.optDouble(k,legacy.toDouble()).toFloat() else legacy
            }
            fun s(k:String,fallback:String):String {
                val legacy=p.getString(k,fallback)?:fallback
                return if(intent?.hasExtra(k)==true)
                    intent.getStringExtra(k)?:legacy
                    else if(stored?.has(k)==true)
                        stored.optString(k,legacy) else legacy
            }
            fun b(k:String,fallback:Boolean):Boolean {
                val legacy=p.getBoolean(k,fallback)
                return if(intent?.hasExtra(k)==true)intent.getBooleanExtra(k,legacy)
                    else if(stored?.has(k)==true)
                        stored.optBoolean(k,legacy) else legacy
            }
            return HudOptions(
                opacity=f(OPACITY,.57f).coerceIn(.30f,.90f),
                size=s(SIZE,"regular").takeIf { it in setOf("small","regular","large") } ?: "regular",
                layout=HudLayoutPolicy.normalize(s(LAYOUT,HudLayoutPolicy.STRIKER)),
                indicatorColor=s(INDICATOR,"red").takeIf {
                    it in setOf("red","orange","cyan","violet") } ?: "red",
                blink=b(BLINK,true),
                showCpuTemp=b("hud_cpuTemp",true),
                showGpuTemp=b("hud_gpuTemp",true),
                showCpuLoad=b("hud_cpuLoad",false),
                showGpuLoad=b("hud_gpuLoad",false),
                showRam=b("hud_ram",true),
                showPower=b("hud_power",true),
                showBatteryTemp=b("hud_batteryTemp",false),
                showFps=false, // Legacy setting intentionally ignored; no verified game FPS
                showSparkline=false,
                adaptiveColors=b(ADAPTIVE,false)
            )
        }
        fun addExtras(i: Intent, options: HudOptions): Intent = i.apply {
            putExtra(OPACITY,options.opacity)
            putExtra(SIZE,options.size)
            putExtra(LAYOUT,HudLayoutPolicy.normalize(options.layout))
            putExtra(INDICATOR,options.indicatorColor)
            putExtra(BLINK,options.blink)
            putExtra(ADAPTIVE,options.adaptiveColors)
            for ((k,v) in listOf(
                "cpuTemp" to options.showCpuTemp,"gpuTemp" to options.showGpuTemp,
                "cpuLoad" to options.showCpuLoad,"gpuLoad" to options.showGpuLoad,
                "ram" to options.showRam,"power" to options.showPower,
                "batteryTemp" to options.showBatteryTemp,
                "sparkline" to options.showSparkline
            )) putExtra("hud_"+k,v)
        }
    }
    /** HUD edits are rare user actions, never per-sample writes. Persist
     * the complete snapshot so a long-lived :monitor process and QS tile
     * cannot observe a stale preference cache.
     */
    fun save(context:Context):Boolean {
        val p=context.getSharedPreferences("coremetrics_settings",Context.MODE_PRIVATE)
        p.edit().putFloat(OPACITY,opacity)
            .putString(SIZE,size).putString(LAYOUT,HudLayoutPolicy.normalize(layout))
            .putString(INDICATOR,indicatorColor).putBoolean(BLINK,blink)
            .putBoolean("hud_cpuTemp",showCpuTemp)
            .putBoolean("hud_gpuTemp",showGpuTemp)
            .putBoolean("hud_cpuLoad",showCpuLoad)
            .putBoolean("hud_gpuLoad",showGpuLoad)
            .putBoolean("hud_ram",showRam)
            .putBoolean("hud_power",showPower)
            .putBoolean("hud_batteryTemp",showBatteryTemp)
            .putBoolean("hud_fps",showFps)
            .putBoolean("hud_sparkline",false)
            .putBoolean(ADAPTIVE,adaptiveColors).apply()
        val json=JSONObject()
            .put(OPACITY,opacity.toDouble())
            .put(SIZE,size).put(LAYOUT,HudLayoutPolicy.normalize(layout))
            .put(INDICATOR,indicatorColor).put(BLINK,blink)
            .put("hud_cpuTemp",showCpuTemp)
            .put("hud_gpuTemp",showGpuTemp)
            .put("hud_cpuLoad",showCpuLoad)
            .put("hud_gpuLoad",showGpuLoad)
            .put("hud_ram",showRam)
            .put("hud_power",showPower)
            .put("hud_batteryTemp",showBatteryTemp)
            .put(ADAPTIVE,adaptiveColors)
        return runCatching {
            SessionStore(context).use { db ->
                db.saveHudOptionsJson(json.toString())
            }
        }.getOrDefault(false)
    }
    fun selectedCount() = listOf(showCpuTemp,showGpuTemp,showCpuLoad,
        showGpuLoad,showRam,showPower,showBatteryTemp).count { it }
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
    // Short attachment latency is not a lost WindowManager owner. A genuinely
    // detached tracked view can be cleaned only after this grace period.
    private var lastAddRequestedAt=0L
    private var pill:LinearLayout?=null
    private var readingsRow:LinearLayout?=null
    private var controlsRow:LinearLayout?=null
    private var topActionsRow:LinearLayout?=null
    private var bottomActionsRow:LinearLayout?=null
    private var cardHeading:TextView?=null
    private var structuralLayout:String?=null
    private var drawer:LinearLayout?=null
    private var dockTab:LinearLayout?=null
    private var dockVisual:View?=null
    private var mainText:TextView?=null
    private var secondaryText:TextView?=null
    private var tertiaryText:TextView?=null
    private var metricsColumn:LinearLayout?=null
    private var thermalIndicator:View?=null
    private var lockedSpace:View?=null
    private var lastHeadroom:Double?=null
    private var headroomAtElapsedMs:Long=-1L
    private var lastBadgeLevel:ThermalIndicatorPolicy.Level?=null
    private var lastBatteryOverheat:Boolean=false
    private var lastDockRecording:Boolean?=null
    // No four-element list allocation on every 2s/5s telemetry update.
    private var appliedX=Int.MIN_VALUE
    private var appliedY=Int.MIN_VALUE
    private var appliedWidth=Int.MIN_VALUE
    private var appliedHeight=Int.MIN_VALUE
    private fun invalidateLayoutCache() {
        appliedX=Int.MIN_VALUE;appliedY=Int.MIN_VALUE
        appliedWidth=Int.MIN_VALUE;appliedHeight=Int.MIN_VALUE
    }
    private fun markLayoutApplied(p:WindowManager.LayoutParams) {
        appliedX=p.x;appliedY=p.y
        appliedWidth=p.width;appliedHeight=p.height
    }
    private fun layoutChanged(p:WindowManager.LayoutParams):Boolean =
        p.x!=appliedX || p.y!=appliedY ||
            p.width!=appliedWidth || p.height!=appliedHeight
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
    private val batteryTemperature=BatteryTemperatureAdvisory()
    private var batteryTemperatureState=BatteryTemperatureAdvisory.State.UNAVAILABLE
    private val thermal=ThermalTrendFilter()
    private var thermalValue:ThermalTrendFilter.Reading?=null
    private var recording=false
    private var startedAtMs=0L
    private var blinkOn=true
    private var locked=false
    private var collapsed=false
    private var dockEdge=DockEdge.RIGHT
    private var expandedY=0
    private var drawerOpen=false
    private var expandedX=0
    // Keep the compact HUD at the right edge unless the user explicitly drags it.
    private var anchoredRight=true
    private var onRecord:(()->Unit)?=null
    private var onLock:(()->Unit)?=null
    private var onUnlock:(()->Unit)?=null
    private var onClose:(()->Unit)?=null
    private var onOptionsChanged:((HudOptions)->Unit)?=null
    private var disposed=false

    private fun shouldBlink() =
        !disposed && recording && opts.blink && !collapsed && !locked && visible()

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
    private fun rounded(alpha:Float=opts.opacity):GradientDrawable {
        val layout=HudLayoutPolicy.normalize(opts.layout)
        val glass=layout==HudLayoutPolicy.GLASS_CARD
        val ribbon=layout==HudLayoutPolicy.RIBBON
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if(glass)intArrayOf(
                Color.argb((255*alpha).roundToInt(),6,19,44),
                Color.argb((242*alpha).roundToInt(),20,55,91),
                Color.argb((226*alpha).roundToInt(),11,35,68))
            else if(ribbon)intArrayOf(
                Color.argb((255*alpha).roundToInt(),5,19,38),
                Color.argb((240*alpha).roundToInt(),18,51,80))
            else intArrayOf(
                Color.argb((255*alpha).roundToInt(),7,17,40),
                Color.argb((220*alpha).roundToInt(),26,56,107))
        ).apply {
            cornerRadius=dp(when(layout) {
                HudLayoutPolicy.GLASS_CARD -> 16f
                HudLayoutPolicy.RIBBON -> 12f
                else -> 23f
            }).toFloat()
            setStroke(dp(1f).coerceAtLeast(1),
                Color.argb(if(glass)192 else if(ribbon)165 else 150,
                    210,234,255))
        }
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
        if(!show) {
            view.animate().cancel()
            view.alpha=1f;view.scaleX=1f;view.scaleY=1f
        }
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
        // Stable anchor: the external unlock target sits over the rightmost
        // 48dp reserved inside the *locked* compact telemetry card. We cannot
        // anchor to a GONE action row because it has no meaningful coordinates.
        val oldX=main.x+main.width-dp(48f)
        val oldY=main.y+((root?.height?:dp(60f))-dp(48f))/2
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
                syncBlink()
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
            syncBlink()
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

    /** Neutral Frosted Glass: enabled = translucent white glass with a subtle
     * hairline and check; disabled = text only, without a colored tile. */
    private fun frostedChip():GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(112,226,241,255),Color.argb(45,190,221,245))
    ).apply {
        cornerRadius=dp(11f).toFloat()
        setStroke(dp(1f).coerceAtLeast(1),Color.argb(170,239,249,255))
    }
    private fun paintDrawerToggle(view:TextView,label:String,enabled:Boolean) {
        view.text=(if(enabled)"✓  " else "")+label
        view.contentDescription=label+(if(enabled)" enabled" else " disabled")
        view.setTextColor(if(enabled)Color.WHITE else Color.argb(183,207,224,239))
        view.typeface=Typeface.create("sans-serif-medium",
            if(enabled)Typeface.BOLD else Typeface.NORMAL)
        view.background=if(enabled)frostedChip() else null
    }

    private fun refreshDrawerControls() {
        val selected=mapOf(
            "CPU T" to opts.showCpuTemp,"GPU T" to opts.showGpuTemp,
            "CPU %" to opts.showCpuLoad,"GPU %" to opts.showGpuLoad,
            "W" to opts.showPower,
            "BAT T" to opts.showBatteryTemp,
            "RAM" to opts.showRam)
        drawerSwitches.forEach { (name,view) ->
            paintDrawerToggle(view,name,selected[name]==true)
        }
        opacityLabel?.text=String.format(Locale.US,
            " Glass %d%% ",(opts.opacity*100f).roundToInt())
        drawerRecControl?.let { button ->
            button.text=if(recording)"● STOP" else "● REC"
            button.setTextColor(if(recording)Color.rgb(255,182,189)
                else Color.WHITE)
            button.background=frostedChip()
        }
        drawerControl?.let { gear ->
            gear.text=if(drawerOpen)"▴" else "⚙"
            gear.contentDescription=if(drawerOpen)
                "Close quick HUD controls" else "Open quick HUD controls"
            gear.background=if(drawerOpen)frostedChip() else rounded(.35f)
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
                // Unselected controls have no large colored backgrounds.
                background=null
                setTextColor(Color.argb(193,222,236,247))
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
            dock(nearestDockEdge())
        }
        drawerRecControl=addChip(bottom,"REC") {
            onRecord?.invoke()
        }
        // The dock control is functional text, not a loud accent tile.
        column.addView(bottom,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(43f)))
        val opacity=makeRow()
        opacity.addView(action("−"){changeOptions(
            opts.copy(opacity=(opts.opacity-.1f).coerceAtLeast(.30f)))}
            .apply {background=frostedChip()},
            LinearLayout.LayoutParams(dp(45f),dp(33f)))
        opacityLabel=styledLabel("",11f).also { label ->
            label.gravity=Gravity.CENTER
            opacity.addView(label,LinearLayout.LayoutParams(dp(105f),dp(33f)))
        }
        opacity.addView(action("+"){changeOptions(
            opts.copy(opacity=(opts.opacity+.1f).coerceAtMost(.90f)))}
            .apply {background=frostedChip()},
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
        // Never create a second view during attachment; a tracked root wins.
        // If Android detached it completely (neither parent nor attachment)
        // well after addView, reclaim the stale handles before any retry.
        // This avoids a permanently inert HUD after a failed/window teardown.
        val previousRoot=root
        if(previousRoot!=null && !previousRoot.isAttachedToWindow &&
            previousRoot.parent==null &&
            SystemClock.elapsedRealtime()-lastAddRequestedAt>2_500L) {
            val removed=hide()
            if(root!=null || unlockWindow!=null)
                return "HUD_OWNERSHIP_UNRESOLVED_"+removed
        }
        if(root!=null) {
            // WindowManager.addView() may not yet mark the view as attached.
            // A tracked root is already owned: NEVER allocate another window
            // merely because isAttachedToWindow is briefly false.
            // Service onDestroy cancels animation callbacks. Reattaching
            // callbacks to a still-attached view must revive them as well.
            disposed=false
            opts=options
            onRecord=onRecordClick;onLock=onLockClick
            onUnlock=onUnlockClick;onClose=onDismissClick
            this.onOptionsChanged=onOptionsChanged
            return try {
                // Do NOT force the previous root visible while rebinding.
                // The Service applies app-foreground/user-preference visibility
                // immediately after show(), without flashing over CoreMetrics.
                applyAppearance()
                syncBlink()
                "HUD_ALREADY_ATTACHED"
            }catch(e:Exception){"HUD_REBIND_FAILED_"+e.javaClass.simpleName}
        }
        if(unlockWindow!=null) {
            // A previously retained unlock chip without a root must be
            // removed before a new HUD can acquire WindowManager ownership.
            val cleaned=hide()
            if(root!=null || unlockWindow!=null)
                return "HUD_OWNERSHIP_UNRESOLVED_"+cleaned
        }
        opts=options
        invalidateLayoutCache();lastBadgeLevel=null;lastDockRecording=null
        onRecord=onRecordClick;onLock=onLockClick
        onUnlock=onUnlockClick;onClose=onDismissClick
        this.onOptionsChanged=onOptionsChanged
        disposed=false
        locked=false;collapsed=false;drawerOpen=false
        dockEdge=DockEdge.RIGHT
        anchoredRight=true
        drawerSwitches.clear()
        opacityLabel=null;drawerRecControl=null
        // Two compact rows: device readings above, actions along the outer
        // right edge below. Controls no longer sit in the middle of gameplay.
        // One horizontal card: the telemetry sits beside a narrow, two-level
        // action cluster. The bottom-left dead area of the old HUD is gone.
        val line=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val readings=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        line.addView(readings,LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        readingsRow=readings
        val actions=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
        }
        line.addView(actions,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        controlsRow=actions
        val label=styledLabel("",12f)
        mainText=label
        val secondary=styledLabel("",12f).apply {visibility=View.GONE}
        val tertiary=styledLabel("",12f).apply {visibility=View.GONE}
        secondaryText=secondary;tertiaryText=tertiary
        val cells=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        metricsColumn=cells
        cells.addView(label,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        cells.addView(secondary,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        cells.addView(tertiary,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        readings.addView(cells,LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        // A dedicated, 48dp placeholder lets the independent unlock chip
        // overlay the locked HUD without leaving a touch-blocking main window.
        lockedSpace=View(context).apply { visibility=View.GONE }
        readings.addView(lockedSpace,LinearLayout.LayoutParams(dp(48f),dp(48f)))
        // Optional Android OS warning only. Normal (NONE) and unavailable
        // signals occupy ZERO room; CPU temperature remains a numeric metric.
        thermalIndicator=View(context).apply {
            visibility=View.GONE
            contentDescription="Device-wide Android thermal warning"
            background=GradientDrawable().apply {
                cornerRadius=dp(2f).toFloat()
                setColor(Color.rgb(255,199,0))
            }
        }
        readings.addView(thermalIndicator,0,LinearLayout.LayoutParams(
            dp(4f),dp(48f)).apply { marginEnd=dp(2f) })
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
        val topActions=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
        }
        topActions.addView(rec)
        actions.addView(topActions,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,dp(33f)))
        topActionsRow=topActions
        val bottomActions=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.END or Gravity.CENTER_VERTICAL
        }
        minimizeControl=action("⇥"){dock(nearestDockEdge())}.apply {
            contentDescription="Collapse HUD to visible edge tab; tap tab to expand"
            minWidth=dp(39f)
            minHeight=dp(34f)
        }
        bottomActions.addView(minimizeControl)
        lockControl=action(""){onLock?.invoke()}.apply {
            background=FlatLockChipDrawable(locked=false)
            setPadding(0,0,0,0)
            minimumWidth=dp(38f)
            minimumHeight=dp(38f)
            contentDescription="Lock HUD: allow game touches through telemetry"
        }
        bottomActions.addView(lockControl)
        closeControl=action("×"){onClose?.invoke()}.apply {
            minWidth=dp(36f)
            minHeight=dp(34f)
            contentDescription="Close standby HUD"
        }
        bottomActions.addView(closeControl)
        actions.addView(bottomActions)
        bottomActionsRow=bottomActions
        // HUD metric customization is in the app's HUD Studio. Do not
        // allocate or expose the former in-game settings tray.
        drawerControl=null
        pill=line

        val tab=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            isClickable=true
            contentDescription="Expand docked CoreMetrics HUD"
            setOnClickListener{expand()}
        }
        val badge=styledLabel("CM",12f).apply {
            gravity=Gravity.CENTER
            typeface=Typeface.DEFAULT_BOLD
            background=GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(244,12,37,66),Color.argb(247,18,56,92))
            ).apply {
                cornerRadius=dp(14f).toFloat()
                setStroke(dp(2f).coerceAtLeast(2),Color.rgb(76,211,243))
            }
            setTextColor(Color.rgb(135,231,255))
            contentDescription="Visible CoreMetrics edge tab; tap to expand HUD"
        }
        tab.addView(badge,LinearLayout.LayoutParams(dp(56f),dp(80f)))
        dockVisual=badge
        dockTab=tab

        // The compact glass card gets one static branded heading.
        // All layouts retain exactly the same telemetry TextView and controls.
        val heading=styledLabel("COREMETRICS  /  TELEMETRY",10f).apply {
            setTextColor(Color.rgb(135,231,255))
            letterSpacing=.11f
            typeface=Typeface.DEFAULT_BOLD
            setPadding(dp(10f),dp(7f),dp(8f),dp(1f))
            visibility=View.GONE
        }
        cardHeading=heading
        val frame=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            addView(heading)
            addView(line)
            addView(tab)
        }
        root=frame
        // Initial width is temporary. applyAppearance() immediately sizes
        // the actual WindowManager window to the selected metric content.
        val hudWidth=minOf(dp(360f),
            (windowWidth()-dp(20f)).coerceAtLeast(dp(260f)))
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
        // Coalesce high-frequency drag MOVE events to one WM layout per
        // display frame. No periodic callback while the user is not dragging.
        var dragLayoutPending=false
        val dragLayout=Runnable {
            dragLayoutPending=false
            if(root===frame && attached())runCatching {
                wm.updateViewLayout(frame,p)
            }.onSuccess { markLayoutApplied(p) }
        }
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
                        .coerceIn(0,(windowWidth()-p.width).coerceAtLeast(0))
                    if(moved) anchoredRight=false
                    p.y=(baseY+deltaY.roundToInt())
                        .coerceIn(0,(windowHeight()-frame.height).coerceAtLeast(0))
                    if(!dragLayoutPending) {
                        dragLayoutPending=true
                        frame.postOnAnimation(dragLayout)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if(dragLayoutPending) {
                        frame.removeCallbacks(dragLayout)
                        dragLayout.run()
                    }
                    if(moved) {
                        // Approaching a screen border PARKS the expanded HUD,
                        // it does not collapse it. Explicit ⇥ remains available.
                        val parked=HudDockGeometry.park(
                            windowWidth(),windowHeight(),p.width,frame.height,
                            p.x,p.y,margin=dp(4f),snapDistance=dp(16f))
                        p.x=parked.x;p.y=parked.y
                        anchoredRight=parked.anchoredRight
                        expandedX=p.x;expandedY=p.y
                        dragLayout.run()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if(dragLayoutPending) {
                        frame.removeCallbacks(dragLayout)
                        dragLayout.run()
                    }
                    true
                }
                else -> false
            }
        }
        return try {
            lastAddRequestedAt=SystemClock.elapsedRealtime()
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

    private fun nearestDockEdge():DockEdge {
        val p=params ?:return DockEdge.RIGHT
        // Horizontal edge tabs are available deliberately by using ⇥ while
        // parked near the top; simply dragging to a corner never collapses.
        if(p.y<=dp(22f) && p.x>dp(50f) &&
            p.x+p.width<windowWidth()-dp(50f))return DockEdge.TOP
        return if(p.x+p.width/2<windowWidth()/2)DockEdge.LEFT else DockEdge.RIGHT
    }
    private fun dock(edge:DockEdge) {
        if(locked || !attached())return
        val p=params ?:return
        expandedX=p.x
        expandedY=p.y
        dockEdge=edge
        anchoredRight=edge==DockEdge.RIGHT
        collapsed=true
        drawerOpen=false
        applyAppearance()
        animateDockTransition()
        syncBlink()
    }
    private fun expand() {
        if(locked || !collapsed)return
        val p=params ?:return
        collapsed=false
        anchoredRight=dockEdge==DockEdge.RIGHT
        applyAppearance()
        p.x=when(dockEdge) {
            DockEdge.RIGHT -> (windowWidth()-p.width-dp(4f)).coerceAtLeast(0)
            DockEdge.LEFT -> dp(4f)
            DockEdge.TOP -> expandedX.coerceIn(0,(windowWidth()-p.width).coerceAtLeast(0))
        }
        p.y=expandedY.coerceIn(0,(windowHeight()-dp(48f)).coerceAtLeast(0))
        root?.let { runCatching{wm.updateViewLayout(it,p)}
            .onSuccess { markLayoutApplied(p) } }
        animateDockTransition()
    }

    /**
     * Exactly one 150ms fade/scale on an explicit dock/expand gesture.
     * WindowManager geometry, flags and touch ownership change synchronously;
     * we never animate an entire overlay window's position or poll its state.
     */
    private fun animateDockTransition() {
        val frame=root ?:return
        if(disposed || !visible() || locked)return
        frame.animate().cancel()
        frame.pivotX=if(dockEdge==DockEdge.LEFT)0f else frame.width.toFloat()
        frame.pivotY=0f
        frame.alpha=.84f
        frame.scaleX=.975f;frame.scaleY=.975f
        frame.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Keep one overlay window. Docking MUST change WindowManager dimensions
     * as well as the child visibility; an invisible 475dp window is not a tab. */
    private fun updateGeometry() {
        val frame=root?:return
        val p=params?:return
        val theme=HudLayoutPolicy.normalize(opts.layout)
        if(collapsed) {
            // 44dp accessible tap target; the DUKO lens draws only a 2dp
            // bezel line and status dot. Never leave an invisible big window.
            val width=dp(44f)
            val height=dp(44f)
            dockTab?.layoutParams=LinearLayout.LayoutParams(width,height)
            (dockVisual as? TextView)?.layoutParams=
                LinearLayout.LayoutParams(width,height)
            (dockVisual as? TextView)?.background=if(HudLayoutPolicy.lens(theme))
                HudEdgeLineDrawable(context.resources.displayMetrics.density,
                    dockEdge==DockEdge.LEFT)
            else GradientDrawable().apply {
                cornerRadius=dp(10f).toFloat()
                setColor(Color.argb(225,9,23,46))
                setStroke(dp(1f).coerceAtLeast(1),
                    Color.rgb(77,216,241))
            }
            p.width=width;p.height=height
            val xy=HudDockGeometry.place(dockEdge,windowWidth(),
                windowHeight(),width,height,expandedX,expandedY)
            p.x=xy.first;p.y=xy.second
            renderDockState()
        }else {
            p.height=WindowManager.LayoutParams.WRAP_CONTENT
            val text=mainText
            val measure=if(text!=null)
                Layout.getDesiredWidth(text.text,text.paint).roundToInt() else 0
            val sec=secondaryText
            val secondWidth=if(sec?.visibility==View.VISIBLE)
                Layout.getDesiredWidth(sec.text,sec.paint).roundToInt() else 0
            val scale=when(opts.size){
                "small"->.82f;"large"->1.18f;else->1f
            }
            val viewport=(windowWidth()-dp(12f)).coerceAtLeast(dp(220f))
            val controlWidth=if(locked)dp(48f) else when(theme) {
                HudLayoutPolicy.STRIKER->dp(120f)
                HudLayoutPolicy.TACTICAL->dp(84f)
                HudLayoutPolicy.DUKO->0
                else->dp(95f)
            }
            val desired=when(theme) {
                HudLayoutPolicy.STRIKER->dp(470f*scale)
                HudLayoutPolicy.TACTICAL->dp(355f*scale)
                HudLayoutPolicy.DUKO->dp(232f*scale)
                HudLayoutPolicy.GHOST->(measure+controlWidth+dp(18f))
                    .coerceAtLeast(dp(200f))
                HudLayoutPolicy.STRIP->(measure+controlWidth+dp(22f))
                    .coerceAtLeast(dp(430f*scale))
                else->dp(320f)
            }
            val maxWidth=when(theme) {
                HudLayoutPolicy.STRIKER->minOf(viewport,dp(520f))
                HudLayoutPolicy.TACTICAL->minOf(viewport,dp(420f))
                HudLayoutPolicy.DUKO->minOf(viewport,dp(295f))
                HudLayoutPolicy.GHOST->minOf(viewport,dp(800f))
                else->minOf(viewport,(windowWidth()*.95f).roundToInt())
            }
            val minWidth=minOf(maxWidth,dp(190f*scale))
            p.width=desired.coerceIn(minWidth,maxWidth)
            text?.maxWidth=when(theme){
                HudLayoutPolicy.STRIKER->((p.width-controlWidth-dp(18f))/2)
                    .coerceAtLeast(dp(75f))
                HudLayoutPolicy.TACTICAL->(p.width-controlWidth-dp(20f))
                    .coerceAtLeast(dp(85f))
                HudLayoutPolicy.DUKO->(p.width-dp(24f))
                    .coerceAtLeast(dp(90f))
                else->(p.width-controlWidth-dp(15f))
                    .coerceAtLeast(dp(75f))
            }
            secondaryText?.maxWidth=text?.maxWidth?:dp(120f)
            tertiaryText?.maxWidth=text?.maxWidth?:dp(120f)
            p.x=if(anchoredRight)
                (windowWidth()-p.width-dp(4f)).coerceAtLeast(0)
                else p.x.coerceIn(0,(windowWidth()-p.width).coerceAtLeast(0))
            p.y=p.y.coerceIn(0,(windowHeight()-dp(48f)).coerceAtLeast(0))
        }
        if(attached() && layoutChanged(p)) {
            runCatching{wm.updateViewLayout(frame,p)}
                .onSuccess {
                    markLayoutApplied(p)
                    if(locked)frame.post {
                        if(root===frame && locked)repositionUnlockControl()
                    }
                }
        }
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
        cp.x=(p.x+p.width-dp(48f))
            .coerceIn(0,(windowWidth()-dp(48f)).coerceAtLeast(0))
        cp.y=(p.y+((root?.height?:dp(60f))-dp(48f))/2)
            .coerceIn(0,(windowHeight()-dp(48f)).coerceAtLeast(0))
        runCatching {wm.updateViewLayout(chip,cp)}
    }

    /**
     * Reconfigure existing views IN PLACE when the user switches layouts.
     * Do not remove/re-add the WindowManager overlay or duplicate REC controls.
     * Called only on user configuration, service rebind or mode change.
     */
    private fun applyLayoutStructure() {
        val theme=HudLayoutPolicy.normalize(opts.layout)
        if(structuralLayout==theme)return
        val line=pill?:return
        val readings=readingsRow?:return
        val cells=metricsColumn?:return
        val actions=controlsRow?:return
        val top=topActionsRow?:return
        val bottom=bottomActionsRow?:return
        val striker=theme==HudLayoutPolicy.STRIKER
        val tactical=theme==HudLayoutPolicy.TACTICAL
        val lens=theme==HudLayoutPolicy.DUKO
        val slim=theme==HudLayoutPolicy.GHOST ||
            theme==HudLayoutPolicy.STRIP

        // All modes reuse precisely these existing views / REC callbacks.
        // This method is called when settings change, never by the sampler.
        line.orientation=if(lens)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        line.gravity=Gravity.CENTER_VERTICAL
        readings.layoutParams=if(lens)
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        else LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)

        cells.orientation=if(striker)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        cells.gravity=Gravity.CENTER_VERTICAL
        fun sizeOfCell(weight:Float)=if(weight>0f)
            LinearLayout.LayoutParams(
                0,LinearLayout.LayoutParams.WRAP_CONTENT,weight)
        else LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        mainText?.layoutParams=sizeOfCell(if(striker)1f else 0f)
        secondaryText?.visibility=if(striker||tactical)View.VISIBLE else View.GONE
        secondaryText?.layoutParams=sizeOfCell(if(striker)1f else 0f)
        tertiaryText?.visibility=if(tactical)View.VISIBLE else View.GONE
        tertiaryText?.layoutParams=sizeOfCell(0f)

        actions.orientation=if(lens||slim)LinearLayout.HORIZONTAL
            else LinearLayout.VERTICAL
        actions.gravity=Gravity.CENTER_VERTICAL or Gravity.END
        actions.layoutParams=if(lens)
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin=dp(4f)
            }
        else LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        top.layoutParams=if(lens)
            LinearLayout.LayoutParams(0,dp(35f),1f)
        else LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,dp(if(slim)37f else 35f))
        bottom.layoutParams=LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,dp(if(lens)35f else 39f))
        bottom.gravity=Gravity.END or Gravity.CENTER_VERTICAL

        // Ghost and Cinema Strip collapse by edge gesture; no permanently
        // visible giant ⇥ button. Tactical/Striker keep an explicit dock.
        minimizeControl?.visibility=if(lens||slim)View.GONE else View.VISIBLE
        structuralLayout=theme
        invalidateLayoutCache()
    }

    private fun applyAppearance() {
        val frame=root?:return
        applyLayoutStructure()
        val theme=HudLayoutPolicy.normalize(opts.layout)
        val striker=theme==HudLayoutPolicy.STRIKER
        val tactical=theme==HudLayoutPolicy.TACTICAL
        val strip=theme==HudLayoutPolicy.STRIP
        val ghost=theme==HudLayoutPolicy.GHOST
        val lens=theme==HudLayoutPolicy.DUKO
        cardHeading?.apply {
            visibility=if(tactical && !collapsed)View.VISIBLE else View.GONE
            text=if(recording)"COREMETRICS / LIVE TELEMETRY"
                else "COREMETRICS / READY"
            setTextColor(Color.rgb(93,215,245))
            setPadding(dp(9f),dp(5f),dp(8f),dp(1f))
        }
        frame.background=if(collapsed || ghost) null else
            HudFrameDrawable(theme,opts.opacity*(if(locked).90f else 1f),
                context.resources.displayMetrics.density)
        frame.setPadding(if(collapsed||ghost)0 else dp(if(lens)11f else 7f),
            if(collapsed||ghost)0 else dp(if(lens)9f else 5f),
            if(collapsed||ghost)0 else dp(if(lens)11f else 7f),
            if(collapsed||ghost)0 else dp(if(lens)9f else 5f))
        pill?.visibility=if(collapsed)View.GONE else View.VISIBLE
        dockTab?.visibility=if(collapsed)View.VISIBLE else View.GONE
        drawer?.visibility=View.GONE
        drawerOpen=false
        val scale=when(opts.size){"small"->.82f;"large"->1.18f;else->1f}
        val textSp=HudLayoutPolicy.nominalTextSp(theme,opts.size)
        for(view in arrayOf(mainText,secondaryText,tertiaryText))view?.apply {
            textSize=textSp
            typeface=when {
                lens||strip->Typeface.MONOSPACE
                striker->Typeface.create("sans-serif-condensed",
                    Typeface.BOLD)
                else->Typeface.create("sans-serif-medium",Typeface.NORMAL)
            }
            setTextColor(Color.rgb(239,247,255))
            setShadowLayer(if(ghost)dp(2f).toFloat() else 0f,
                0f,if(ghost)dp(1f).toFloat() else 0f,Color.BLACK)
            setPadding(dp(if(ghost)1f else if(lens)3f else 6f),
                dp(if(ghost)2f else 5f),
                dp(if(ghost)1f else if(lens)3f else 6f),
                dp(if(ghost)2f else 5f))
            setSingleLine(false)
            maxLines=if(lens)9 else if(tactical)4 else if(strip)5 else 6
            ellipsize=null
        }
        // Tactical has three substantive, independent labelled data blocks.
        // No extra background bitmap or shader is created on sampling ticks.
        for(view in arrayOf(mainText,secondaryText,tertiaryText)){
            view?.background=if(tactical)GradientDrawable().apply {
                cornerRadius=dp(4f).toFloat()
                setColor(Color.argb(110,12,34,58))
                setStroke(dp(.6f).coerceAtLeast(1),
                    Color.argb(80,118,172,200))
            } else null
        }
        secondaryText?.setPadding(dp(6f),dp(5f),dp(5f),dp(5f))
        tertiaryText?.setPadding(dp(6f),dp(5f),dp(5f),dp(5f))
        if(striker) {
            mainText?.setPadding(dp(8f),dp(6f),dp(7f),dp(6f))
            secondaryText?.setPadding(dp(10f),dp(6f),dp(3f),dp(6f))
        }
        recControl?.background=when {
            ghost||lens->null
            else->GradientDrawable().apply {
                cornerRadius=dp(if(striker)5f else 8f).toFloat()
                setColor(Color.argb(125,9,22,41))
                setStroke(dp(1f).coerceAtLeast(1),
                    Color.argb(210,255,91,109))
            }
        }
        recControl?.setPadding(dp(if(lens)2f else 8f),dp(3f),
            dp(if(lens)5f else 8f),dp(3f))
        recText?.apply {
            textSize=(if(lens)12f else 11f)*scale
            typeface=if(lens||strip)Typeface.MONOSPACE
                else Typeface.DEFAULT_BOLD
        }
        lockControl?.apply {
            if(lens){
                background=null
                text="[ LOCK ]"
                setTextColor(Color.rgb(225,237,249))
                textSize=11f*scale
                minimumWidth=dp(64f)
                minimumHeight=dp(36f)
            }else{
                background=FlatLockChipDrawable(locked=false)
                text=""
                minimumWidth=dp(38f)
                minimumHeight=dp(38f)
            }
        }
        controlsRow?.visibility=if(locked)View.GONE else View.VISIBLE
        lockedSpace?.visibility=if(locked)View.VISIBLE else View.GONE
        lockControl?.visibility=if(locked)View.GONE else View.VISIBLE
        closeControl?.visibility=if(recording)View.GONE else View.VISIBLE
        minimizeControl?.visibility=if(striker||tactical)View.VISIBLE else View.GONE
        refreshThermalIndicator()
        renderTelemetry()
        renderAction()
        updateGeometry()
        if(locked)frame.post{if(root===frame)repositionUnlockControl()}
    }

    fun mode(isRecording:Boolean,startedMs:Long=0L) {
        recording=isRecording
        startedAtMs=startedMs
        if(!isRecording) {
            latest=null;thermal.reset();thermalValue=null
            batteryTemperature.reset()
            batteryTemperatureState=BatteryTemperatureAdvisory.State.UNAVAILABLE
            lastHeadroom=null;headroomAtElapsedMs=-1L
            lastBatteryOverheat=false
            metricLevels.clear()
        }
        applyAppearance()
        syncBlink()
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(s:Snapshot,count:Long) {
        if(!recording || !attached())return
        latest=s
        // BAT T uses the existing sampler timestamp; no independent timer.
        batteryTemperatureState=batteryTemperature.update(
            s.batteryTempC,s.sampleElapsedMs)
        thermalValue=thermal.update(s.timeMs,s.cpuTempC)
        updateLevels(s)
        // Sparse headroom measurements are kept only for the HUD, with a
        // bounded freshness window. SQLite samples themselves stay sparse.
        if(s.thermalHeadroom!=null && s.sampleElapsedMs!=null) {
            lastHeadroom=s.thermalHeadroom
            headroomAtElapsedMs=s.sampleElapsedMs
        }
        refreshThermalIndicator()
        // Rendering consumes only the recorder's current sample; never polls
        // hardware at an independent cadence.
        if(visible()) {
            if(collapsed)renderDockState()
            else {
                renderTelemetry()
                updateGeometry()
            }
        }
    }

    private fun refreshThermalIndicator() {
        val s=latest
        val now=s?.sampleElapsedMs
        val freshHeadroom=if(now!=null && headroomAtElapsedMs>=0L &&
            now-headroomAtElapsedMs in 0L..25_000L) lastHeadroom else null
        val level=if(recording)ThermalIndicatorPolicy.level(
            s?.thermalStatus,freshHeadroom) else ThermalIndicatorPolicy.Level.UNKNOWN
        // OS NONE cannot establish absence of real throttling. The Odin ROM
        // has reported NONE with no useful headroom; do not waste 60dp on an
        // always-on badge. Only actual policy pressure gets a colored bar.
        val batteryOverheat=recording && s?.batteryOverheatReported==true
        val alert=recording &&
            (ThermalIndicatorPolicy.showHudAlert(level) || batteryOverheat)
        thermalIndicator?.apply {
            visibility=if(alert)View.VISIBLE else View.GONE
            if(lastBadgeLevel!=level || lastBatteryOverheat!=batteryOverheat) {
                background=GradientDrawable().apply {
                    cornerRadius=dp(2f).toFloat()
                    setColor(if(batteryOverheat ||
                        level==ThermalIndicatorPolicy.Level.SEVERE)
                        Color.rgb(255,94,104) else Color.rgb(255,199,0))
                }
                contentDescription=if(batteryOverheat)
                    "Android battery-health OVERHEAT reported"
                    else "Android OS-wide warning: "+
                        ThermalIndicatorPolicy.description(level)
                lastBadgeLevel=level
                lastBatteryOverheat=batteryOverheat
            }
        }
        // Battery-health overheat is a separate device-reported signal,
        // not an app-defined 39/42C warmth advisory.
        val description=if(batteryOverheat)
            "Android battery overheat reported. CoreMetrics device telemetry"
            else if(alert)
            "Android device thermal alert. "+ThermalIndicatorPolicy.description(level)
            else "CoreMetrics device telemetry"
        mainText?.let { if(it.contentDescription!=description) {
            it.contentDescription=description
        } }
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
            view.text=when(HudLayoutPolicy.normalize(opts.layout)) {
                HudLayoutPolicy.GLASS_CARD -> "Ready to record\nTelemetry starts after REC"
                HudLayoutPolicy.RIBBON -> "CM · READY  │  TAP REC TO START"
                else -> "COREMETRICS · READY\nNo sensor sampling until REC"
            }
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
            if(HudLayoutPolicy.card(opts.layout) && thermalValue?.rawPeakC!=null)
                items+=("Peak "+fmt(thermalValue?.rawPeakC,"°C")) to
                    HudSeverity.Level.NORMAL
        }
        if(opts.showGpuTemp)add("GPU T",fmt(s?.gpuTempC,"°C"))
        if(opts.showCpuLoad)add("CPU %",s?.cpuPercent?.let{"$it%"}?:"—")
        if(opts.showGpuLoad)add("GPU %",s?.gpuPercent?.let{"$it%"}?:"—")
        if(opts.showRam)add("RAM",fmt(s?.ramGiB," GiB"))
        if(opts.showPower)add("BAT",fmt(s?.signedBatteryWatts," W"))
        if(opts.showBatteryTemp) {
            val valid=s?.batteryTempC?.takeIf { it.isFinite() && it in -10.0..90.0 }
            add("BAT T",fmt(valid,"°C"))
        }
        // Game FPS is not collected by the ordinary-app recorder.
        val builder=SpannableStringBuilder()
        if(items.isEmpty())builder.append("REC · telemetry")
        val widthDp=(windowWidth()/context.resources.displayMetrics.density)
            .roundToInt()
        val twoRows=HudLayoutPolicy.ribbonTwoRows(items.size,widthDp)
        items.forEachIndexed{index,pair->
            if(index>0)builder.append(HudLayoutPolicy.divider(
                opts.layout,index,items.size,twoRows))
            val begin=builder.length
            builder.append(pair.first)
            if(pair.first.startsWith("BAT T ")) {
                // The BAT T warning outline is confined to this token, with
                // constant reserved width even when normal; no layout jump.
                builder.setSpan(BatteryTemperatureSpan(batteryTemperatureState,
                    context.resources.displayMetrics.density),begin,
                    builder.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }else if(pair.second!=HudSeverity.Level.NORMAL)
                builder.setSpan(ForegroundColorSpan(severityColor(pair.second)),
                    begin,builder.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.text=builder
    }

    private fun renderDockState() {
        if(!collapsed)return
        val badge=dockVisual as? TextView ?:return
        val time=if(recording) {
            val elapsed=((System.currentTimeMillis()-startedAtMs)/1000)
                .coerceAtLeast(0)
            String.format(Locale.US,"%02d:%02d",elapsed/60,elapsed%60)
        } else ""
        val top=dockEdge==DockEdge.TOP
        val label=when {
            !recording -> if(top)"CM · READY" else "CM\nREADY"
            top -> "● REC · $time"
            else -> "● R\n$time"
        }
        if(badge.text.toString()!=label)badge.text=label
        if(lastDockRecording!=recording) {
            val stroke=if(recording)Color.rgb(255,104,112)
                else Color.rgb(76,211,243)
            badge.setTextColor(if(recording)Color.rgb(255,175,180)
                else Color.rgb(135,231,255))
            (badge.background as? GradientDrawable)?.setStroke(
                dp(2f).coerceAtLeast(2),stroke)
            badge.contentDescription=if(recording)
                "CoreMetrics recording, elapsed "+time+", tap to expand HUD"
                else "CoreMetrics standby, tap to expand HUD"
            lastDockRecording=recording
        }else if(recording) {
            badge.contentDescription="CoreMetrics recording, elapsed "+
                time+", tap to expand HUD"
        }
    }
    private fun renderAction() {
        renderDockState()
        recDot?.text=if(recording && opts.blink && !blinkOn)"○ " else "● "
        recDot?.setTextColor(accent())
        val elapsed=((System.currentTimeMillis()-startedAtMs)/1000).coerceAtLeast(0)
        recText?.text=if(recording)
            String.format(Locale.US,"STOP %02d:%02d",elapsed/60,elapsed%60)
            else "REC"
    }

    fun hide():String {
        disposed=true
        root?.animate()?.cancel()
        // Restore default visual properties if Android keeps the WindowManager
        // owner attached for a later cleanup/rebind attempt.
        root?.alpha=1f;root?.scaleX=1f;root?.scaleY=1f
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
            root=null;lastAddRequestedAt=0L
            params=null;pill=null;readingsRow=null;controlsRow=null
            topActionsRow=null;bottomActionsRow=null;cardHeading=null
            structuralLayout=null;drawer=null;dockTab=null
            dockVisual=null;mainText=null;secondaryText=null;tertiaryText=null
            metricsColumn=null;thermalIndicator=null;recText=null;recDot=null
            recControl=null;lockControl=null;drawerControl=null
            closeControl=null;minimizeControl=null
            drawerSwitches.clear()
            opacityLabel=null;drawerRecControl=null
            latest=null;thermal.reset();thermalValue=null
            recording=false;locked=false;collapsed=false
            invalidateLayoutCache();lastBadgeLevel=null
            lastBatteryOverheat=false;lastDockRecording=null
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
