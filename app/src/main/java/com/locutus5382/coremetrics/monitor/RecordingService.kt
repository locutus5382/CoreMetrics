package com.locutus5382.coremetrics.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.service.quicksettings.TileService
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.quick.SessionTile
import com.locutus5382.coremetrics.telemetry.DirectSampler
import com.locutus5382.coremetrics.telemetry.ForegroundAttribution
import com.locutus5382.coremetrics.telemetry.FrameEstimate
import com.locutus5382.coremetrics.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit user-started recording. No boot receiver, no privilege or kernel writes. */
class RecordingService : Service() {
    companion object {
        const val START = "com.locutus5382.coremetrics.START"
        const val STOP = "com.locutus5382.coremetrics.STOP"
        const val LABEL = "label"
        const val CONFIG = "configuration"
        const val INTERVAL = "interval"
        private const val CHANNEL = "coremetrics_monitor"
        private const val NOTIFICATION_ID = 4411
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: SessionStore
    private lateinit var hud: RecorderHud
    private var task: Job? = null
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        db = SessionStore(applicationContext)
        hud = RecorderHud(applicationContext)
        RecorderJournal.event(this, "SERVICE_CREATED", "pid=" + android.os.Process.myPid())
        runCatching {
            val recovered = db.recoverInterruptedSessions(System.currentTimeMillis())
            if (recovered > 0) RecorderJournal.event(this,"RECOVERED_INTERRUPTED",recovered.toString())
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "CoreMetrics recording",
                NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RecorderJournal.event(this, "SERVICE_COMMAND", intent?.action ?: "null")
        when (intent?.action) {
            STOP -> {
                RecorderJournal.event(this,"STOP_REQUESTED","id=" + MonitorState.sessionId)
                stopping = true
                MonitorState.message = "Stopping…"
                val runningTask = task
                if (runningTask == null) closeService() else runningTask.cancel()
                refreshTile()
                return START_NOT_STICKY
            }
            START -> {
                if (task?.isActive == true || MonitorState.starting || stopping) {
                    RecorderJournal.event(this,"START_IGNORED","already running/starting/stopping")
                    return START_NOT_STICKY
                }
                RecorderJournal.event(this,"START_REQUESTED","User start")
                // Safety floor while launch/LMK issues are investigated: no 1 Hz IO
                // loop in this build, even if an older v0.2 preference says 1 s.
                val requestedInterval = intent.getLongExtra(INTERVAL, 5000L)
                val interval = if (requestedInterval == 2000L) 2000L else 5000L
                if (requestedInterval != interval)
                    RecorderJournal.event(this,"SAMPLE_INTERVAL_NORMALIZED",
                        "requested=" + requestedInterval + " actual=" + interval)
                val label = (intent.getStringExtra(LABEL) ?: "Gameplay").take(64)
                val config = (intent.getStringExtra(CONFIG) ?: "Unspecified").take(40)
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                    RecorderJournal.event(this,"START_DENIED","POST_NOTIFICATIONS missing")
                    MonitorState.clear("Notifications required to start recording")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                MonitorState.starting = true
                try {
                    val n = notification("CoreMetrics: starting", label)
                    if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    else startForeground(NOTIFICATION_ID, n)
                } catch (e: Exception) {
                    RecorderJournal.event(this,"FOREGROUND_START_FAILED",
                        e.javaClass.simpleName + ": " + (e.message ?: ""))
                    MonitorState.clear("Cannot start foreground recorder: " + e.javaClass.simpleName)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                RecorderJournal.event(this,"FOREGROUND_STARTED","startId=" + startId)
                refreshTile()
                task = scope.launch {
                    var id = 0L
                    var finalStatus = "INTERRUPTED"
                    try {
                        id = withContext(Dispatchers.IO) {
                            db.begin(label, config, System.currentTimeMillis())
                        }
                        RecorderJournal.event(this@RecordingService, "SESSION_PERSISTED", "id=" + id)
                        MonitorState.sessionId = id
                        MonitorState.startedAt = System.currentTimeMillis()
                        MonitorState.sampleCount = 0
                        MonitorState.starting = false
                        MonitorState.running = true
                        MonitorState.message = label + " · " + config
                        refreshTile()
                        val sampler = DirectSampler(application)
                        val detector = ForegroundAttribution(applicationContext)
                        // All seven supplied November Portal captures reported
                        // SURFACEFLINGER_PERMISSION_DENIED. Do not keep starting
                        // dumpsys child processes under heavy emulator load.
                        val frame = FrameEstimate(null,null,0,
                            "SURFACEFLINGER_PERMISSION_DENIED_ON_PORTAL")
                        val hudOn = getSharedPreferences("coremetrics_settings", MODE_PRIVATE)
                            .getBoolean("hud_enabled", false)
                        if (hudOn) {
                            val result = withContext(Dispatchers.Main.immediate) {
                                runCatching { hud.show() }.getOrElse {
                                    "HUD_SHOW_FAILED_" + it.javaClass.simpleName
                                }
                            }
                            MonitorState.hudState = result
                            RecorderJournal.event(this@RecordingService,"HUD_START",result)
                        }
                        if (!hudOn) MonitorState.hudState = "HUD_OFF"
                        var next = SystemClock.elapsedRealtime()
                        var lastNotification = 0L
                        while (isActive && !stopping) {
                            val s = withContext(Dispatchers.IO) {
                                val foreground = detector.current(System.currentTimeMillis())
                                sampler.sample(foreground, frame)
                            }
                            withContext(Dispatchers.IO) { db.add(id, s) }
                            MonitorState.latest = s
                            MonitorState.sampleCount++
                            if (hudOn && hud.attached()) {
                                withContext(Dispatchers.Main.immediate) {
                                    runCatching { hud.update(s,MonitorState.sampleCount) }
                                        .onFailure {
                                            RecorderJournal.event(this@RecordingService,
                                                "HUD_UPDATE_FAILED",it.javaClass.simpleName)
                                        }
                                }
                            }
                            if (MonitorState.sampleCount == 1L) {
                                RecorderJournal.event(this@RecordingService,"FIRST_SAMPLE_WRITTEN","id=" + id)
                            }
                            val elapsed = SystemClock.elapsedRealtime()
                            if (elapsed - lastNotification > 12_000L) {
                                lastNotification = elapsed
                                getSystemService(NotificationManager::class.java).notify(
                                    NOTIFICATION_ID,
                                    notification("● REC · " + MonitorState.sampleCount + " samples",
                                        label + " · " + config)
                                )
                            }
                            next += interval
                            val wait = next - SystemClock.elapsedRealtime()
                            if (wait > 0L) delay(wait) else next = SystemClock.elapsedRealtime()
                        }
                        finalStatus = "COMPLETED"
                    } catch (_: CancellationException) {
                        finalStatus = if (stopping) "COMPLETED" else "INTERRUPTED"
                    } catch (e: Exception) {
                        RecorderJournal.event(this@RecordingService,"SAMPLING_FAILED",
                            e.javaClass.simpleName + ": " + (e.message ?: ""))
                        finalStatus = "FAILED"
                        MonitorState.message = "Recording failure: " + e.javaClass.simpleName
                    } finally {
                        withContext(NonCancellable) {
                            val hudResult = withContext(Dispatchers.Main.immediate) {
                                runCatching { hud.hide() }.getOrElse {
                                    "HUD_REMOVE_EXCEPTION_" + it.javaClass.simpleName
                                }
                            }
                            MonitorState.hudState = hudResult
                            RecorderJournal.event(this@RecordingService,
                                "HUD_STOP",hudResult)
                            withContext(Dispatchers.IO) {
                            if (id != 0L) runCatching {
                                db.finish(id, System.currentTimeMillis(), finalStatus)
                            }
                            RecorderJournal.event(this@RecordingService,"SESSION_FINALIZED",
                                "id=" + id + " status=" + finalStatus +
                                    " samples=" + MonitorState.sampleCount)
                            MonitorState.clear(
                                if (finalStatus == "COMPLETED") "Recording saved"
                                else "Recording " + finalStatus.lowercase()
                            )
                            refreshTile()
                            closeService()
                            }
                        }
                    }
                }
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun notification(title: String, detail: String): Notification {
        val open = PendingIntent.getActivity(this, 200,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 201,
            Intent(this, RecordingService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title).setContentText(detail)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop and save", stop
            ).build()).build()
    }

    private fun refreshTile() = runCatching {
        TileService.requestListeningState(this, ComponentName(this, SessionTile::class.java))
    }.getOrNull()

    private fun closeService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        RecorderJournal.event(this,"SERVICE_DESTROYED",
            "active=" + (task?.isActive == true) + " id=" + MonitorState.sessionId)
        stopping = true
        runCatching { hud.hide() }.onSuccess { RecorderJournal.event(this,"HUD_DESTROY",it) }
        task?.cancel()
        scope.cancel()
        val active = task
        if (active != null) active.invokeOnCompletion { db.close() }
        else db.close()
        super.onDestroy()
    }
}
