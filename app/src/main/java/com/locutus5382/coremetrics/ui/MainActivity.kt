package com.locutus5382.coremetrics.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import com.locutus5382.coremetrics.monitor.RecorderLease
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.app.StatusBarManager
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import com.locutus5382.coremetrics.analysis.SegmentAnalysis
import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.analysis.ScorecardModel
import com.locutus5382.coremetrics.analysis.ScorecardExportRequest
import com.locutus5382.coremetrics.analysis.DocumentWindowRequest
import com.locutus5382.coremetrics.analysis.SessionRegimeAnalyzer
import com.locutus5382.coremetrics.analysis.V08Analytics
import com.locutus5382.coremetrics.telemetry.ThermalTrendFilter
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.data.UiSessionStoreOwner
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.monitor.HudOptions
import com.locutus5382.coremetrics.monitor.RecorderJournal
import com.locutus5382.coremetrics.quick.SessionTile
import com.locutus5382.coremetrics.quick.HudTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val night = Color(0xFF0A1522)
private val cyan = Color(0xFF71D7E2)
private val white = Color(0xFFF0F5FA)
private val muted = Color(0xFFA8C4D1)
private val glow = Color(0xFF18445A)
private val pages = listOf("Live", "Sessions", "Compare", "HUD", "Settings")

private data class AbWindowExportRequest(
    val sessionAId:Long,val sessionBId:Long,
    val startElapsedMs:Long,val endElapsedMs:Long
)

private data class SegmentExportRequest(
    val sessionId:Long,val startMs:Long,val endMs:Long
)

private data class ScreenStatus(
    val running: Boolean = false, val starting: Boolean = false,
    val standby: Boolean = false, val pendingRecovery:Boolean=false,
    // Initial/cold-start UI must not present IDLE before the first IPC read.
    val known:Boolean=false,
    val sessionId: Long = 0, val count: Long = 0,
    val message: String = "Idle",
    val cpu: String? = null, val gpu: String? = null,
    val cpuLoad: String? = null, val gpuLoad: String? = null,
    val peakCore: String? = null, val ram: String? = null,
    val clock: String? = null, val app: String? = null,
    val power: String? = null, val powerStatus: String? = null,
    val powerSource: String? = null, val fps: String? = null,
    val frameMs: String? = null, val fpsStatus: String? = null,
    val observedLimits: String? = null,
    val coreLoads: String? = null,
    val hudStatus: String? = null, val hudLocked: Boolean = false,
    val hudVisible: Boolean = false,
    val hudOptionRevision: Long = 0L, val hudChanged: HudOptions? = null,
    val lastAt: Long = 0
)

