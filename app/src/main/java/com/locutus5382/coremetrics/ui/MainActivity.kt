package com.locutus5382.coremetrics.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.monitor.RecordingService
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
private val pages = listOf("Live", "Sessions", "Compare", "Settings")

private data class ScreenStatus(
    val running: Boolean = false, val starting: Boolean = false,
    val sessionId: Long = 0, val count: Long = 0,
    val message: String = "Idle",
    val cpu: String? = null, val gpu: String? = null,
    val cpuLoad: String? = null, val gpuLoad: String? = null,
    val peakCore: String? = null, val ram: String? = null,
    val clock: String? = null, val app: String? = null,
    val lastAt: Long = 0
)

class MainActivity : ComponentActivity() {
    private fun query(): ScreenStatus = try {
        val uri = Uri.parse("content://com.locutus5382.coremetrics.monitor.status")
        val v = contentResolver.call(uri, "status", null, null)
        if (v == null) ScreenStatus(message = "Recorder status unavailable")
        else ScreenStatus(
            running = v.getBoolean("running", false),
            starting = v.getBoolean("starting", false),
            sessionId = v.getLong("id", 0), count = v.getLong("count", 0),
            message = v.getString("message") ?: "Idle",
            cpu = v.getString("cpu"), gpu = v.getString("gpu"),
            cpuLoad = v.getString("cpuLoad"), gpuLoad = v.getString("gpuLoad"),
            peakCore = v.getString("peakCore"), ram = v.getString("ram"),
            clock = v.getString("clock"), app = v.getString("app"),
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
            var label by rememberSaveable { mutableStateOf(prefs.getString("label", "NFS benchmark") ?: "NFS benchmark") }
            var configuration by rememberSaveable {
                mutableStateOf(prefs.getString("configuration", "Stock") ?: "Stock")
            }
            var interval by rememberSaveable { mutableLongStateOf(prefs.getLong("interval_ms", 5000L)) }
            var message by remember { mutableStateOf("") }
            var sessionList by remember { mutableStateOf(emptyList<SessionStore.Session>()) }
            var firstId by rememberSaveable { mutableLongStateOf(0L) }
            var secondId by rememberSaveable { mutableLongStateOf(0L) }
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
                        status = withContext(Dispatchers.IO) { query() }
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

            MaterialTheme(colorScheme = darkColorScheme(primary = cyan, surface = night,
                background = night, onSurface = white)) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(
                        listOf(night, Color(0xFF0E2940), Color(0xFF15152E), night)
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
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension
                        drawCircle(Brush.radialGradient(
                            listOf(glow.copy(alpha = .6f), Color.Transparent),
                            center = Offset(size.width * .89f, size.height * .08f),
                            radius = r * .85f
                        ), radius = r * .85f, center = Offset(size.width * .89f, size.height * .08f))
                    }
                    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(
                            Modifier.width(170.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xB518293A))
                                .border(1.dp, Color(0x667ED2E2), RoundedCornerShape(20.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("CORE", color = cyan, style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold)
                            Text("METRICS", color = white, style = MaterialTheme.typography.titleMedium)
                            Text("GAMEPLAY ANALYTICS", color = muted, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            pages.forEach { item ->
                                Button(onClick = { page = item }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor =
                                        if (item == page) Color(0xFF23637C) else Color(0x5530475B))) {
                                    Text(item, color = white)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(if (status.running) "● RECORDING" else if (status.starting) "◌ STARTING" else "○ IDLE",
                                color = if (status.running) cyan else muted,
                                style = MaterialTheme.typography.labelMedium)
                            Text("v0.1 alpha · read-only", color = muted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(page, color = white, style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (status.running) "● REC · " + status.count + " samples"
                                    else status.message, color = if (status.running) cyan else muted)
                            }
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (page) {
                                    "Live" -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            MetricCard("CPU / SoC", status.cpu, "Named thermal", Modifier.weight(1f))
                                            MetricCard("GPU", status.gpu, "Named thermal", Modifier.weight(1f))
                                            MetricCard("CPU Load", status.cpuLoad?.plus("%"), "System /proc/stat", Modifier.weight(1f))
                                            MetricCard("GPU Load", status.gpuLoad?.plus("%"), "KGSL if readable", Modifier.weight(1f))
                                        }
                                        GlassPanel {
                                            Text("Live recording", color = white, style = MaterialTheme.typography.titleMedium)
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
                                                listOf(1000L,2000L,5000L).forEach { ms ->
                                                    FilterChip(selected = interval == ms,
                                                        onClick = { interval = ms },
                                                        label = { Text((ms / 1000).toString() + " seconds") })
                                                }
                                            }
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
                                                    Text("View sessions")
                                                }
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
                                            Text("Current CPU clocks", color = muted)
                                            Text(status.clock ?: "Unavailable", color = white)
                                            Text("App package: " + (status.app ?: "Unavailable / Usage Access off"),
                                                color = muted, style = MaterialTheme.typography.bodySmall)
                                            Text("FPS, watts and 1% lows are not yet validated. No values are invented.",
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
                                            Text("A/B benchmark comparison", style = MaterialTheme.typography.titleMedium)
                                            Text("Compare identical game tracks. Align time after warmup; do not infer FPS or power efficiency.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
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
                                            if (firstPoints.isNotEmpty() && secondPoints.isNotEmpty()) {
                                                val a = firstPoints.mapNotNull { it.cpu }
                                                val b = secondPoints.mapNotNull { it.cpu }
                                                if (a.isNotEmpty() && b.isNotEmpty()) {
                                                    val avgA = a.average()
                                                    val avgB = b.average()
                                                    Text("CPU mean: A " + fmt(avgA) + "°C  |  B " +
                                                        fmt(avgB) + "°C  |  Δ " + fmt(avgB - avgA) + "°C",
                                                        color = white)
                                                }
                                                Text("CPU temperature · elapsed time", color = muted)
                                                CompareChart(firstPoints,secondPoints)
                                            } else {
                                                Text("Select two saved sessions to draw thermal curves.",
                                                    color = muted)
                                            }
                                        }
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
                                            Text("No overlay, root, PServer, INTERNET permission, or kernel writes in this alpha.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
                                            Text("FPS and battery watts require independent device validation before display.",
                                                style = MaterialTheme.typography.bodySmall, color = muted)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xAD1A2A3D))
            .border(1.dp, Color(0x447ED2E2), RoundedCornerShape(18.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}
@Composable
private fun MetricCard(title: String, value: String?, source: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xAA1A2B3A))
        .border(1.dp, Color(0x447ED2E2), RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = muted)
        Text(value ?: "Unavailable", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = white)
        Text(source, style = MaterialTheme.typography.labelSmall, color = muted)
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
