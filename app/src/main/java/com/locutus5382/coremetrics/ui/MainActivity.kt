package com.locutus5382.coremetrics.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.locutus5382.coremetrics.analysis.SessionRegimeAnalyzer
import com.locutus5382.coremetrics.telemetry.ThermalTrendFilter
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.monitor.HudOptions
import com.locutus5382.coremetrics.monitor.RecorderJournal
import com.locutus5382.coremetrics.quick.SessionTile
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

private data class ScreenStatus(
    val running: Boolean = false, val starting: Boolean = false,
    val standby: Boolean = false,
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
    val lastAt: Long = 0
)

class MainActivity : ComponentActivity() {
    private fun requestQuickSettingsTile(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult("Edit Quick Settings and add CoreMetrics Record manually")
            return
        }
        try {
            val statusBar = getSystemService(StatusBarManager::class.java)
            statusBar?.requestAddTileService(
                ComponentName(this, SessionTile::class.java),
                "CoreMetrics • Record",
                Icon.createWithResource(this, com.locutus5382.coremetrics.R.drawable.ic_coremetrics_notification),
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

    private fun query(): ScreenStatus = try {
        val uri = Uri.parse("content://com.locutus5382.coremetrics.monitor.status")
        val v = contentResolver.call(uri, "status", null, null)
        if (v == null) ScreenStatus(message = "Recorder status unavailable")
        else ScreenStatus(
            running = v.getBoolean("running", false),
            starting = v.getBoolean("starting", false),
            standby = v.getBoolean("standby", false),
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
            lastAt = v.getLong("lastSampleAt", 0)
        )
    } catch (e: Exception) { ScreenStatus(message = "Recorder unreachable: " + e.javaClass.simpleName) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("coremetrics_settings", MODE_PRIVATE)
        val database = SessionStore(applicationContext)
        setContent {
            var page by rememberSaveable { mutableStateOf("Live") }
            var status by remember { mutableStateOf(ScreenStatus()) }
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
            var label by rememberSaveable { mutableStateOf(prefs.getString("label", "NFS benchmark") ?: "NFS benchmark") }
            var configuration by rememberSaveable {
                mutableStateOf(prefs.getString("configuration", "Stock") ?: "Stock")
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
            var firstId by rememberSaveable { mutableLongStateOf(0L) }
            var secondId by rememberSaveable { mutableLongStateOf(0L) }
            var activeOnly by rememberSaveable { mutableStateOf(true) }
            var firstPoints by remember { mutableStateOf(emptyList<SessionStore.Point>()) }
            var secondPoints by remember { mutableStateOf(emptyList<SessionStore.Point>()) }
            var exportId by remember { mutableLongStateOf(0L) }
            val scope = rememberCoroutineScope()
            val notifyPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                message = if (granted) "Notifications allowed. Tap Start to record."
                    else "Notifications required for explicit recording."
            }
            val exportFile = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->
                if (uri != null && exportId != 0L) scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                database.exportCsv(exportId, it)
                            } ?: error("Document cannot be opened")
                        }
                    }
                    message = result.fold(onSuccess = { "CSV exported successfully" },
                        onFailure = { "Export failed: " + (it.message ?: it.javaClass.simpleName) })
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
                        status = next
                        overlayAllowed=Settings.canDrawOverlays(this@MainActivity)
                        delay(1300L)
                    }
                }
            }
            LaunchedEffect(page, status.running) {
                if (page == "Sessions" || page == "Compare") {
                    sessionList = withContext(Dispatchers.IO) { database.list() }
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
                // Commit metadata before :monitor process sees the HUD click.
                prefs.edit().putString("label",label.trim())
                    .putString("configuration",configuration)
                    .putLong("interval_ms",interval).commit()
                hudOptions.save(this@MainActivity)
                val i=HudOptions.addExtras(
                    Intent(this@MainActivity,RecordingService::class.java)
                        .setAction(RecordingService.ARM)
                        .putExtra(RecordingService.LABEL,label.trim())
                        .putExtra(RecordingService.CONFIG,configuration)
                        .putExtra(RecordingService.INTERVAL,interval),hudOptions)
                runCatching{startForegroundService(i)}.fold(
                    onSuccess={message="Standby requested · no recording until REC"},
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
            MaterialTheme(colorScheme = darkColorScheme(primary = iceBlue, surface = cobaltNavy,
                background = cobaltNavy, onSurface = coreText)) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(
                        listOf(cobaltNavy, Color(0xFF09173A), Color(0xFF10265C), cobaltNavy)
                    )).onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        if (key.action != KeyEvent.ACTION_DOWN || key.repeatCount != 0) false
                        else when(key.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_L1 -> {
                                page = pages[Math.floorMod(pages.indexOf(page) - 1, pages.size)]; true
                            }
                            KeyEvent.KEYCODE_BUTTON_R1 -> {
                                page = pages[Math.floorMod(pages.indexOf(page) + 1, pages.size)]; true
                            }
                            else -> false
                        }
                    }
                ) {
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
                                CompactRailAction(item, railIcon(item), page==item) { page=item }
                            }
                            HorizontalDivider(color=coreBorder)
                            Text(if(status.running) "● REC"
                                else if(status.starting) "START"
                                else if(status.standby) "READY"
                                else "IDLE",color=if(status.running) solarOrange
                                    else if(status.standby) solarGold else muted,
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
                                    Text("COREMETRICS  /  COBALT & SOLAR GLASS",
                                        color=iceBlue,
                                        style=MaterialTheme.typography.labelSmall)
                                    Text(page.uppercase(Locale.US),color=coreText,
                                        style=MaterialTheme.typography.titleLarge,
                                        fontWeight=FontWeight.Bold)
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
                                        else status.message.take(52),
                                        color=if(status.running) coreMint else coreSecondary,
                                        style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (page) {
                                    "Live" -> {
                                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                            Button(onClick={ if(status.standby || status.running) {
                                                page="HUD"
                                            } else armHud() },
                                                colors=ButtonDefaults.buttonColors(
                                                    containerColor=solarOrange)) {
                                                Text(if(status.standby) "HUD READY"
                                                    else if(status.running) "● REC"
                                                    else "LAUNCH HUD STANDBY")
                                            }
                                            OutlinedButton(onClick={page="HUD"}) {
                                                Text("Customize HUD")
                                            }
                                            OutlinedButton(onClick={
                                                requestQuickSettingsTile { message=it }
                                            }) { Text("Add QS tile") }
                                        }
                                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                            val cpuDisplay=cpuEma?.let {
                                                String.format(Locale.US,"%.1f°C",it)
                                            } ?: status.cpu
                                            PureGlassGauge("CPU",cpuDisplay,
                                                (((cpuDisplay?.removeSuffix("°C")
                                                    ?.toDoubleOrNull() ?: 25.0)-25.0)/65.0).toFloat(),
                                                solarOrange,Modifier.weight(1f))
                                            PureGlassGauge("GPU",status.gpu,
                                                (((status.gpu?.removeSuffix("°C")
                                                    ?.toDoubleOrNull() ?: 25.0)-25.0)/65.0).toFloat(),
                                                solarGold,Modifier.weight(1f))
                                            PureGlassGauge("Battery W",status.power,
                                                ((status.power?.removeSuffix(" W")
                                                    ?.toDoubleOrNull() ?: 0.0)/12.0).toFloat(),
                                                solarGold,Modifier.weight(1f))
                                            PureGlassGauge("FPS",status.fps,
                                                ((status.fps?.toDoubleOrNull() ?: 0.0)/120.0).toFloat(),
                                                iceBlue,Modifier.weight(1f))
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
                                         Text("Ring arcs use display-only reference scales; " +
                                            "they are not thermal safety limits.",
                                            color=coreSecondary,
                                            style=MaterialTheme.typography.labelSmall)
                                        LiveScope(liveTrace)
                                        PerCoreLoadMatrix(status.coreLoads)
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            MetricCard("CPU load",status.cpuLoad?.plus("%"),
                                                "All cores",Modifier.weight(1f))
                                            MetricCard("GPU load",status.gpuLoad?.plus("%"),
                                                "Adreno KGSL",Modifier.weight(1f))
                                            MetricCard("Prime / busiest",status.peakCore?.plus("%"),
                                                "Observed single core",Modifier.weight(1f))
                                            MetricCard("Frame time",status.frameMs,
                                                "Surface layer only",Modifier.weight(1f))
                                        }
                                        GlassPanel {
                                            Text("RECORDING CONTROL  ·  ONE USER-STARTED SESSION",
                                                color = coreCyan, style = MaterialTheme.typography.titleMedium)
                                            Text("Game label", color = muted)
                                            OutlinedTextField(label, { label = it.take(64) },
                                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                label = { Text("Game / track label") })
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("Stock", "ClusterTune", "Custom").forEach { item ->
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
                                                            } else {
                                                                startForegroundService(svc.setAction(RecordingService.START)
                                                                    .putExtra(RecordingService.LABEL,label.trim())
                                                                    .putExtra(RecordingService.CONFIG,configuration)
                                                                    .putExtra(RecordingService.INTERVAL,interval))
                                                                message = "Start requested"
                                                            }
                                                        } catch (e: Exception) {
                                                            message = "Action failed: " + e.javaClass.simpleName
                                                        }
                                                    }
                                                }) {
                                                    Text(if (status.running || status.starting)
                                                        "STOP AND SAVE" else "START RECORDING")
                                                }
                                                OutlinedButton(onClick = { page = "Sessions" }) {
                                                    Text("Sessions")
                                                }
                                                OutlinedButton(onClick = {
                                                    requestQuickSettingsTile { message=it }
                                                }) { Text("Add QS tile") }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            MetricCard("Peak core", status.peakCore?.plus("%"),
                                                "Highest sampled core", Modifier.weight(1f))
                                            MetricCard("RAM used", status.ram,
                                                "System total − available", Modifier.weight(1f))
                                            MetricCard("Last sample", if (status.lastAt > 0)
                                                time(status.lastAt) else null, "Device-local time", Modifier.weight(1f))
                                        }
                                        GlassPanel {
                                            Text("ACTUAL CPU CLOCKS", color = coreCyan)
                                            Text(status.clock ?: "Unavailable", color = white)
                                            Text("OBSERVED LIMITS  ·  not proof of tuner identity",
                                                color = muted,style=MaterialTheme.typography.labelSmall)
                                            Text(status.observedLimits ?: "Unavailable",color=white)
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
                                            Text("Recorded runs", style = MaterialTheme.typography.titleMedium)
                                            Text("Only explicit sessions are recorded. Labels are not proof of applied limits.",
                                                color = muted, style = MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick = {
                                                scope.launch { sessionList = withContext(Dispatchers.IO) { database.list() } }
                                            }) { Text("Refresh history") }
                                            if (sessionList.isEmpty()) Text("No sessions yet.", color = muted)
                                            sessionList.forEach { s ->
                                                HorizontalDivider(color = Color(0x335F91A7))
                                                Row(verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(s.label + " · " + s.configuration, color = white,
                                                            fontWeight = FontWeight.Bold)
                                                        Text(time(s.startedAt) + " · " + s.count + " samples · " + s.status,
                                                            color = muted, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    OutlinedButton(onClick = {
                                                        exportId = s.id
                                                        exportFile.launch("CoreMetrics_" + s.id + ".csv")
                                                    }) { Text("CSV") }
                                                }
                                            }
                                        }
                                    }
                                    "Compare" -> {
                                        GlassPanel {
                                            Text("Analysis & Session Compare", style = MaterialTheme.typography.titleMedium)
                                            Text("Compare the same game and track. Equal GPU load does NOT prove equal FPS.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                Text("Active 3D gameplay: GPU ≥ 15% (heuristic)",
                                                    Modifier.weight(1f),color=muted,
                                                    style=MaterialTheme.typography.bodySmall)
                                                Switch(activeOnly,onCheckedChange={activeOnly=it})
                                            }
                                            Text("Select Stock / A", color = cyan)
                                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                sessionList.take(14).forEach { s ->
                                                    FilterChip(selected = firstId == s.id,
                                                        onClick = { firstId = s.id },
                                                        label = { Text("#" + s.id + " " + s.configuration) })
                                                }
                                            }
                                            Text("Select Tuned / B", color = cyan)
                                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                sessionList.take(14).forEach { s ->
                                                    FilterChip(selected = secondId == s.id,
                                                        onClick = { secondId = s.id },
                                                        label = { Text("#" + s.id + " " + s.configuration) })
                                                }
                                            }
                                            val viewA = if(activeOnly) firstPoints.filter {
                                                (it.gpuLoad ?: 0.0) >= 15.0
                                            } else firstPoints
                                            val viewB = if(activeOnly) secondPoints.filter {
                                                (it.gpuLoad ?: 0.0) >= 15.0
                                            } else secondPoints
                                            // Segment the ORIGINAL timeline, before gameplay filtering.
                                            val regimes=remember(firstPoints,activeOnly) {
                                                SessionRegimeAnalyzer.analyze(firstPoints.map { p ->
                                                    SessionRegimeAnalyzer.Sample(
                                                        p.timeMs,p.observedCpuLimits,p.cpu,p.gpu,
                                                        p.cpuLoad,p.busiestCorePct,p.gpuLoad,p.ram,
                                                        p.batteryWatts,p.batteryPowerStatus)
                                                },activeGameplayOnly=activeOnly)
                                            }
                                            if(firstPoints.isNotEmpty()) {
                                                HorizontalDivider(color=Color.White.copy(alpha=.14f))
                                                Text("IN-SESSION CPU CEILING REGIMES · A",
                                                    color=iceBlue,fontWeight=FontWeight.Bold)
                                                Text("Observed limits (not proof of an applied tuner). " +
                                                    "One/two-sample switching glitches are ignored.",
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
                                                    RegimeTimeline(firstPoints,regimes)
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
                                                val meanCpuA=viewA.mapNotNull { it.cpu }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                val meanCpuB=viewB.mapNotNull { it.cpu }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                val meanGpuA=viewA.mapNotNull { it.gpu }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                val meanGpuB=viewB.mapNotNull { it.gpu }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                val meanWattA=viewA.mapNotNull { it.batteryWatts }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                val meanWattB=viewB.mapNotNull { it.batteryWatts }.takeIf {
                                                    it.isNotEmpty() }?.average()
                                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    InstrumentKpi("Δ mean CPU",
                                                        if(meanCpuA!=null && meanCpuB!=null)
                                                            fmt(meanCpuB-meanCpuA)+"°C" else null,
                                                        "B − A",coreCyan,Modifier.weight(1f))
                                                    InstrumentKpi("Δ peak CPU",
                                                        if(viewA.any { it.cpu!=null } && viewB.any { it.cpu!=null })
                                                            fmt(viewB.mapNotNull{it.cpu}.max() -
                                                                viewA.mapNotNull{it.cpu}.max())+"°C" else null,
                                                        "B − A",coreCyan,Modifier.weight(1f))
                                                    InstrumentKpi("Δ mean GPU",
                                                        if(meanGpuA!=null && meanGpuB!=null)
                                                            fmt(meanGpuB-meanGpuA)+"°C" else null,
                                                        "B − A",coreMint,Modifier.weight(1f))
                                                    InstrumentKpi("Δ battery W",
                                                        if(meanWattA!=null && meanWattB!=null)
                                                            fmt(meanWattB-meanWattA)+" W" else null,
                                                        "Battery net · B − A",coreAmber,Modifier.weight(1f))
                                                }
                                                Text("CPU temperature · elapsed time · A cyan, B amber",
                                                    color=coreSecondary)
                                                CompareChart(viewA,viewB)
                                                Text("Frame rate remains unavailable if SurfaceFlinger layer access fails. " +
                                                    "Time alignment uses first selected sample, not synchronized in-game events.",
                                                    color=coreSecondary,style=MaterialTheme.typography.labelSmall)
                                            } else {
                                                Text("Select two recordings. If the gameplay filter returns nothing, " +
                                                    "turn it off to inspect all samples.",
                                                    color=muted)
                                            }
                                        }
                                    }
                                    "HUD" -> {
                                        HudSettingsPanel(
                                            options=hudOptions,
                                            isArmed=status.standby || status.running,
                                            recording=status.running,
                                            locked=status.hudLocked,
                                            permissionGranted=overlayAllowed,
                                            onChange={ persistHud(it) },
                                            onArm={ armHud() },
                                            onDisarm={ closeHud() },
                                            onUnlock={ unlockHud() },
                                            onGrantPermission={
                                                startActivity(Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:"+packageName)))
                                            },
                                            onTile={requestQuickSettingsTile {message=it}}
                                        )
                                    }
                                    else -> {
                                        GlassPanel {
                                            Text("Settings & privacy", style = MaterialTheme.typography.titleMedium)
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
                                            Text("FPS: the Portal denied SurfaceFlinger access on every " +
                                                "recording. System dumps are disabled in this stability build. " +
                                                "No fabricated FPS values. We will test a read-only FPS " +
                                                "provider separately.", color=coreSecondary,
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

/** Static graph; no infinite animation or frame polling. Points represent recorded samples. */
@Composable
private fun CompareChart(a: List<SessionStore.Point>, b: List<SessionStore.Point>) {
    Canvas(Modifier.fillMaxWidth().height(180.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0x80101C2B))) {
        val w = size.width
        val h = size.height
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(Color(0x335A8FA3), Offset(0f,y),Offset(w,y),strokeWidth=1f)
        }
        fun pathFor(points: List<SessionStore.Point>): Path? {
            if (points.size < 2) return null
            val start = points.first().timeMs
            val duration = max(1L, points.last().timeMs - start)
            val coords = points.mapNotNull { p ->
                val temp = p.cpu ?: return@mapNotNull null
                Offset(((p.timeMs - start).toFloat() / duration) * w,
                    ((85.0 - temp).coerceIn(0.0,65.0) / 65.0 * h).toFloat())
            }
            if (coords.size < 2) return null
            return Path().apply {
                moveTo(coords[0].x, coords[0].y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }
        pathFor(a)?.let { drawPath(it, cyan, style = Stroke(width=3f)) }
        pathFor(b)?.let { drawPath(it, Color(0xFFFFC077), style = Stroke(width=3f)) }
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
    label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit
) {
    val shape=RoundedCornerShape(11.dp)
    Column(
        Modifier.fillMaxWidth().height(49.dp)
            .clip(shape)
            .background(if(selected) Color(0xE7245367) else Color(0x42192335))
            .border(1.dp,if(selected) coreCyan.copy(alpha=0.7f)
                else coreBorder.copy(alpha=0.38f),shape)
            .clickable(role=Role.Button,onClick=onClick)
            .padding(vertical=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ) {
        Icon(icon,contentDescription=null,modifier=Modifier.size(20.dp),
            tint=if(selected) coreCyan else coreSecondary)
        Text(label,color=if(selected) coreText else coreSecondary,
            fontSize=10.sp,fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,
            maxLines=1)
    }
}