class MainActivity : ComponentActivity() {
    /** Hardware shoulder navigation even after touch focus is lost. */
    private var controllerTabAction:((Int)->Unit)?=null
    private var controllerFocusAction:((Int)->Boolean)?=null
    private var controllerScrollAction:((Float)->Unit)?=null
    private var controllerScrubAction:((Float)->Unit)?=null
    private var controllerIsCompare:(()->Boolean)?=null
    private var lastAnalogMoveMs=0L
    private var lastAnalogFocusMs=0L
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0) {
            val delta=when(event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_L2 -> -1
                KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_R2 -> 1
                else -> 0
            }
            if(delta!=0)controllerTabAction?.let { it(delta);return true }
        }
        // Some controllers do not translate A to DPAD_CENTER automatically.
        if(event.keyCode==KeyEvent.KEYCODE_BUTTON_A) {
            val centered=KeyEvent(event.downTime,event.eventTime,
                event.action,KeyEvent.KEYCODE_DPAD_CENTER,event.repeatCount,
                event.metaState,event.deviceId,event.scanCode,
                event.flags,event.source)
            if(super.dispatchKeyEvent(centered))return true
        }
        // Native Compose focus navigation and focused slider receive D-pad
        // first. Only run fallback focus search if not consumed.
        if(super.dispatchKeyEvent(event))return true
        if(event.action==KeyEvent.ACTION_DOWN) {
            val dir=when(event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> 1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 2
                KeyEvent.KEYCODE_DPAD_UP -> 3
                KeyEvent.KEYCODE_DPAD_DOWN -> 4
                else -> 0
            }
            if(dir!=0)return controllerFocusAction?.invoke(dir)==true
        }
        return false
    }

    override fun onGenericMotionEvent(event:MotionEvent):Boolean {
        if(event.action==MotionEvent.ACTION_MOVE &&
            (event.source and InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK) {
            val now=SystemClock.uptimeMillis()
            val hatX=event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY=event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            // Hardware right stick: Z (horizontal) + RZ (vertical).
            // Fallback for controller firmwares exposing RX/RY instead.
            val z=event.getAxisValue(MotionEvent.AXIS_Z)
            val rz=event.getAxisValue(MotionEvent.AXIS_RZ)
            val rx=event.getAxisValue(MotionEvent.AXIS_RX)
            val ry=event.getAxisValue(MotionEvent.AXIS_RY)
            if((kotlin.math.abs(hatX)>.55f ||
                    kotlin.math.abs(hatY)>.55f) &&
                    now-lastAnalogFocusMs>=180L) {
                lastAnalogFocusMs=now
                val dir=if(kotlin.math.abs(hatX)>kotlin.math.abs(hatY))
                    if(hatX<0f)1 else 2
                else if(hatY<0f)3 else 4
                if(controllerFocusAction?.invoke(dir)==true)return true
            }
            val x=if(kotlin.math.abs(z)>.18f)z else rx
            val y=if(kotlin.math.abs(rz)>.18f)rz else ry
            // Only scroll a *visible Activity*. The game/HUD never receives
            // fabricated touch events, and no sampler is started here.
            if(kotlin.math.abs(y)>.18f && now-lastAnalogMoveMs>=55L) {
                lastAnalogMoveMs=now
                controllerScrollAction?.let{it(y);return true}
            }
            if(kotlin.math.abs(x)>.18f) {
                if(controllerIsCompare?.invoke()==true &&
                    now-lastAnalogMoveMs>=55L) {
                    lastAnalogMoveMs=now
                    controllerScrubAction?.let{it(x);return true}
                } else if(controllerIsCompare?.invoke()!=true &&
                    kotlin.math.abs(x)>.60f &&
                    now-lastAnalogFocusMs>=180L) {
                    lastAnalogFocusMs=now
                    if(controllerFocusAction?.invoke(if(x<0f)1 else 2)==true)
                        return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun requestQuickSettingsTile(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult("Edit Quick Settings and add CoreMetrics Record manually")
            return
        }
        try {
            val statusBar = getSystemService(StatusBarManager::class.java)
            statusBar?.requestAddTileService(
                ComponentName(this, SessionTile::class.java),
                "CoreMetrics REC",
                Icon.createWithResource(this, com.locutus5382.coremetrics.R.drawable.ic_qs_cm_rec_off),
                mainExecutor
            ) { result ->
                onResult(when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "CoreMetrics Record tile added"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "Tile is already in Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        "Tile not added; edit Quick Settings manually if preferred"
                    else -> "System could not add tile (code " + result + ")"
                })
            } ?: onResult("Quick Settings manager unavailable")
        } catch (e: Exception) {
            onResult("Quick Settings request failed: " + e.javaClass.simpleName)
        }
    }

    private fun requestHudQuickSettingsTile(onResult:(String)->Unit) {
        if(Build.VERSION.SDK_INT<33) {
            onResult("Edit Quick Settings and add CM HUD manually")
            return
        }
        try {
            val bar=getSystemService(StatusBarManager::class.java)
            bar?.requestAddTileService(
                ComponentName(this,HudTile::class.java),
                "CoreMetrics HUD",
                Icon.createWithResource(this,
                    com.locutus5382.coremetrics.R.drawable.ic_qs_cm_off),
                mainExecutor
            ){ result->
                onResult(when(result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED->
                        "HUD tile added · toggles standby, not REC"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED->
                        "HUD tile is already installed"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED->
                        "Edit Android Quick Settings to add HUD manually"
                    else->"Tile request returned "+result
                })
            }?:onResult("Quick Settings manager unavailable")
        }catch(e:Exception){
            onResult("HUD tile request failed: "+e.javaClass.simpleName)
        }
    }

    private fun query(): ScreenStatus = try {
        // Do not start a second process merely to paint an idle UI page.
        // ActivityManager is allowed to report null; in that case fall back
        // to the provider rather than falsely claiming the recorder is idle.
        val processes=getSystemService(ActivityManager::class.java)
            ?.runningAppProcesses
        if(processes!=null && processes.none {
                it.processName=="$packageName:monitor"
            }) {
            val recoverable=RecorderLease.recover(this)!=null
            ScreenStatus(known=true,pendingRecovery=recoverable,
                message=if(recoverable)"Previous session available for recovery"
                    else "Idle")
        } else {
            val uri = Uri.parse("content://" + packageName + ".monitor.status")
        val v = contentResolver.call(uri, "status", null, null)
        if (v == null) ScreenStatus(known=false,message = "Recorder status unavailable")
        else ScreenStatus(
            known=true,
            running = v.getBoolean("running", false),
            starting = v.getBoolean("starting", false),
            standby = v.getBoolean("standby", false),
            pendingRecovery=v.getBoolean("pendingRecovery",false),
            sessionId = v.getLong("id", 0), count = v.getLong("count", 0),
            message = v.getString("message") ?: "Idle",
            cpu = v.getString("cpu"), gpu = v.getString("gpu"),
            cpuLoad = v.getString("cpuLoad"), gpuLoad = v.getString("gpuLoad"),
            peakCore = v.getString("peakCore"), ram = v.getString("ram"),
            clock = v.getString("clock"), app = v.getString("app"),
            power = v.getString("power"), powerStatus = v.getString("powerStatus"),
            powerSource = v.getString("powerSource"), fps = v.getString("fps"),
            frameMs = v.getString("frameMs"),fpsStatus=v.getString("fpsStatus"),
            observedLimits = v.getString("observedLimits"),
            coreLoads = v.getString("coreLoads"),
            hudStatus = v.getString("hudStatus"),
            hudLocked = v.getBoolean("hudLocked",false),
            hudVisible = v.getBoolean("hudVisible",false),
            hudOptionRevision = v.getLong("hudOptionRevision",0L),
            hudChanged = if(v.getLong("hudOptionRevision",0L)>0L) HudOptions(
                opacity=v.getFloat("hudOptOpacity",0.57f),
                size=v.getString("hudOptSize")?:"regular",
                layout=v.getString("hudOptLayout")?:"pill",
                indicatorColor=v.getString("hudOptIndicator")?:"red",
                blink=v.getBoolean("hudOptBlink",true),
                showCpuTemp=v.getBoolean("hudOptCpuTemp",true),
                showGpuTemp=v.getBoolean("hudOptGpuTemp",true),
                showCpuLoad=v.getBoolean("hudOptCpuLoad",false),
                showGpuLoad=v.getBoolean("hudOptGpuLoad",false),
                showRam=v.getBoolean("hudOptRam",true),
                showPower=v.getBoolean("hudOptPower",true),
                showFps=v.getBoolean("hudOptFps",false),
                showSparkline=false,
                adaptiveColors=v.getBoolean("hudOptAdaptive",false)
            ) else null,
            lastAt = v.getLong("lastSampleAt", 0)
        )
        }
    } catch (e: Exception) { ScreenStatus(known=false,message = "Recorder unreachable: " + e.javaClass.simpleName) }

    // The latest status poll already established whether a user-requested
    // recording/HUD exists. Never synchronously query the :monitor provider from
    // Activity.onStart/onStop: the binder query could block the UI thread and
    // can launch an idle monitor process just to send a visibility signal.
    private var lastObservedMonitorActive=false
    private fun notifyAppVisible(visible:Boolean) {
        if(!lastObservedMonitorActive)return
        // Async status updates must never re-hide the HUD during Activity exit.
        if(visible && (!appVisible || isFinishing || isDestroyed))return
        runCatching {
            startService(Intent(this,RecordingService::class.java).setAction(
                if(visible)RecordingService.APP_FOREGROUND
                else RecordingService.APP_BACKGROUND))
        }
    }
    private var appVisible=false
    override fun onStart() {
        super.onStart()
        appVisible=true
        if(lastObservedMonitorActive)notifyAppVisible(true)
        else lifecycleScope.launch {
            // Reconcile on a worker, never synchronously through a Binder
            // provider in onStart(). Cold Activity recreation must promptly
            // pause its own HUD rather than cover the app for 4.5 seconds.
            val state=withContext(Dispatchers.IO){query()}
            if(appVisible && !isFinishing && !isDestroyed &&
                lifecycle.currentState.isAtLeast(
                    Lifecycle.State.STARTED) &&
                (state.running || state.starting || state.standby)){
                lastObservedMonitorActive=true
                notifyAppVisible(true)
            }
        }
    }
    override fun onStop() {
        appVisible=false
        notifyAppVisible(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("coremetrics_settings", MODE_PRIVATE)
        // SQLite helper survives UI recreation; no close-while-exporting race.
        val database = UiSessionStoreOwner.get(applicationContext)
        setContent {
            // One app, all four finishes. Existing stored preferences stay valid.
            var finish by remember {
                mutableStateOf(SurfaceFinish.parse(
                    prefs.getString("ui_surface_finish","frosted")))
            }
            val appliedFinish=finish
            var immersive by rememberSaveable {
                mutableStateOf(prefs.getBoolean("immersive_ui",false))
            }
            LaunchedEffect(immersive) {
                val controller=window.insetsController
                if(immersive) {
                    controller?.systemBarsBehavior=
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller?.hide(WindowInsets.Type.systemBars())
                } else controller?.show(WindowInsets.Type.systemBars())
            }
            DisposableEffect(Unit) {
                onDispose {
                    window.insetsController?.show(WindowInsets.Type.systemBars())
                }
            }
            var page by rememberSaveable {
                mutableStateOf(if(intent?.action==TileService.ACTION_QS_TILE_PREFERENCES)
                    "HUD" else "Live")
            }
            var compareCursor by rememberSaveable { mutableFloatStateOf(.5f) }
            val contentScroll=rememberScrollState()
            val scope=rememberCoroutineScope()
            // Device-reported capacity, read once for the display gauge.
            // Do not hard-code a Portal-specific (or decimal GB) memory ceiling.
            val ramTotalGiB=remember {
                runCatching {
                    val info=ActivityManager.MemoryInfo()
                    getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
                    info.totalMem.takeIf{it>0L}?.toDouble()?.div(1_073_741_824.0)
                }.getOrNull()
            }
            val firstNavFocus=remember {FocusRequester()}
            LaunchedEffect(page) {contentScroll.scrollTo(0)}
            val focusManager=LocalFocusManager.current
            SideEffect {
                controllerTabAction={ delta ->
                    page=pages[Math.floorMod(pages.indexOf(page)+delta,pages.size)]
                }
                controllerFocusAction={ dir ->
                    focusManager.moveFocus(when(dir) {
                        1 -> FocusDirection.Left
                        2 -> FocusDirection.Right
                        3 -> FocusDirection.Up
                        else -> FocusDirection.Down
                    })
                }
                controllerIsCompare={page=="Compare"}
                controllerScrollAction={value ->
                    val target=(contentScroll.value+value*60f).toInt()
                        .coerceIn(0,contentScroll.maxValue)
                    scope.launch{contentScroll.scrollTo(target)}
                }
                controllerScrubAction={value ->
                    compareCursor=(compareCursor+value*.025f).coerceIn(0f,1f)
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    controllerTabAction=null
                    controllerFocusAction=null
                    controllerScrollAction=null
                    controllerScrubAction=null
                    controllerIsCompare=null
                }
            }
            // Focus a real clickable control, not the previous non-clickable
            // focusable Box which could trap D-pad input.
            LaunchedEffect(Unit) {firstNavFocus.requestFocus()}

            var status by remember { mutableStateOf(ScreenStatus()) }
            // REC may be initiated from this already-visible Activity.
            LaunchedEffect(status.running,status.starting,status.standby) {
                val active=status.running||status.starting||status.standby
                lastObservedMonitorActive=active
                if(appVisible && active)notifyAppVisible(true)
            }
            var overlayAllowed by remember {
                mutableStateOf(Settings.canDrawOverlays(this@MainActivity))
            }
            val liveTrace = remember { mutableStateListOf<LiveTracePoint>() }
            val thermalGauge = remember { ThermalTrendFilter() }
            var cpuEma by remember { mutableStateOf<Double?>(null) }
            var cpuPeak by remember { mutableStateOf<Double?>(null) }
            var cpuTrend by remember {
                mutableStateOf(ThermalTrendFilter.Direction.UNKNOWN)
            }
            var diagnosticReport by remember { mutableStateOf("") }
            var label by rememberSaveable {
                mutableStateOf((prefs.getString("label","")?:"")
                    .takeUnless{it=="NFS benchmark" || it=="Gameplay"} ?: "")
            }
            var configuration by rememberSaveable {
                mutableStateOf(when(prefs.getString("configuration","Stock")) {
                    "Stock" -> "Stock"
                    "Custom" -> "Custom"
                    else -> "Underclocking"
                })
            }
            var interval by rememberSaveable { mutableLongStateOf(
                if (prefs.getLong("interval_ms", 5000L) == 2000L) 2000L else 5000L
            ) }
            var hudEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean("hud_enabled", false))
            }
            var hudOptions by remember { mutableStateOf(HudOptions.from(this@MainActivity)) }
            var message by remember { mutableStateOf("") }
            var sessionList by remember { mutableStateOf(emptyList<SessionStore.Session>()) }
            var linkedPairs by remember {
                mutableStateOf(emptyList<SessionStore.Link>())
            }
            var detailedId by rememberSaveable { mutableLongStateOf(0L) }
            var detailReport by remember {
                mutableStateOf(null as BenchmarkAnalyzer.Report?)
            }
            var detailPoints by remember {
                mutableStateOf(emptyList<SessionStore.Point>())
            }
            var firstId by rememberSaveable { mutableLongStateOf(0L) }
            var secondId by rememberSaveable { mutableLongStateOf(0L) }
            var activeOnly by rememberSaveable { mutableStateOf(true) }
            var skipWarmUp by rememberSaveable { mutableStateOf(false) }
            var detailTechnical by rememberSaveable { mutableStateOf(false) }
            var compareTechnical by rememberSaveable { mutableStateOf(false) }
            var compareFullTimeline by rememberSaveable { mutableStateOf(false) }
            var hideShortRuns by rememberSaveable { mutableStateOf(true) }
            // User controlled. Finishing an Activity never guarantees Android
            // will immediately reclaim its cached UI process/PSS.
            var closeUiOnArm by rememberSaveable {
                mutableStateOf(prefs.getBoolean("close_ui_on_arm",false))
            }
            var metadataEditorId by rememberSaveable { mutableLongStateOf(0L) }
            var firstPoints by remember { mutableStateOf(emptyList<SessionStore.Point>()) }
            var secondPoints by remember { mutableStateOf(emptyList<SessionStore.Point>()) }
            // Every SAF request survives Activity recreation and is consumed
            // exactly once on document selection OR cancellation.
            var exportId by rememberSaveable { mutableLongStateOf(0L) }
            var reportAId by rememberSaveable { mutableLongStateOf(0L) }
            var reportBId by rememberSaveable { mutableLongStateOf(0L) }
            var reportSkipWarmup by rememberSaveable { mutableStateOf(false) }
            var segmentTextRequest by rememberSaveable { mutableStateOf(null as String?) }
            var segmentCsvRequest by rememberSaveable { mutableStateOf(null as String?) }
            var abWindowExport by rememberSaveable { mutableStateOf(null as String?) }
            // Persist pending SAF intent across configuration/Activity recreation.
            var pngRequest by rememberSaveable { mutableStateOf(null as String?) }
            val notifyPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                message = if (granted) "Notifications allowed. Tap Start to record."
                    else "Notifications required for explicit recording."
            }
            val exportFile = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->
                val id=exportId
                exportId=0L
                if (uri != null && id != 0L) scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                database.exportCsv(id, it)
                            } ?: error("Document cannot be opened")
                        }
                    }
                    message = result.fold(onSuccess = { "CSV exported successfully" },
                        onFailure = { "Export failed: " + (it.message ?: it.javaClass.simpleName) })
                }
            }
            val reportFile=rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                val idA=reportAId
                val idB=reportBId
                val warmup=reportSkipWarmup
                reportAId=0L;reportBId=0L;reportSkipWarmup=false
                if(uri!=null && idA!=0L)scope.launch {
                    val outcome=withContext(Dispatchers.IO) {
                        runCatching {
                            val a=database.sessionById(idA)
                                ?:error("Report session A is missing")
                            val reportPoints=database.points(a.id)
                            val analyzedA=BenchmarkAnalyzer.analyze(a,reportPoints,if(warmup)30 else 0)
                            val content=if(idB!=0L && idB!=a.id) {
                                val b=database.sessionById(idB)
                                    ?:error("Report session B is missing")
                                BenchmarkAnalyzer.comparison(analyzedA,
                                    BenchmarkAnalyzer.analyze(b,database.points(b.id),if(warmup)30 else 0))
                            } else BenchmarkAnalyzer.text(analyzedA) +
                                SessionDiagnosticsAnalyzer.text(
                                    SessionDiagnosticsAnalyzer.analyze(
                                        V08Analytics.afterWarmUp(reportPoints,
                                            if(warmup)30 else 0),analyzedA),
                                    reportPoints.firstOrNull()?.timeMs)
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{
                                it.write(content)
                            } ?:error("Cannot write report")
                        }
                    }
                    message=outcome.fold(onSuccess={"Report exported"},
                        onFailure={"Report failed: "+(it.message?:it.javaClass.simpleName)})
                }
            }
            val segmentTextFile=rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                val request=DocumentWindowRequest.parseSegment(segmentTextRequest)
                segmentTextRequest=null
                if(uri!=null && request!=null)scope.launch {
                    val outcome=withContext(Dispatchers.IO) {
                        runCatching {
                            val session=database.sessionById(request.sessionId)
                                ?:error("Source session no longer exists")
                            val source=database.points(request.sessionId)
                            require(source.size>=session.count) {
                                "The source exceeds the first 12,000 loaded samples"
                            }
                            val selected=SegmentAnalysis.create(session,source,
                                SegmentAnalysis.Range(request.startMs,request.endMs))
                            val content=SegmentAnalysis.textReport(selected)
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{
                                it.write(content)
                            } ?: error("Cannot open segment report destination")
                        }
                    }
                    message=outcome.fold(
                        onSuccess={"Selected-segment report exported"},
                        onFailure={"Segment report failed: "+
                            (it.message?:it.javaClass.simpleName)})
                }
            }
            val segmentCsvFile=rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->
                val request=DocumentWindowRequest.parseSegment(segmentCsvRequest)
                segmentCsvRequest=null
                if(uri!=null && request!=null)scope.launch {
                    val outcome=withContext(Dispatchers.IO) {
                        runCatching {
                            val session=database.sessionById(request.sessionId)
                                ?:error("Source session no longer exists")
                            val source=database.points(request.sessionId)
                            require(source.size>=session.count) {
                                "The source exceeds the first 12,000 loaded samples"
                            }
                            val selected=SegmentAnalysis.create(session,source,
                                SegmentAnalysis.Range(request.startMs,request.endMs))
                            require(selected.validForReport){"Selected range has insufficient samples"}
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{
                                database.exportCsvRange(request.sessionId,
                                    request.startMs,request.endMs,it)
                            } ?: error("Cannot open segment CSV destination")
                        }
                    }
                    message=outcome.fold(
                        onSuccess={"Selected-segment CSV exported"},
                        onFailure={"Segment CSV failed: "+
                            (it.message?:it.javaClass.simpleName)})
                }
            }
            val abWindowTextFile=rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                val request=DocumentWindowRequest.parseAb(abWindowExport)
                abWindowExport=null
                if(uri!=null && request!=null)scope.launch {
                    val outcome=withContext(Dispatchers.IO) {
                        runCatching {
                            val sa=database.sessionById(request.firstId)
                                ?:error("Session A has been removed")
                            val sb=database.sessionById(request.secondId)
                                ?:error("Session B has been removed")
                            val pa=database.points(sa.id)
                            val pb=database.points(sb.id)
                            require(pa.size>=sa.count && pb.size>=sb.count) {
                                "Cannot export incompletely loaded recordings (>12k samples)"
                            }
                            val analysis=CompareStudioAnalysis.analyze(
                                sa,pa,sb,pb,CompareStudioAnalysis.Window(
                                    request.startMs,request.endMs))
                            val text=CompareStudioAnalysis.report(analysis)
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{
                                it.write(text)
                            }?:error("Cannot open selected A/B report destination")
                        }
                    }
                    message=outcome.fold(
                        onSuccess={"Selected-window A/B report exported"},
                        onFailure={"A/B export failed: "+
                            (it.message?:it.javaClass.simpleName)})
                }
            }
            // Android Storage Access Framework: user explicitly chooses a PNG
            // destination. Render offline on IO dispatcher after that choice.
            // Share ONLY that user-owned document URI, with temporary read grant;
            // never use private paths, auto-capture the screen or contact network.
            val pngDocument=rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("image/png")
            ) { uri ->
                val request=ScorecardExportRequest.decode(pngRequest)
                pngRequest=null
                if(uri!=null && request!=null)scope.launch {
                    val outcome=try {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val a=database.sessionById(request.firstId)
                                ?:error("Source session no longer exists")
                            val pointsA=database.points(a.id)
                            require(pointsA.size>=a.count) {
                                "Source session exceeds 12,000 loaded samples"
                            }
                            val spec=if(request.secondId==0L) {
                                val bundle=SegmentAnalysis.create(a,pointsA,
                                    SegmentAnalysis.Range(
                                        request.startMs,request.endMs))
                                ScorecardModel.single(bundle)
                            }else {
                                val b=database.sessionById(request.secondId)
                                    ?:error("Comparison session B no longer exists")
                                val pointsB=database.points(b.id)
                                require(pointsB.size>=b.count) {
                                    "Comparison session exceeds 12,000 loaded samples"
                                }
                                val report=CompareStudioAnalysis.analyze(
                                    a,pointsA,b,pointsB,
                                    CompareStudioAnalysis.Window(
                                        request.startMs,request.endMs))
                                ScorecardModel.comparison(report)
                            }
                            contentResolver.openOutputStream(uri,"w")?.use { out ->
                                ScorecardPngRenderer.write(
                                    spec,SurfaceFinish.parse(request.styleKey),out)
                            }?:error("Cannot open PNG destination")
                            }
                        }
                    } catch(e:kotlin.coroutines.cancellation.CancellationException) {
                        // Activity rotation or cancellation can happen while the
                        // document provider already owns a partially written URI.
                        // Cleanup must run even when the UI coroutine is cancelled.
                        withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO) {
                            runCatching {contentResolver.delete(uri,null,null)}
                        }
                        throw e
                    }
                    // SAF document may already exist even if encode/write fails.
                    // Best effort: remove only the just-created selected URI.
                    val removedPartial=if(outcome.isFailure)withContext(Dispatchers.IO) {
                        runCatching {contentResolver.delete(uri,null,null)>0}
                            .getOrDefault(false)
                    }else true
                    outcome.fold(onSuccess={
                        message="PNG scorecard saved (1600 × 1050, original evidence preserved)"
                        if(request.shareAfterSave) {
                            runCatching {
                                val shareIntent=Intent(Intent.ACTION_SEND).apply {
                                    type="image/png"
                                    putExtra(Intent.EXTRA_STREAM,uri)
                                    clipData=ClipData.newUri(
                                        contentResolver,"CoreMetrics PNG scorecard",uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(
                                    shareIntent,"Share CoreMetrics scorecard"))
                            }.onFailure {
                                message="PNG saved; share sheet could not open: "+
                                    (it.message?:it.javaClass.simpleName)
                            }
                        }
                    },onFailure={
                        message="PNG export failed: "+
                            (it.message?:it.javaClass.simpleName)+
                            (if(removedPartial)"" else
                                " · Cannot remove incomplete file; check chosen folder")
                    })
                }
            }
            val importFile=rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if(uri!=null) scope.launch {
                    val result=withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use {
                                database.importCsv(it)
                            } ?: error("Cannot open selected CSV")
                        }
                    }
                    result.fold(onSuccess={ id ->
                        message="Imported as session #"+id
                        sessionList=withContext(Dispatchers.IO){database.list()}
                        firstId=id
                        page="Compare"
                    }, onFailure={ e ->
                        message="Import failed without partial save: "+
                            (e.message ?: e.javaClass.simpleName)
                    })
                }
            }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) {
                        val next = withContext(Dispatchers.IO) { query() }
                        if (next.lastAt > 0L && next.lastAt != status.lastAt &&
                            next.running) {
                            val smoothed=thermalGauge.update(next.lastAt,
                                next.cpu?.removeSuffix("°C")?.toDoubleOrNull())
                            cpuEma=smoothed.smoothedC
                            cpuPeak=smoothed.rawPeakC
                            cpuTrend=smoothed.direction
                            liveTrace.add(LiveTracePoint(next.lastAt,
                                next.cpu?.removeSuffix("°C")?.toDoubleOrNull(),
                                next.gpu?.removeSuffix("°C")?.toDoubleOrNull(),
                                next.cpuLoad?.toDoubleOrNull(),
                                next.gpuLoad?.toDoubleOrNull()))
                            while (liveTrace.size > 70) liveTrace.removeAt(0)
                        }
                        if(!next.running && status.running) {
                            thermalGauge.reset()
                            cpuEma=null
                            cpuPeak=null
                            cpuTrend=ThermalTrendFilter.Direction.UNKNOWN
                        }
                        if(next.hudOptionRevision>status.hudOptionRevision &&
                            next.hudChanged!=null) {
                            // Synchronize preferences across Android processes without
                            // a HUD_CONFIG echo and without overwriting drawer changes.
                            hudOptions=next.hudChanged
                            hudOptions.save(this@MainActivity)
                        }
                        status = next
                        lastObservedMonitorActive=
                            next.running||next.starting||next.standby
                        overlayAllowed=Settings.canDrawOverlays(this@MainActivity)
                        // Only the Live/HUD screens need responsive telemetry.
                        // Sessions, Compare and Settings avoid unnecessary Binder
                        // traffic and Compose recompositions during inspection.
                        val nextPollMs=when {
                            !lastObservedMonitorActive->8_000L
                            page=="Live" || page=="HUD"->1_800L
                            else->4_500L
                        }
                        delay(nextPollMs)
                    }
                }
            }
            // Drop cached 12k-sample analysis lists once their page is closed.
            // Reopening a page loads original data from the local database.
            LaunchedEffect(page) {
                if(page!="Sessions"){
                    detailReport=null
                    detailPoints=emptyList()
                    detailedId=0L
                    detailTechnical=false
                    metadataEditorId=0L
                }
                if(page!="Compare"){
                    firstPoints=emptyList()
                    secondPoints=emptyList()
                    compareTechnical=false
                }
            }
            LaunchedEffect(page, status.running) {
                if(page=="Live" && !status.running)
                    sessionList=withContext(Dispatchers.IO){database.list(1)}
                if (page == "Sessions" || page == "Compare") {
                    sessionList = withContext(Dispatchers.IO) { database.list() }
                    if(page=="Compare")
                        linkedPairs=withContext(Dispatchers.IO){database.links()}
                }
            }
            LaunchedEffect(page,detailedId,skipWarmUp,sessionList) {
                if(page=="Sessions" && detailedId>0L) {
                    val response=withContext(Dispatchers.IO) {
                        val session=database.sessionById(detailedId)
                        val points=database.points(detailedId)
                        val report=session?.let{BenchmarkAnalyzer.analyze(it,points,if(skipWarmUp)30 else 0)}
                        report to points
                    }
                    detailReport=response.first
                    detailPoints=response.second
                }
            }
            LaunchedEffect(page, firstId, secondId) {
                if (page == "Compare") {
                    val result = withContext(Dispatchers.IO) {
                        val a = if (firstId != 0L) database.points(firstId) else emptyList()
                        val b = if (secondId != 0L) database.points(secondId) else emptyList()
                        a to b
                    }
                    firstPoints = result.first
                    secondPoints = result.second
                }
            }

            fun persistHud(updated: HudOptions) {
                hudOptions=updated
                updated.save(this@MainActivity)
                if(status.running || status.starting || status.standby) {
                    val i=HudOptions.addExtras(
                        Intent(this@MainActivity,RecordingService::class.java)
                            .setAction(RecordingService.HUD_CONFIG),updated)
                    runCatching{startService(i)}.onFailure{
                        message="HUD change failed: "+it.javaClass.simpleName
                    }
                }
            }
            fun armHud() {
                if(!Settings.canDrawOverlays(this@MainActivity)) {
                    message="Grant overlay permission, then tap Launch standby again."
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+packageName)))
                    return
                }
                if(Build.VERSION.SDK_INT>=33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED) {
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    message="Allow notifications, then tap Launch standby again."
                    return
                }
                if(!status.known || status.pendingRecovery){
                    runCatching{
                        startForegroundService(Intent(this@MainActivity,
                            RecordingService::class.java)
                            .setAction(RecordingService.RECOVER))
                    }.fold(onSuccess={
                        message="Trying to recover a previously started session"
                    },onFailure={
                        message="Recovery request failed: "+it.javaClass.simpleName
                    })
                    return
                }
                // Commit metadata before :monitor process sees the HUD click.
                prefs.edit().putString("label",label.trim())
                    .putString("configuration",configuration)
                    .putLong("interval_ms",interval).commit()
                hudOptions.save(this@MainActivity)
                val i=HudOptions.addExtras(
                    Intent(this@MainActivity,RecordingService::class.java)
                        .setAction(RecordingService.ARM)
                        .putExtra(RecordingService.LABEL,label.trim().ifBlank{"Gameplay"})
                        .putExtra(RecordingService.CONFIG,configuration)
                        .putExtra(RecordingService.INTERVAL,interval),hudOptions)
                runCatching{startForegroundService(i)}.fold(
                    onSuccess={
                        lastObservedMonitorActive=true
                        message="Standby requested · no recording until REC"
                        if(closeUiOnArm) {
                            appVisible=false
                            notifyAppVisible(false)
                            this@MainActivity.finish()
                        }
                    },
                    onFailure={message="Standby failed: "+it.javaClass.simpleName})
            }
            fun unlockHud() {
                runCatching { startService(Intent(this@MainActivity,
                    RecordingService::class.java).setAction(RecordingService.HUD_UNLOCK)) }
                    .onFailure { message="Unlock failed: "+it.javaClass.simpleName }
            }
            fun closeHud() {
                runCatching{startService(Intent(this@MainActivity,
                    RecordingService::class.java).setAction(RecordingService.DISARM))}
                    .onFailure{message="Close failed: "+it.javaClass.simpleName}
            }
            CompositionLocalProvider(LocalSurfaceFinish provides appliedFinish) {
            val themePalette=appliedFinish.palette
            val rootBackground=when(appliedFinish) {
                SurfaceFinish.FROSTED -> Brush.linearGradient(listOf(
                    cobaltNavy,Color(0xFF09173A),Color(0xFF10265C),cobaltNavy))
                SurfaceFinish.CRYSTAL_AERO -> Brush.linearGradient(listOf(
                    Color(0xFF030B1D),Color(0xFF123C68),Color(0xFF0C2858),
                    Color(0xFF0C132F),Color(0xFF030815)))
                SurfaceFinish.SOLAR_PULSE -> Brush.linearGradient(listOf(
                    cobaltNavy,Color(0xFF07192F),Color(0xFF081D43),cobaltNavy))
                SurfaceFinish.COBALT_MATRIX -> Brush.linearGradient(listOf(
                    Color(0xFF030814),Color(0xFF071426),Color(0xFF030814)))
            }
            val displayColorScheme=if(appliedFinish==SurfaceFinish.FROSTED)
                // Exact pre-v0.9.3 Frosted Material3 colors and defaults.
                darkColorScheme(primary=iceBlue,onPrimary=cobaltNavy,
                    secondary=coreCyan,surface=cobaltNavy,
                    background=cobaltNavy,onSurface=coreText)
            else darkColorScheme(
                primary=when(appliedFinish) {
                    SurfaceFinish.SOLAR_PULSE -> solarOrange
                    SurfaceFinish.COBALT_MATRIX -> Color(0xFF75DBFF)
                    SurfaceFinish.CRYSTAL_AERO -> Color(0xFFB9ECFF)
                    else -> iceBlue
                },
                onPrimary=if(appliedFinish==SurfaceFinish.SOLAR_PULSE)
                    Color.White else cobaltNavy,
                primaryContainer=if(appliedFinish==SurfaceFinish.SOLAR_PULSE)
                    Color(0xFF8A3A0C) else Color(0xFF183E70),
                onPrimaryContainer=themePalette.foreground,
                secondary=when(appliedFinish) {
                    SurfaceFinish.SOLAR_PULSE -> Color(0xFF62D7ED)
                    SurfaceFinish.COBALT_MATRIX -> solarGold
                    else -> coreCyan
                },
                onSecondary=Color(0xFF07152B),
                surface=when(appliedFinish) {
                    SurfaceFinish.COBALT_MATRIX -> Color(0xFF101A29)
                    SurfaceFinish.SOLAR_PULSE -> Color(0xFF132747)
                    SurfaceFinish.CRYSTAL_AERO -> Color(0xFF112E51)
                    else -> cobaltNavy
                },
                surfaceVariant=themePalette.top,
                background=if(appliedFinish==SurfaceFinish.COBALT_MATRIX)
                    Color(0xFF030814) else cobaltNavy,
                onSurface=themePalette.foreground,
                onSurfaceVariant=themePalette.muted,
                outline=themePalette.borderSoft
            )
            MaterialTheme(
                colorScheme=displayColorScheme,
                shapes=if(appliedFinish==SurfaceFinish.FROSTED)
                    androidx.compose.material3.Shapes()
                else androidx.compose.material3.Shapes(
                    small=RoundedCornerShape(
                        if(themePalette.sharpCorners)4.dp else 10.dp),
                    medium=RoundedCornerShape(
                        if(themePalette.sharpCorners)7.dp else 15.dp),
                    large=RoundedCornerShape(
                        if(themePalette.sharpCorners)9.dp else 23.dp)
                )
            ) {
                Box(Modifier.fillMaxSize().background(rootBackground)) {
                    PureGlassBackdrop()
                    Row(Modifier.fillMaxSize().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Compact, icon-led rail fits four destinations even on
                        // the Portal's 360dp landscape configuration. Scroll is
                        // still available when system font scaling is increased.
                        Column(
                            Modifier.width(84.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(15.dp))
                                .pureGlass(15)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal=5.dp, vertical=6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("CM",color=coreCyan,
                                style=MaterialTheme.typography.titleMedium,
                                fontWeight=FontWeight.Black)
                            Text("CORE",color=coreSecondary, fontSize=9.sp,
                                fontWeight=FontWeight.Bold)
                            pages.forEach { item ->
                                CompactRailAction(item,railIcon(item),page==item,
                                    modifier=if(item=="Live")
                                        Modifier.focusRequester(firstNavFocus)
                                    else Modifier) { page=item }
                            }
                            HorizontalDivider(color=coreBorder)
                            Text(if(status.running) "● REC"
                                else if(status.starting) "START"
                                else if(status.standby) "READY"
                                else if(status.pendingRecovery) "RECOVER"
                                else if(!status.known) "UNKNOWN"
                                else "IDLE",color=if(status.running) solarOrange
                                    else if(status.standby || status.pendingRecovery)
                                        solarGold else muted,
                                style=MaterialTheme.typography.labelSmall,
                                fontWeight=FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .pureGlass(14)
                                    .padding(horizontal=14.dp,vertical=10.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(if(appliedFinish==SurfaceFinish.FROSTED)
                                        "COREMETRICS  /  COBALT & SOLAR GLASS"
                                    else "COREMETRICS  /  "+appliedFinish.displayName.uppercase(),
                                        color=iceBlue,
                                        style=MaterialTheme.typography.labelSmall)
                                    Text(page.uppercase(Locale.US),color=coreText,
                                        style=MaterialTheme.typography.titleLarge,
                                        fontWeight=FontWeight.Bold)
                                }
                                CMActionPill("STYLE ↻") {
                                    val choices=SurfaceFinish.values()
                                    val next=choices[(finish.ordinal+1)%choices.size]
                                    finish=next
                                    prefs.edit().putString("ui_surface_finish",next.key).apply()
                                }
                                if(page=="Live") {
                                    CMActionPill(if(status.standby||status.running)"HUD"
                                        else "ARM HUD",primary=true) {
                                        if(status.standby||status.running)page="HUD"
                                        else armHud()
                                    }
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(9.dp))
                                        .background(if(status.running) Color(0xBB19473F)
                                            else Color(0xBB263343))
                                        .border(1.dp,if(status.running) coreMint.copy(alpha=.60f)
                                            else coreBorder,RoundedCornerShape(9.dp))
                                        .padding(horizontal=11.dp,vertical=7.dp)
                                ) {
                                    Text(if(status.running) "● REC  " + status.count + " samples"
                                        else if(status.starting) "◌ STARTING"
                                        else if(status.standby) "◌ HUD READY · tap REC"
                                        else if(status.pendingRecovery) "◌ RECOVER EXISTING SESSION"
                                        else if(!status.known) "◌ RECORDER STATUS UNKNOWN"
                                        else status.message.take(52),
                                        color=if(status.running) coreMint else coreSecondary,
                                        style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            Column(Modifier.weight(1f).verticalScroll(contentScroll),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (page) {
                                    "Live" -> {
                                        Text("Eden first → Quick Settings → CM HUD → REC. " +
                                            "Standby performs no hardware sampling.",
                                            color=coreSecondary,
                                            style=MaterialTheme.typography.labelSmall)
                                        if(!status.running && !status.starting &&
                                            !status.standby && !status.pendingRecovery &&
                                            status.known) {
                                            GlassPanel {
                                                Text("READY FOR YOUR NEXT SESSION",
                                                    color=iceBlue,fontWeight=FontWeight.Bold)
                                                Text("Launch Eden first, then use CM HUD in Quick "+
                                                    "Settings to show the floating controls. "+
                                                    "The recorder remains idle until REC.",
                                                    color=coreSecondary,
                                                    style=MaterialTheme.typography.bodySmall)
                                                val previous=sessionList.firstOrNull()
                                                if(previous!=null) {
                                                    HorizontalDivider(color=coreBorder)
                                                    Text("LAST RECORDING · SESSION "+previous.id,
                                                        color=solarGold,
                                                        fontWeight=FontWeight.Bold)
                                                    Text(previous.configuration+" · "+
                                                        previous.status+" · "+
                                                        previous.count+" samples",
                                                        color=coreText)
                                                    OutlinedButton(onClick={
                                                        detailedId=previous.id
                                                        page="Sessions"
                                                    }){Text("Analyze last session")}
                                                }else Text("No recordings saved yet.",
                                                    color=coreSecondary)
                                            }
                                        }else {
                                        val powerState=when {
                                            status.powerStatus?.startsWith("DISCHARGING")==true ->
                                                "Discharging"
                                            status.powerStatus?.startsWith(
                                                "CHARGING_OR_EXTERNAL_POWER")==true ||
                                            status.powerStatus?.startsWith("FULL")==true ->
                                                "Plugged in"
                                            status.powerStatus==null -> null
                                            else -> "Unknown"
                                        }
                                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                            val cpuDisplay=cpuEma?.let {
                                                String.format(Locale.US,"%.1f°C",it)
                                            } ?: status.cpu
                                            PureGlassGauge("CPU",cpuDisplay,
                                                (((cpuDisplay?.removeSuffix("°C")
                                                    ?.toDoubleOrNull() ?: 25.0)-25.0)/65.0).toFloat(),
                                                solarOrange,Modifier.weight(1f),
                                                subtitle=status.cpuLoad?.let{"Load $it%"}
                                                    ?:"Load unavailable")
                                            PureGlassGauge("GPU",status.gpu,
                                                (((status.gpu?.removeSuffix("°C")
                                                    ?.toDoubleOrNull() ?: 25.0)-25.0)/65.0).toFloat(),
                                                solarGold,Modifier.weight(1f),
                                                subtitle=status.gpuLoad?.let{"Load $it%"}
                                                    ?:"Load unavailable")
                                            PureGlassGauge("Battery W",status.power,
                                                ((status.power?.removeSuffix(" W")
                                                    ?.toDoubleOrNull() ?: 0.0)/12.0).toFloat(),
                                                solarGold,Modifier.weight(1f),
                                                subtitle=powerState?:"Battery state unavailable")
                                            val usedRamGiB=status.ram
                                                ?.removeSuffix(" GiB")?.toDoubleOrNull()
                                            PureGlassGauge("RAM GiB",usedRamGiB?.let{
                                                String.format(Locale.US,"%.2f",it)
                                            },if(usedRamGiB!=null && ramTotalGiB!=null &&
                                                ramTotalGiB>0.0)
                                                (usedRamGiB/ramTotalGiB).toFloat()
                                            else 0f,
                                                iceBlue,Modifier.weight(1f),
                                                subtitle=ramTotalGiB?.let{
                                                    String.format(Locale.US,"of %.1f GiB",it)
                                                }?:"System RAM")
                                        }
                                        Text("CPU EMA · raw peak " +
                                            (cpuPeak?.let { fmt(it)+"°C" } ?: "—") +
                                            " · " + when(cpuTrend) {
                                                ThermalTrendFilter.Direction.RISING -> "▲ rising"
                                                ThermalTrendFilter.Direction.FALLING -> "▼ falling"
                                                ThermalTrendFilter.Direction.STABLE -> "— stable"
                                                else -> "—"
                                            },color=coreSecondary,
                                            style=MaterialTheme.typography.labelSmall)
                                        Text("Gauge arcs are reference scales, not safety limits.",
                                            color=coreSecondary,
                                            style=MaterialTheme.typography.labelSmall)
                                        if(status.lastAt>0) {
                                            Text("Last sample: "+
                                                java.text.DateFormat.getTimeInstance(
                                                    java.text.DateFormat.MEDIUM)
                                                    .format(java.util.Date(status.lastAt)),
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.labelSmall)
                                        }
                                        LiveScope(liveTrace)
                                        PerCoreLoadMatrix(status.coreLoads)
                                        }
                                        GlassPanel {
                                            Text("RECORDING CONTROL  ·  ONE USER-STARTED SESSION",
                                                color = coreCyan, style = MaterialTheme.typography.titleMedium)
                                            Text("Optional game / track note", color = muted)
                                            OutlinedTextField(label, { label = it.take(64) },
                                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                label = { Text("Optional game / track note") })
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("Stock", "Underclocking", "Custom").forEach { item ->
                                                    FilterChip(selected = configuration == item,
                                                        onClick = { configuration = item },
                                                        label = { Text(item) })
                                                }
                                            }
                                            Text("Sample interval", color = muted)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(2000L,5000L).forEach { ms ->
                                                    FilterChip(selected = interval == ms,
                                                        onClick = { interval = ms },
                                                        label = { Text((ms / 1000).toString() + " seconds") })
                                                }
                                            }
                                            Text("Safety mode: 2 or 5 second samples. " +
                                                "Use 5 s for Eden launch testing.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.labelSmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(onClick = {
                                                    if (!status.running && !status.starting &&
                                                        Build.VERSION.SDK_INT >= 33 &&
                                                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                                        PackageManager.PERMISSION_GRANTED) {
                                                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    } else {
                                                        prefs.edit().putString("label", label.trim())
                                                            .putString("configuration", configuration)
                                                            .putLong("interval_ms", interval).apply()
                                                        try {
                                                            val svc = Intent(this@MainActivity, RecordingService::class.java)
                                                            if (status.running || status.starting) {
                                                                startService(svc.setAction(RecordingService.STOP))
                                                                message = "Stop requested"
                                                            } else if(!status.known || status.pendingRecovery) {
                                                                startForegroundService(
                                                                    svc.setAction(RecordingService.RECOVER))
                                                                lastObservedMonitorActive=true
                                                                message="Trying to recover the existing recorder"
                                                            } else {
                                                                startForegroundService(svc.setAction(RecordingService.START)
                                                                    .putExtra(RecordingService.LABEL,label.trim())
                                                                    .putExtra(RecordingService.CONFIG,configuration)
                                                                    .putExtra(RecordingService.INTERVAL,interval))
                                                                lastObservedMonitorActive=true
                                                                message = "Start requested"
                                                            }
                                                        } catch (e: Exception) {
                                                            message = "Action failed: " + e.javaClass.simpleName
                                                        }
                                                    }
                                                }) {
                                                    Text(if(status.running || status.starting)
                                                        "STOP AND SAVE"
                                                        else if(status.pendingRecovery)
                                                            "RECOVER EXISTING SESSION"
                                                        else if(!status.known)
                                                            "CHECK RECORDER STATUS"
                                                        else "START RECORDING")
                                                }
                                                OutlinedButton(onClick = { page = "Sessions" }) {
                                                    Text("Sessions")
                                                }
                                                OutlinedButton(onClick = {
                                                    requestQuickSettingsTile { message=it }
                                                }) { Text("Add QS tile") }
                                            }
                                        }

                                        GlassPanel {
                                            Text("ACTUAL CPU CLOCKS", color = coreCyan)
                                            Text(status.clock ?: "Unavailable", color = white)
                                            Text("OBSERVED LIMITS  ·  not proof of tuner identity",
                                                color = muted,style=MaterialTheme.typography.labelSmall)
                                            Text(status.observedLimits ?: "Unavailable",color=white)
                                            val liveWarning=remember(configuration,status.observedLimits) {
                                                V08Analytics.liveProfileWarning(
                                                    configuration,status.observedLimits)
                                            }
                                            liveWarning?.let { warning ->
                                                Text(warning,color=solarOrange,
                                                    style=MaterialTheme.typography.bodySmall)
                                            }
                                            Text("BATTERY ELECTRICAL MEASUREMENT",color=muted)
                                            Text((status.power ?: "Unavailable") + "  ·  " +
                                                (status.powerStatus ?: "Start to measure"),
                                                color=white,style=MaterialTheme.typography.bodySmall)
                                            Text("FRAME SOURCE",color=muted)
                                            Text(status.fpsStatus ?: "FPS probe disabled",
                                                color=white,style=MaterialTheme.typography.bodySmall)
                                            Text("App package: " + (status.app ?: "Unavailable / Usage Access off"),
                                                color = muted, style = MaterialTheme.typography.bodySmall)
                                            Text("FPS is SurfaceFlinger layer-present rate only; watts are signed battery-net power. No low-percentiles or CPU watts are invented.",
                                                color = muted, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    "Sessions" -> {
                                        GlassPanel {
                                            Text("Recorded sessions", style = MaterialTheme.typography.titleMedium)
                                            Text("Session names are generated automatically. "+
                                                "The game / track note is optional.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick={importFile.launch(arrayOf("*/*"))},
                                                 enabled=!status.running && !status.starting) {
                                                 Text("Import CSV")
                                             }
                                             Text("Only explicit sessions are recorded. Labels are not proof of applied limits.",
                                                color = muted, style = MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick = {
                                                scope.launch { sessionList = withContext(Dispatchers.IO) { database.list() } }
                                            }) { Text("Refresh history") }
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                 Text("Hide runs < 15 s",
                                                     Modifier.weight(1f),color=coreText)
                                                 Switch(hideShortRuns,
                                                     onCheckedChange={hideShortRuns=it})
                                             }
                                             Text("Only filters this list; no recordings are deleted.",
                                                 color=coreSecondary,
                                                 style=MaterialTheme.typography.labelSmall)
                                             if (sessionList.isEmpty()) Text("No sessions yet.", color = muted)
                                            detailReport?.takeIf{detailedId>0L}?.let{r ->
                                                SessionAnalysisStudio(
                                                    session=r.session,
                                                    source=detailPoints,
                                                    initialSkipWarmup=skipWarmUp,
                                                    onExportText={ range ->
                                                        segmentTextRequest=DocumentWindowRequest.segment(
                                                            DocumentWindowRequest.Segment(
                                                                r.session.id,range.startMs,range.endMs))
                                                        segmentTextFile.launch("CoreMetrics_Segment_"+
                                                            r.session.id+"_"+range.startMs+"_"+range.endMs+".txt")
                                                    },
                                                    onExportCsv={ range ->
                                                        segmentCsvRequest=DocumentWindowRequest.segment(
                                                            DocumentWindowRequest.Segment(
                                                                r.session.id,range.startMs,range.endMs))
                                                        segmentCsvFile.launch("CoreMetrics_Segment_"+
                                                            r.session.id+"_"+range.startMs+"_"+range.endMs+".csv")
                                                    },
                                                    onExportPng={ range,share ->
                                                        pngRequest=ScorecardExportRequest(
                                                            firstId=r.session.id,secondId=0L,
                                                            startMs=range.startMs,endMs=range.endMs,
                                                            styleKey=appliedFinish.key,
                                                            shareAfterSave=share).encode()
                                                        pngDocument.launch(
                                                            "CoreMetrics_Session_"+r.session.id+
                                                                "_"+range.startMs+"_"+range.endMs+".png")
                                                    }
                                                ) { selected ->
                                                    OutlinedButton(onClick={
                                                        detailTechnical=!detailTechnical
                                                    }) {
                                                        Text(if(detailTechnical)
                                                            "Hide detailed evidence"
                                                            else "Show energy provenance, CPU limits and classic timeline")
                                                    }
                                                    if(detailTechnical) {
                                                        Row(Modifier.fillMaxWidth(),
                                                            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                                            EnergyRatePanel(selected.benchmark.energy,
                                                                Modifier.weight(1f))
                                                            ObservedProfilePanel(
                                                                r.session.configuration,
                                                                selected.points,
                                                                modifier=Modifier.weight(1f),
                                                                referenceStartMs=detailPoints.firstOrNull()?.timeMs)
                                                        }
                                                        CoreWinnerPanel(selected.points,
                                                            Modifier.fillMaxWidth())
                                                        SessionMetricTimeline(selected.points,
                                                            label="CLASSIC SIGNAL EXPLORER · SELECTED RANGE",
                                                            referenceStartMs=detailPoints.firstOrNull()?.timeMs)
                                                    }
                                                }
                                                OutlinedButton(onClick={
                                                    detailedId=0L
                                                    detailReport=null;detailPoints=emptyList()
                                                }){Text("Close session analysis")}
                                            }
                                            sessionList.filter { s ->
                                                 !hideShortRuns || s.endedAt==null ||
                                                     s.endedAt-s.startedAt>=15_000L
                                             }.forEach { s ->
                                                HorizontalDivider(color = Color(0x335F91A7))
                                                Row(Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("Session " + s.id + " · " + s.configuration, color = white,
                                                            fontWeight = FontWeight.Bold)
                                                        Text(time(s.startedAt) + " · " + s.count + " samples · " + s.status,
                                                            color = muted, style = MaterialTheme.typography.labelSmall)
                                                        s.note?.let{Text("Note: "+it,color=coreSecondary,
                                                            style=MaterialTheme.typography.labelSmall)}
                                                        if(s.hasExternalPower)Text(
                                                            if(s.hasDischargePower)"MIXED POWER" else "PLUGGED IN",
                                                            color=solarOrange,
                                                            style=MaterialTheme.typography.labelSmall)
                                                        Text("Driver: "+(s.graphicsDriver?:"—")+
                                                            " · Scale: "+(s.resolutionScale?:"—"),
                                                            color=coreSecondary,
                                                            style=MaterialTheme.typography.labelSmall)
                                                    }
                                                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp),
                                                        verticalAlignment=Alignment.CenterVertically) {
                                                    OutlinedButton(contentPadding=PaddingValues(horizontal=8.dp,vertical=5.dp),onClick = {
                                                        exportId = s.id
                                                        exportFile.launch("CoreMetrics_" + s.id + ".csv")
                                                    }) { Text("CSV") }
                                                    OutlinedButton(contentPadding=PaddingValues(horizontal=8.dp,vertical=5.dp),onClick={
                                                        detailedId=s.id;page="Sessions"
                                                        scope.launch {contentScroll.scrollTo(0)}
                                                    }){Text("Analyze")}
                                                    OutlinedButton(contentPadding=PaddingValues(horizontal=8.dp,vertical=5.dp),onClick={
                                                        metadataEditorId=if(metadataEditorId==s.id)
                                                            0L else s.id
                                                    },enabled=!status.running && !status.starting){
                                                        Text("Edit")
                                                    }
                                                    OutlinedButton(contentPadding=PaddingValues(horizontal=8.dp,vertical=5.dp),onClick={
                                                        reportAId=s.id;reportBId=0L
                                                        reportFile.launch("CoreMetrics_Report_"+
                                                            s.id+".txt")
                                                    }){Text("Report")}
                                                    }
                                                }
                                                if(metadataEditorId==s.id) {
                                                    SessionMetadataEditor(session=s,
                                                        onDriver={tag->
                                                            scope.launch {
                                                                val outcome=withContext(Dispatchers.IO){
                                                                    runCatching{
                                                                        check(database.updateGraphicsDriver(s.id,tag)){
                                                                            "Session no longer exists"
                                                                        }
                                                                    }
                                                                }
                                                                outcome.fold(onSuccess={
                                                                    sessionList=withContext(Dispatchers.IO){
                                                                        database.list()
                                                                    }
                                                                    message="Graphics driver tag saved"
                                                                },onFailure={e->
                                                                    message="Driver tag failed: "+
                                                                        (e.message?:e.javaClass.simpleName)
                                                                })
                                                            }
                                                        },
                                                        onResolution={tag->
                                                            scope.launch {
                                                                val outcome=withContext(Dispatchers.IO){
                                                                    runCatching{
                                                                        check(database.updateResolutionScale(s.id,tag)){
                                                                            "Session no longer exists"
                                                                        }
                                                                    }
                                                                }
                                                                outcome.fold(onSuccess={
                                                                    sessionList=withContext(Dispatchers.IO){
                                                                        database.list()
                                                                    }
                                                                    message="Resolution tag saved"
                                                                },onFailure={e->
                                                                    message="Resolution tag failed: "+
                                                                        (e.message?:e.javaClass.simpleName)
                                                                })
                                                            }
                                                        },
                                                        onNote={note->
                                                            scope.launch {
                                                                val outcome=withContext(Dispatchers.IO){
                                                                    runCatching {
                                                                        check(database.updateGameNote(s.id,note)){
                                                                            "Session missing or recording"
                                                                        }
                                                                    }
                                                                }
                                                                outcome.fold(onSuccess={
                                                                    sessionList=withContext(Dispatchers.IO){database.list()}
                                                                    message="Game note updated"
                                                                },onFailure={
                                                                    message="Game note failed: "+
                                                                        (it.message?:it.javaClass.simpleName)
                                                                })
                                                            }
                                                        },
                                                        onConfig={config->
                                                            scope.launch {
                                                                val outcome=withContext(Dispatchers.IO){
                                                                    runCatching {
                                                                        check(database.updateConfiguration(s.id,config)){
                                                                            "Session missing or recording"
                                                                        }
                                                                    }
                                                                }
                                                                outcome.fold(onSuccess={
                                                                    sessionList=withContext(Dispatchers.IO){database.list()}
                                                                    message="Configuration updated"
                                                                },onFailure={
                                                                    message="Configuration failed: "+
                                                                        (it.message?:it.javaClass.simpleName)
                                                                })
                                                            }
                                                        },
                                                        onDelete={
                                                            scope.launch {
                                                                val outcome=withContext(Dispatchers.IO){
                                                                    runCatching {
                                                                        check(database.deleteSession(s.id)){
                                                                            "Session missing or still recording"
                                                                        }
                                                                    }
                                                                }
                                                                outcome.fold(onSuccess={
                                                                    metadataEditorId=0L
                                                                    if(detailedId==s.id) {
                                                                        detailedId=0L;detailReport=null
                                                                        detailPoints=emptyList()
                                                                    }
                                                                    if(firstId==s.id)firstId=0L
                                                                    if(secondId==s.id)secondId=0L
                                                                    sessionList=withContext(Dispatchers.IO){database.list()}
                                                                    linkedPairs=withContext(Dispatchers.IO){database.links()}
                                                                    message="Session #"+s.id+" deleted"
                                                                },onFailure={
                                                                    message="Delete failed: "+
                                                                        (it.message?:it.javaClass.simpleName)
                                                                })
                                                            }
                                                        },
                                                        onClose={metadataEditorId=0L})
                                                }
                                            }
                                        }
                                    }
                                    "Compare" -> {
                                        GlassPanel {
                                            Text("Analysis & Session Compare", style = MaterialTheme.typography.titleMedium)
                                            OutlinedButton(onClick={importFile.launch(arrayOf("*/*"))},
                                                 enabled=!status.running && !status.starting) {
                                                 Text("Import CSV")
                                             }
                                             Text("Import creates an offline session; compare elapsed time, not identical game events.",
                                                 color=muted,style=MaterialTheme.typography.bodySmall)
                                             Text("Compare the same game and track. Equal GPU load does NOT prove equal FPS.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                 Text("Default crop: skip first 30s (legacy details too)",
                                                     Modifier.weight(1f),color=coreText,
                                                     style=MaterialTheme.typography.bodySmall)
                                                 Switch(skipWarmUp,
                                                     onCheckedChange={skipWarmUp=it})
                                             }
                                             Text("After REC, not proven game-world start. "+
                                                 "Original samples and linked timelines remain unchanged.",
                                                 color=coreSecondary,
                                                 style=MaterialTheme.typography.labelSmall)
                                             Row(verticalAlignment=Alignment.CenterVertically) {
                                                Text("Chart display filter: GPU ≥ 15% (not statistics)",
                                                    Modifier.weight(1f),color=muted,
                                                    style=MaterialTheme.typography.bodySmall)
                                                Switch(activeOnly,onCheckedChange={activeOnly=it})
                                            }
                                            Text("Select session A · D-pad focus + A", color = cyan)
                                            Text("Stock is a CPU-limit profile, NOT the graphics driver. "+
                                                "Use Sessions → Edit metadata to tag T30 or StevenMXZ "+
                                                "for each recording before comparing.",
                                                color=solarGold,
                                                style=MaterialTheme.typography.bodySmall)
                                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                sessionList.take(14).forEach { s ->
                                                    FilterChip(selected = firstId == s.id,
                                                        onClick = { firstId = s.id },
                                                        label = { Text("Session " + s.id + " · " + s.configuration+
                                                            " · driver: "+(s.graphicsDriver?:"untagged")) })
                                                }
                                            }
                                            Text("Select session B · D-pad focus + A", color = cyan)
                                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                sessionList.take(14).forEach { s ->
                                                    FilterChip(selected = secondId == s.id,
                                                        onClick = { secondId = s.id },
                                                        label = { Text("Session " + s.id + " · " + s.configuration+
                                                            " · driver: "+(s.graphicsDriver?:"untagged")) })
                                                }
                                            }
                                            if(firstId>0L && secondId>0L && firstId!=secondId) {
                                                val linked=linkedPairs.any{
                                                    setOf(it.firstId,it.secondId)==setOf(firstId,secondId)
                                                }
                                                OutlinedButton(onClick={
                                                    scope.launch {
                                                        val result=withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                if(linked) database.unlinkSessions(
                                                                    firstId,secondId)
                                                                else database.linkSessions(firstId,secondId)
                                                            }
                                                        }
                                                        result.fold(onSuccess={
                                                            linkedPairs=withContext(Dispatchers.IO){
                                                                database.links()
                                                            }
                                                            message=if(linked)
                                                                "Link removed; recordings unchanged"
                                                            else "Manual link saved; samples remain separate"
                                                        },onFailure={e->
                                                            message="Link failed: "+(e.message?:"unknown")
                                                        })
                                                    }
                                                }){Text(if(linked)"Unlink recordings"
                                                    else "Link these recordings")}
                                                if(linked && firstPoints.isNotEmpty() &&
                                                    secondPoints.isNotEmpty()) {
                                                    val a=sessionList.firstOrNull{it.id==firstId}
                                                    val b=sessionList.firstOrNull{it.id==secondId}
                                                    if(a!=null && b!=null)LinkedSessionsTimeline(
                                                        a,firstPoints,b,secondPoints)
                                                }else Text("Manual and reversible. "+
                                                    "No interpolated samples or presumed restart.",
                                                    color=coreSecondary,
                                                    style=MaterialTheme.typography.labelSmall)
                                            }
                                            // Use identical warm-up windows for Session A/B analytics.
                                            // Linked-session timestamps remain untouched.
                                            val eligibleA=remember(firstPoints,skipWarmUp) {
                                                V08Analytics.afterWarmUp(firstPoints,
                                                    if(skipWarmUp)30 else 0)
                                            }
                                            val eligibleB=remember(secondPoints,skipWarmUp) {
                                                V08Analytics.afterWarmUp(secondPoints,
                                                    if(skipWarmUp)30 else 0)
                                            }
                                            val analysisA=remember(firstPoints,firstId,sessionList,skipWarmUp) {
                                                sessionList.firstOrNull{it.id==firstId}
                                                    ?.takeIf{firstPoints.isNotEmpty()}
                                                    ?.let{BenchmarkAnalyzer.analyze(it,firstPoints,if(skipWarmUp)30 else 0)}
                                            }
                                            val analysisB=remember(secondPoints,secondId,sessionList,skipWarmUp) {
                                                sessionList.firstOrNull{it.id==secondId}
                                                    ?.takeIf{secondPoints.isNotEmpty()}
                                                    ?.let{BenchmarkAnalyzer.analyze(it,secondPoints,if(skipWarmUp)30 else 0)}
                                            }
                                            val selectedSessionA=sessionList.firstOrNull{it.id==firstId}
                                            val selectedSessionB=sessionList.firstOrNull{it.id==secondId}
                                            if(selectedSessionA!=null && selectedSessionB!=null &&
                                                selectedSessionA.id!=selectedSessionB.id &&
                                                firstPoints.isNotEmpty() && secondPoints.isNotEmpty()) {
                                                CompareStudioPanel(
                                                    sessionA=selectedSessionA,
                                                    pointsA=firstPoints,
                                                    sessionB=selectedSessionB,
                                                    pointsB=secondPoints,
                                                    activeGpuFilter=activeOnly,
                                                    showFullTimelines=compareFullTimeline,
                                                    onToggleFullTimeline={compareFullTimeline=it},
                                                    initialSkipWarmUp=skipWarmUp,
                                                    cursorFraction=compareCursor,
                                                    onCursorFraction={compareCursor=it},
                                                    onExport={window ->
                                                        abWindowExport=DocumentWindowRequest.ab(
                                                            DocumentWindowRequest.Ab(
                                                                firstId,secondId,
                                                                window.startElapsedMs,window.endElapsedMs))
                                                        abWindowTextFile.launch(
                                                            "CoreMetrics_AB_Window_"+firstId+"_"+secondId+
                                                                "_"+window.startElapsedMs+"_"+window.endElapsedMs+".txt")
                                                    },
                                                    onExportPng={ window,share ->
                                                        pngRequest=ScorecardExportRequest(
                                                            firstId=firstId,secondId=secondId,
                                                            startMs=window.startElapsedMs,
                                                            endMs=window.endElapsedMs,
                                                            styleKey=appliedFinish.key,
                                                            shareAfterSave=share).encode()
                                                        pngDocument.launch(
                                                            "CoreMetrics_AB_"+firstId+"_"+secondId+
                                                                "_"+window.startElapsedMs+
                                                                "_"+window.endElapsedMs+".png")
                                                    }
                                                )
                                            }else Text("Choose two distinct recordings to compare.",
                                                color=coreSecondary)
                                            OutlinedButton(onClick={
                                                compareTechnical=!compareTechnical
                                            }) {
                                                Text(if(compareTechnical)"Hide legacy technical evidence"
                                                    else "Show legacy full-run diagnostics and regimes")
                                            }
                                            if(compareTechnical) {
                                            Text("LEGACY TECHNICAL VIEW: These panels and "+
                                                "their original full-run report use source "+
                                                "chronology (optionally excluding first 30s), "+
                                                "not the A/B scorecard's custom crop. Use "+
                                                "Export selected A/B report above for "+
                                                "range-matched data.",
                                                color=solarOrange,
                                                style=MaterialTheme.typography.bodySmall)
                                            analysisA?.let { r ->
                                                Row(Modifier.fillMaxWidth(),
                                                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                                    ObservedProfilePanel(
                                                        r.session.configuration,eligibleA,
                                                        modifier=Modifier.weight(1f),
                                                        referenceStartMs=
                                                            firstPoints.firstOrNull()?.timeMs)
                                                    EnergyRatePanel(r.energy,Modifier.weight(1f))
                                                }
                                                CoreWinnerPanel(eligibleA,Modifier.fillMaxWidth())
                                                HorizontalDivider(color=coreBorder)
                                                Text("BENCHMARK RELIABILITY · "+r.quality.score+
                                                    "/100 · "+r.quality.category,
                                                    color=iceBlue,fontWeight=FontWeight.Bold)
                                                Row(Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    InstrumentKpi("Measured energy",
                                                        r.energy.wh?.let{String.format(
                                                            Locale.US,"%.4f Wh",it)},
                                                        "Battery terminal; no gap filling",solarGold,
                                                        Modifier.width(185.dp))
                                                    InstrumentKpi("Data coverage",
                                                        fmt(r.quality.coveragePct)+"%",
                                                        r.quality.gapCount.toString()+" gaps",
                                                        coreCyan,Modifier.width(155.dp))
                                                    InstrumentKpi("Thermal slope",
                                                        r.thermal.slopeCPerMin?.let{
                                                            fmt(it)+"°C/min"},
                                                        r.thermal.state,coreMint,
                                                        Modifier.width(185.dp))
                                                }
                                                Text("Observed: "+r.quality.observedCeilings,
                                                    color=if(r.quality.labelConflict)solarOrange
                                                        else coreSecondary,
                                                    style=MaterialTheme.typography.bodySmall)
                                                r.quality.reasons.take(4).forEach{
                                                    Text("• "+it,color=coreSecondary,
                                                        style=MaterialTheme.typography.labelSmall)
                                                }
                                                Text(r.frames.note,color=coreSecondary,
                                                    style=MaterialTheme.typography.labelSmall)
                                                OutlinedButton(onClick={
                                                    reportAId=firstId;reportBId=0L
                                                     reportSkipWarmup=skipWarmUp
                                                    reportFile.launch("CoreMetrics_Report_A_"+
                                                        firstId+".txt")
                                                }){Text("Export evidence report")}
                                            }
                                            if(analysisA!=null && analysisB!=null) {
                                                Text("SESSION B · NORMALIZED ENERGY",
                                                    color=iceBlue,fontWeight=FontWeight.Bold)
                                                EnergyRatePanel(analysisB.energy)
                                                val rateA=analysisA.energy.milliWhPerMinute
                                                val rateB=analysisB.energy.milliWhPerMinute
                                                Text("B − A normalized rate: "+
                                                    (if(rateA!=null && rateB!=null)
                                                        String.format(Locale.US,"%.2f mWh/min",
                                                            rateB-rateA) else "unavailable"),
                                                    color=solarGold)
                                                if(analysisA.elapsedSeconds>0.0 &&
                                                    analysisB.elapsedSeconds>0.0 &&
                                                    max(analysisA.elapsedSeconds,
                                                        analysisB.elapsedSeconds)/
                                                        kotlin.math.min(analysisA.elapsedSeconds,
                                                            analysisB.elapsedSeconds)>1.2)
                                                    Text("CAUTION: durations differ by >20%.",
                                                        color=solarOrange)
                                                if(analysisA.meanGpuLoad!=null &&
                                                    analysisB.meanGpuLoad!=null &&
                                                    kotlin.math.abs(analysisA.meanGpuLoad-
                                                        analysisB.meanGpuLoad)>10.0)
                                                    Text("CAUTION: GPU workload differs by >10 pp.",
                                                        color=solarOrange)
                                                if(analysisA.energy.coveragePct<80.0 ||
                                                    analysisB.energy.coveragePct<80.0)
                                                    Text("CAUTION: poor valid discharge coverage.",
                                                        color=solarOrange)
                                                Text("B quality: "+analysisB.quality.score+"/100 · "+
                                                    "B energy: "+(analysisB.energy.wh?.let{
                                                        String.format(Locale.US,"%.4f Wh",it)
                                                    }?:"—"),color=coreSecondary)
                                                OutlinedButton(onClick={
                                                    reportAId=firstId;reportBId=secondId
                                                     reportSkipWarmup=skipWarmUp
                                                    reportFile.launch("CoreMetrics_AB_"+
                                                        firstId+"_"+secondId+".txt")
                                                }){Text("Export A/B report")}
                                            }
                                            val viewA=remember(eligibleA,activeOnly) {
                                                if(activeOnly)eligibleA.filter {
                                                    (it.gpuLoad ?: 0.0)>=15.0
                                                }else eligibleA
                                            }
                                            val viewB=remember(eligibleB,activeOnly) {
                                                if(activeOnly)eligibleB.filter {
                                                    (it.gpuLoad ?: 0.0)>=15.0
                                                }else eligibleB
                                            }
                                            // Segment the original chronology *within the
                                            // selected analysis window* before the GPU-load
                                            // gameplay filter. Keep real sample timestamps.
                                            val regimes=remember(eligibleA,activeOnly) {
                                                SessionRegimeAnalyzer.analyze(eligibleA.map { p ->
                                                    SessionRegimeAnalyzer.Sample(
                                                        p.timeMs,p.observedCpuLimits,p.cpu,p.gpu,
                                                        p.cpuLoad,p.busiestCorePct,p.gpuLoad,p.ram,
                                                        p.batteryWatts,p.batteryPowerStatus)
                                                },activeGameplayOnly=activeOnly)
                                            }
                                            if(eligibleA.isNotEmpty()) {
                                                HorizontalDivider(color=Color.White.copy(alpha=.14f))
                                                Text("IN-SESSION CPU CEILING REGIMES · A",
                                                    color=iceBlue,fontWeight=FontWeight.Bold)
                                                Text("Observed limits (not proof of an applied tuner). " +
                                                    "One/two-sample switching glitches are ignored."+
                                                    if(skipWarmUp) " First 30 s excluded; elapsed " +
                                                        "times remain relative to the original start."
                                                    else "",
                                                    style=MaterialTheme.typography.bodySmall,
                                                    color=coreSecondary)
                                                Row(Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    regimes.forEach { seg ->
                                                        Column(Modifier.width(236.dp).pureGlass(14).padding(12.dp),
                                                            verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                                            Text("#"+(seg.index+1)+"  "+seg.label,
                                                                color=if(seg.baseline) solarOrange else solarGold,
                                                                style=MaterialTheme.typography.labelLarge,
                                                                fontWeight=FontWeight.Bold)
                                                            Text(((seg.startMs-firstPoints.first().timeMs)/1000).toString()+
                                                                "–"+((seg.endMs-firstPoints.first().timeMs)/1000)+
                                                                " s · "+seg.sampleCount+" samples",
                                                                color=coreSecondary,
                                                                style=MaterialTheme.typography.labelSmall)
                                                            Text("CPU avg / peak: "+
                                                                (seg.meanCpuC?.let{fmt(it)+"°"}?:"—")+" / "+
                                                                (seg.maxCpuC?.let{fmt(it)+"°"}?:"—"),
                                                                color=coreText)
                                                            Text("GPU avg: "+(seg.meanGpuC?.let{fmt(it)+"°C"}?:"—")+
                                                                " · Battery: "+
                                                                (seg.meanDischargeW?.let{fmt(it)+" W"}?:"—"),
                                                                color=coreText,
                                                                style=MaterialTheme.typography.bodySmall)
                                                            Text(if(seg.baseline) "Reference phase" else
                                                                "Δ CPU "+(seg.deltaMeanCpuC?.let{fmt(it)+"°C"}?:"—")+
                                                                " · Δ power "+
                                                                (seg.deltaMeanDischargeW?.let{fmt(it)+" W"}?:"—"),
                                                                color=solarGold,
                                                                style=MaterialTheme.typography.bodySmall)
                                                            seg.projectedHours?.let {
                                                                Text("Ideal 30.8 Wh projection: "+fmt(it)+" h",
                                                                    color=coreSecondary,
                                                                    style=MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                                if(regimes.size>1) {
                                                    Text("CPU temperature · phase colors · transition markers",
                                                        color=coreSecondary)
                                                    RegimeTimeline(eligibleA,regimes)
                                                }
                                                Text("Battery-terminal net discharge power; playtime is a " +
                                                    "nominal-capacity projection, not measured runtime. " +
                                                    "Different scenes and warm-up confound deltas.",
                                                    color=coreSecondary,
                                                    style=MaterialTheme.typography.labelSmall)
                                            }
                                            if (viewA.isNotEmpty() && viewB.isNotEmpty()) {
                                                Text("A: " + viewA.size + " measured points · B: " +
                                                    viewB.size + " measured points",
                                                    color=coreSecondary,
                                                    style=MaterialTheme.typography.labelSmall)
                                                val meansA=remember(viewA) {V08Analytics.sampleAverages(viewA)}
                                                val meansB=remember(viewB) {V08Analytics.sampleAverages(viewB)}
                                                val meanCpuA=meansA.cpuC
                                                val meanCpuB=meansB.cpuC
                                                val meanGpuA=meansA.gpuC
                                                val meanGpuB=meansB.gpuC
                                                val meanWattA=meansA.dischargeW
                                                val meanWattB=meansB.dischargeW
                                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    InstrumentKpi("Δ mean CPU",
                                                        if(meanCpuA!=null && meanCpuB!=null)
                                                            fmt(meanCpuB-meanCpuA)+"°C" else null,
                                                        "B − A",coreCyan,Modifier.weight(1f))
                                                    InstrumentKpi("Δ peak CPU",
                                                        if(meansA.peakCpuC!=null && meansB.peakCpuC!=null)
                                                            fmt(meansB.peakCpuC-meansA.peakCpuC)+"°C"
                                                        else null,
                                                        "B − A",coreCyan,Modifier.weight(1f))
                                                    InstrumentKpi("Δ mean GPU",
                                                        if(meanGpuA!=null && meanGpuB!=null)
                                                            fmt(meanGpuB-meanGpuA)+"°C" else null,
                                                        "B − A",coreMint,Modifier.weight(1f))
                                                    InstrumentKpi("Δ battery W",
                                                        if(meanWattA!=null && meanWattB!=null)
                                                            fmt(meanWattB-meanWattA)+" W" else null,
                                                        "Sample-mean discharge only · B − A",
                                                        coreAmber,Modifier.weight(1f))
                                                }
                                                Text("CPU temperature · elapsed time · A cyan, B amber",
                                                    color=coreSecondary)
                                                Text("The synchronized cursor and chart are above.",
                                                    color=coreSecondary)
                                                Text("Frame rate remains unavailable if SurfaceFlinger layer access fails. " +
                                                    "Time alignment uses first selected sample, not synchronized in-game events.",
                                                    color=coreSecondary,style=MaterialTheme.typography.labelSmall)
                                            } else {
                                                Text("Select two recordings. If the gameplay filter returns nothing, " +
                                                    "turn it off to inspect all samples.",
                                                    color=muted)
                                            }
                                            } // optional technical details
                                        }
                                    }
                                    "HUD" -> {
                                        if(status.running || status.standby || status.starting)
                                            Text("Floating HUD pauses over CoreMetrics "+
                                                "so it does not obscure controls. "+
                                                "It returns in your game if enabled. "+
                                                "REC continues while this screen is open.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.bodySmall)
                                        HudSettingsPanel(
                                            options=hudOptions,
                                            isArmed=status.standby || status.running,
                                            recording=status.running,
                                            locked=status.hudLocked,
                                            permissionGranted=overlayAllowed,
                                            onChange={ persistHud(it) },
                                            onArm={ armHud() },
                                            closeUiOnArm=closeUiOnArm,
                                            onCloseUiOnArmChange={ enabled->
                                                closeUiOnArm=enabled
                                                prefs.edit().putBoolean(
                                                    "close_ui_on_arm",enabled).apply()
                                            },
                                            onDisarm={ closeHud() },
                                            onUnlock={ unlockHud() },
                                            onGrantPermission={
                                                startActivity(Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:"+packageName)))
                                            },
                                            onTile={requestQuickSettingsTile {message=it}},
                                             onHudTile={
                                                 requestHudQuickSettingsTile{message=it}
                                             }
                                        )
                                    }
                                    else -> {
                                        GlassPanel {
                                            Text("Settings & privacy",color=iceBlue,
                                                style = MaterialTheme.typography.titleMedium)
                                             Text("CONTROLLER · L1/R1 (or L2/R2) switches tabs; "+
                                                 "D-pad/hat and right joystick move focus; "+
                                                 "A/DPAD CENTER selects focused controls. "+
                                                 "With the A/B slider focused, left/right scrubs. "+
                                                 "Use controller mode, not mouse emulation.",
                                                 color=coreSecondary,
                                                 style=MaterialTheme.typography.bodySmall)
                                            ThemePicker(
                                                selected=appliedFinish,
                                                onSelect={ option ->
                                                    finish=option
                                                    prefs.edit().putString(
                                                        "ui_surface_finish",option.key).apply()
                                                }
                                            )
                                            Text("Frosted is the default. True Glass uses "+
                                                "layered transparency (no GPU blur shader). "+
                                                "Modern Colorful and Technical Cockpit are "+
                                                "optional. Themes do not change telemetry "+
                                                "or background resource use.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.labelSmall)
                                            HorizontalDivider(color=coreBorder)
                                            Row(verticalAlignment=Alignment.CenterVertically,
                                                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("OPTIONAL IMMERSIVE LANDSCAPE",
                                                        color=iceBlue,fontWeight=FontWeight.Bold)
                                                    Text("Hide system bars on this screen. "+
                                                        "Swipe from the edge to reveal navigation. "+
                                                        "Off by default.",
                                                        color=coreSecondary,
                                                        style=MaterialTheme.typography.bodySmall)
                                                }
                                                Switch(checked=immersive,onCheckedChange={enabled->
                                                    immersive=enabled
                                                    prefs.edit().putBoolean("immersive_ui",enabled)
                                                        .apply()
                                                })
                                            }
                                            HorizontalDivider(color=coreBorder)
                                            Text("SUPPORT COREMETRICS",color=iceBlue,
                                                style=MaterialTheme.typography.titleSmall)
                                            Text("CoreMetrics is free, offline and fully functional. "+
                                                "If you find it useful, an optional donation "+
                                                "supports development. There are no paid features, "+
                                                "subscriptions or donor-only versions.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.bodySmall)
                                            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                                OutlinedButton(onClick={
                                                    runCatching {
                                                        startActivity(Intent(Intent.ACTION_VIEW,
                                                            Uri.parse("https://ko-fi.com/locutus5382")))
                                                    }.onFailure {
                                                        getSystemService(ClipboardManager::class.java)
                                                            ?.setPrimaryClip(ClipData.newPlainText(
                                                                "CoreMetrics Ko-fi",
                                                                "https://ko-fi.com/locutus5382"))
                                                        message="Browser unavailable; Ko-fi link copied"
                                                    }
                                                }) { Text("Open Ko-fi") }
                                                OutlinedButton(onClick={
                                                    getSystemService(ClipboardManager::class.java)
                                                        ?.setPrimaryClip(ClipData.newPlainText(
                                                            "CoreMetrics Ko-fi",
                                                            "https://ko-fi.com/locutus5382"))
                                                    message="Ko-fi link copied"
                                                }) { Text("Copy link") }
                                            }
                                            HorizontalDivider(color=coreBorder)
                                            Text("Recording is always user-started, offline, and read-only.",
                                                color = muted)
                                            Text("Optional Android Usage Access", color = white)
                                            Text("Used to identify the foreground Android application package during recording. " +
                                                "It cannot identify which NFS title or game is running inside Eden. " +
                                                "The app records normally without Usage Access.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
                                            OutlinedButton(onClick = {
                                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            }) { Text("Open Usage Access settings") }
                                            Text("FPS LAB · Off by default. No PServer command is executed. " +
                                                "An authenticated privileged read-only host and device-verified " +
                                                "presentation timestamps are required before enabling FPS. " +
                                                "PServerBinder visibility alone does not establish access.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.bodySmall)
                                            HorizontalDivider(color=coreBorder)
                                            Text("IN-GAME HUD",color=coreCyan,
                                                style=MaterialTheme.typography.titleSmall)
                                            Text("HUD customization is available on the dedicated HUD tab. " +
                                                "Standby is visible but samples nothing; REC activates sampling.",
                                                color=muted,style=MaterialTheme.typography.bodySmall)
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                Text("Automatically display HUD when starting recording from app or QS",Modifier.weight(1f),
                                                    color=white)
                                                Switch(hudEnabled,onCheckedChange={ enabled ->
                                                    hudEnabled=enabled
                                                    prefs.edit().putBoolean("hud_enabled",enabled).apply()
                                                    if(enabled && !Settings.canDrawOverlays(this@MainActivity)) {
                                                        message="Grant overlay permission; HUD starts on the NEXT session."
                                                    }
                                                })
                                            }
                                            Text(if(Settings.canDrawOverlays(this@MainActivity))
                                                    "Overlay permission granted"
                                                else "Overlay permission required for HUD",
                                                color=if(Settings.canDrawOverlays(this@MainActivity))
                                                    coreMint else coreAmber,
                                                style=MaterialTheme.typography.labelSmall)
                                            OutlinedButton(onClick={
                                                startActivity(Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:" + packageName)))
                                            }) { Text("Open overlay permission") }
                                            HorizontalDivider(color=coreBorder)
                                            Text("HUD status: " + (status.hudStatus ?: "Unknown"),
                                                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
                                            Text("QUICK SETTINGS",color=coreCyan,
                                                style=MaterialTheme.typography.titleSmall)
                                            Text("The Record tile is separate from the application icon. " +
                                                "Add it once, then use it to start/stop recording after Eden opens.",
                                                color=muted,style=MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick={
                                                requestQuickSettingsTile { message=it }
                                            }) { Text("Add Record tile to Quick Settings") }
                                            Text("No root, PServer, INTERNET permission or hardware writes.",
                                                style=MaterialTheme.typography.bodySmall,color=muted)
                                            Text("SENSOR PROVENANCE · READ-ONLY",color=coreCyan,
                                                style=MaterialTheme.typography.titleSmall)
                                            Text("CPU/GPU °C: readable sysfs thermal zones selected by " +
                                                "cpu-/cpuss- and gpu-/gpuss- sensor TYPE, not a fixed zone number.\n"+
                                                "GPU MHz: KGSL cur_freq or gpuclk (instantaneous frequency; " +
                                                "not an inferred performance-mode floor).\n"+
                                                "Battery °C/%: Android ACTION_BATTERY_CHANGED; " +
                                                "temperature reported in tenths of a degree.\n"+
                                                "Net battery W: Android battery current × voltage where valid.\n"+
                                                "System RAM used: /proc/meminfo MemTotal − MemAvailable.\n"+
                                                "Busiest core: /proc/stat interval winner, NOT core residency.\n"+
                                                "Game FPS: not verified on the Portal; no interpolation.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.bodySmall)
                                            Text("Constant GPU MHz or integer battery temperature is not " +
                                                "automatically a sensor error; source precision and " +
                                                "mode effects require device verification.",
                                                color=coreSecondary,
                                                style=MaterialTheme.typography.labelSmall)
                                            Text("Battery power shows battery-terminal flow, not whole-device or wall power. Charging/bypass sessions cannot be compared as power efficiency.",
                                                style = MaterialTheme.typography.bodySmall,color=muted)
                                            HorizontalDivider(color=coreBorder)
                                            Text("RECORDER DIAGNOSTICS",color=coreCyan)
                                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                                OutlinedButton(onClick = {
                                                    diagnosticReport=RecorderJournal.report(this@MainActivity)
                                                }) { Text("View process-exit & lifecycle log") }
                                                OutlinedButton(enabled=diagnosticReport.isNotEmpty(),onClick = {
                                                    val clipboard=getSystemService(ClipboardManager::class.java)
                                                    clipboard.setPrimaryClip(ClipData.newPlainText(
                                                        "CoreMetrics diagnostic report",diagnosticReport))
                                                    message="Diagnostic report copied"
                                                }) { Text("Copy report") }
                                            }
                                            if (diagnosticReport.isNotBlank()) {
                                                Text(diagnosticReport,color=coreSecondary,
                                                    style=MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                if (message.isNotBlank()) Text(message, color = cyan,
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun fmt(value: Double) = String.format(Locale.US,"%.1f",value)
private fun time(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().pureGlass(18)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}
@Composable
private fun MetricCard(title: String, value: String?, source: String, modifier: Modifier = Modifier) {
    Column(modifier.pureGlass(15).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = muted)
        Text(value ?: "Unavailable", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = white)
        Text(source, style = MaterialTheme.typography.labelSmall, color = muted)
    }
}
/** Shaded observed phases over the recorded CPU-temperature curve. */
@Composable
private fun RegimeTimeline(
    points:List<SessionStore.Point>,
    regimes:List<SessionRegimeAnalyzer.RegimeSegment>
) {
    Canvas(Modifier.fillMaxWidth().height(158.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0x80101C2B))) {
        if(points.isEmpty())return@Canvas
        val t0=points.first().timeMs
        val duration=max(1L,points.last().timeMs-t0)
        fun x(ms:Long)=((ms-t0).toFloat()/duration.toFloat()*size.width)
            .coerceIn(0f,size.width)
        val colors=listOf(solarOrange,solarGold,coreCyan,Color(0xFF2563EB))
        regimes.forEachIndexed { index,seg ->
            val left=x(seg.startMs)
            val right=x(seg.endMs)
            if(right>left)drawRect(colors[index%colors.size].copy(alpha=.10f),
                Offset(left,0f),Size(right-left,size.height))
            if(index>0)drawLine(solarGold.copy(alpha=.8f),
                Offset(left,0f),Offset(left,size.height),strokeWidth=2f,
                pathEffect=PathEffect.dashPathEffect(floatArrayOf(9f,7f),0f))
        }
        for(i in 1..3) {
            val y=size.height*i/4f
            drawLine(Color(0x335A8FA3),Offset(0f,y),Offset(size.width,y),1f)
        }
        val coords=points.mapNotNull { p ->
            p.cpu?.takeIf{it.isFinite()}?.let { temp ->
                Offset(x(p.timeMs),((95.0-temp).coerceIn(0.0,75.0)/
                    75.0*size.height).toFloat())
            }
        }
        if(coords.size>1)drawPath(Path().apply {
            moveTo(coords.first().x,coords.first().y)
            coords.drop(1).forEach{lineTo(it.x,it.y)}
        },coreCyan,style=Stroke(width=3f))
    }
}

private fun railIcon(label: String): ImageVector = when (label) {
    "Live" -> Icons.Rounded.Dashboard
    "Sessions" -> Icons.Rounded.History
    "Compare" -> Icons.Rounded.CompareArrows
    "HUD" -> Icons.Rounded.Tune
    else -> Icons.Rounded.Settings
}

@Composable
private fun CompactRailAction(
    label:String,icon:ImageVector,selected:Boolean,
    modifier:Modifier=Modifier,onClick:()->Unit
) {
    val finish=LocalSurfaceFinish.current
    val p=finish.palette
    val shape=RoundedCornerShape(if(p.sharpCorners)6.dp else 11.dp)
    var hasFocus by remember {mutableStateOf(false)}
    Column(
        modifier.fillMaxWidth().height(49.dp)
            .onFocusChanged {hasFocus=it.isFocused}
            .clip(shape)
            .background(if(selected||hasFocus)p.navSelected else p.navIdle)
            .border(if(hasFocus)2.dp else 1.dp,
                if(hasFocus)solarGold else if(selected)p.border
                    else p.borderSoft,shape)
            .cmInteractive(cornerRadiusDp=11,accent=solarGold,onClick=onClick)
            .padding(vertical=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ) {
        Icon(icon,contentDescription=null,modifier=Modifier.size(20.dp),
            tint=if(selected && finish!=SurfaceFinish.SOLAR_PULSE)
                p.accent else p.foreground)
        Text(label,color=if(selected)p.foreground else p.muted,
            fontSize=10.sp,
            fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,
            maxLines=1,
            fontFamily=if(p.sharpCorners)
                androidx.compose.ui.text.font.FontFamily.Monospace
                else androidx.compose.ui.text.font.FontFamily.Default)
    }
}
