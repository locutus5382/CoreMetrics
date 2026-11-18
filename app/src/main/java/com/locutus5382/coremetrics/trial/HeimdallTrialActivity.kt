package com.locutus5382.coremetrics.trial

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.locutus5382.coremetrics.BuildConfig
import com.locutus5382.coremetrics.data.UiSessionStoreOwner
import com.locutus5382.coremetrics.monitor.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Odin test console, available ONLY in the independently installed
 * heimdallTrial build. No production UI/QS path can invoke these actions.
 *
 * This Activity is not exported. Service admission always revalidates both
 * the lease and the SQLite row; these button rules are presentation only.
 */
class HeimdallTrialActivity : ComponentActivity() {
    private val midnight = Color.rgb(12, 21, 38)
    private val white = Color.rgb(238, 242, 247)
    private val grey = Color.rgb(165, 175, 190)
    private val cobalt = Color.rgb(40, 85, 197)
    private val amber = Color.rgb(242, 169, 0)
    private lateinit var details: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var copy: Button
    private lateinit var verify: Button
    private lateinit var verifySave: Button
    private var growthCheckInFlight = false
    private var savedCheckInFlight = false
    private var growthReport = "Sample growth: NOT VERIFIED"
    private var savedReport = "Saved session: NOT VERIFIED"
    private var stopBaseline: TrialSavedSessionEvidence? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot: TrialSnapshot? = null
    private var lastReport = "Not checked yet"

    private data class TrialSnapshot(
        val monitorStatusVerified: Boolean,
        val serviceAlive: Boolean,
        val phase: String?,
        val rawOwner: String?,
        val sessionId: Long,
        val samples: Long,
        val endedAtMs: Long?,
        val totalSessionRows: Long,
        val statusMessage: String,
        val retryNeeded: Boolean,
        val repairNeeded: Boolean,
        val rawLeaseUnresolved: Boolean,
        val running: Boolean,
        val starting: Boolean,
        val standby: Boolean,
        val saving: Boolean,
        val hudAttached: Boolean,
        val unfinishedRow: Boolean,
        val persistedRowStatus: String?,
        val issueCode: String?,
    ) {
        private val admission: HeimdallTrialAdmissionEvidence
            get() = HeimdallTrialAdmissionEvidence(
                monitorStatusVerified = monitorStatusVerified,
                rawOwner = rawOwner,
                unfinishedRow = unfinishedRow,
                rawLeaseUnresolved = rawLeaseUnresolved,
                repairNeeded = repairNeeded,
                running = running, starting = starting,
                standby = standby, saving = saving,
                hudAttached = hudAttached, phase = phase,
            )
        val mayRequestStart: Boolean
            get() = HeimdallTrialAdmission.mayRequestStart(admission)
        val mayRequestStop: Boolean
            get() = HeimdallTrialAdmission.mayRequestStop(admission)
        val mayVerifySamples: Boolean
            get() = monitorStatusVerified &&
                phase == "RECORDING" && rawOwner == "HEIMDALL_DEBUG" &&
                persistedRowStatus == "RECORDING" &&
                !rawLeaseUnresolved && !repairNeeded && sessionId > 0L
        val sampleEvidence: TrialSampleEvidence
            get() = TrialSampleEvidence(
                statusVerified = monitorStatusVerified,
                phase = phase, owner = rawOwner,
                sessionId = sessionId, persistedSamples = samples,
                rowStatus = persistedRowStatus,
                unresolvedLease = rawLeaseUnresolved,
            )
        val savedEvidence: TrialSavedSessionEvidence
            get() = TrialSavedSessionEvidence(
                statusVerified = monitorStatusVerified,
                enginePhase = phase, rawOwner = rawOwner,
                originalSessionId = sessionId,
                rowStatus = persistedRowStatus, endedAtMs = endedAtMs,
                persistedSamples = samples, totalSessionRows = totalSessionRows,
                unfinishedRowExists = unfinishedRow,
                rawLeaseUnresolved = rawLeaseUnresolved,
                repairNeeded = repairNeeded,
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG || !BuildConfig.HEIMDALL_TRIAL) {
            finish()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 36)
            setBackgroundColor(midnight)
        }
        fun line(text: String, size: Float, color: Int): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                setPadding(0, 4, 0, 16)
                panel.addView(this)
            }
        fun action(label: String, color: Int, block: () -> Unit): Button =
            Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(white)
                backgroundTintList = ColorStateList.valueOf(color)
                setOnClickListener { block() }
                panel.addView(this, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        line("HEIMDALL · DEVICE TRIAL", 23f, white)
        line("Isolated test installation · not a production release", 14f, amber)
        line("Records into this trial application's separate SQLite database. " +
            "The original CoreMetrics installation and its sessions are not modified. " +
            "Run a short test first; do not clear unfinished session evidence.",
            13f, grey)
        details = line("Checking recorder and original-session lease…", 14f, white)
        start = action("Start Heimdall test recording", cobalt) {
            submitStart()
        }
        stop = action("STOP / retry finalization", cobalt) {
            submitStop()
        }
        action("Refresh verified status", cobalt) { refresh() }
        verify = action("Verify persisted sample growth (8 seconds)", amber) {
            verifySampleGrowth()
        }
        verify.isEnabled = false
        verifySave = action("Verify completed STOP and saved session", amber) {
            verifySavedSession()
        }
        verifySave.isEnabled = false
        copy = action("Copy diagnostic summary", cobalt) {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                ClipData.newPlainText("Heimdall trial status", lastReport))
        }
        line("Observe session ID and sample count, then STOP and verify the " +
            "same session becomes finalized. Do not force-stop this installation " +
            "before your first successful ordinary START/STOP test. " +
            "An unavailable original lease is a repair condition, not permission " +
            "to start a replacement recording.", 13f, grey)
        setContentView(ScrollView(this).apply {
            addView(panel)
            isFillViewport = true
        })
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG && BuildConfig.HEIMDALL_TRIAL) refresh()
    }

    private suspend fun readVerified(): TrialSnapshot {
        val provider = contentResolver.call(
            Uri.parse("content://$packageName.monitor.status"),
            "status", null, null)
            ?: error("MONITOR_STATUS_UNAVAILABLE")
        // A missing data contract cannot be interpreted as idle. Only the
        // local, app-private :monitor process may publish these fields.
        check(provider.containsKey("heimdallRawRecoveryOwner") &&
            provider.containsKey("serviceAlive") &&
            provider.containsKey("running") &&
            provider.containsKey("starting") &&
            provider.containsKey("saving")) {
                "MONITOR_STATUS_INCOMPLETE"
            }
        val store = UiSessionStoreOwner.get(applicationContext)
        val unfinished = store.hasUnfinishedRecording()
        // A completed STOP clears the live lease. Retain only the most recent
        // displayed ID in trial UI prefs to inspect its ORIGINAL persisted row.
        // This display hint cannot resume or authorize any session.
        val seen = provider?.getLong("heimdallRecorderOriginalId", 0L)
            ?.takeIf { it > 0L }
            ?: provider?.getLong("heimdallRecoveryLeaseId", 0L)
                ?.takeIf { it > 0L }
        val prefs = getSharedPreferences("heimdall_trial_ui", MODE_PRIVATE)
        if (seen != null) prefs.edit().putLong(
            "last_seen_session_id", seen).apply()
        val rowId = seen ?: prefs.getLong("last_seen_session_id", 0L)
        val originalRow = if (rowId > 0L) store.sessionById(rowId) else null
        val persisted = originalRow?.status
        val sampleCount = if (rowId > 0L) store.storedSampleCount(rowId) else 0L
        val totalRows = store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM sessions", null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        return TrialSnapshot(
            monitorStatusVerified = true,
            serviceAlive = provider.getBoolean("serviceAlive"),
            phase = provider?.getString("heimdallRecorderPhase"),
            rawOwner = provider?.getString("heimdallRawRecoveryOwner"),
            sessionId = rowId,
            samples = sampleCount,
            endedAtMs = originalRow?.endedAt,
            totalSessionRows = totalRows,
            statusMessage = provider?.getString("message") ?: "Status unavailable",
            retryNeeded = provider?.getBoolean("heimdallStopOrSaveRetry") ?: false,
            repairNeeded = provider?.getBoolean("heimdallRecorderNeedsRepair") ?: false,
            rawLeaseUnresolved =
                provider?.getBoolean("heimdallUnresolvedRawLease") ?: false,
            running = provider?.getBoolean("running") ?: false,
            starting = provider?.getBoolean("starting") ?: false,
            standby = provider?.getBoolean("standby") ?: false,
            saving = provider?.getBoolean("saving") ?: false,
            hudAttached = provider?.getBoolean("hudAttached") ?: false,
            unfinishedRow = unfinished,
            persistedRowStatus = persisted,
            issueCode = provider?.getString("heimdallRecorderIssueCode"),
        )
    }

    private fun refresh() {
        details.text = "Reading monitor-owned status and session database…"
        start.isEnabled = false
        stop.isEnabled = false
        verify.isEnabled = false
        verifySave.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readVerified() }
            }
            result.onSuccess { s ->
                lastSnapshot = s
                lastReport = buildString {
                    appendLine("HEIMDALL Trial · isolated appId=$packageName")
                    appendLine("Service alive: ${s.serviceAlive}")
                    appendLine("Engine phase: ${s.phase ?: "UNAVAILABLE"}")
                    appendLine("Original session ID: ${s.sessionId}")
                    appendLine("Lease owner: ${s.rawOwner ?: "NONE"}")
                    appendLine("Unfinished SQLite row: ${s.unfinishedRow}")
                    appendLine("Persisted SQLite row: ${s.persistedRowStatus ?: "NONE"}")
                    appendLine("Persisted samples: ${s.samples}")
                    appendLine("SQLite sessions: ${s.totalSessionRows}")
                    appendLine("Original session endedAt: ${s.endedAtMs ?: "NONE"}")
                    appendLine("Recording: ${s.running}; saving: ${s.saving}")
                    appendLine("Retry: ${s.retryNeeded}; repair: ${s.repairNeeded}")
                    appendLine("Unresolved lease: ${s.rawLeaseUnresolved}")
                    appendLine("Issue: ${s.issueCode ?: "NONE"}")
                    appendLine("Message: ${s.statusMessage}")
                    appendLine(growthReport)
                    appendLine(savedReport)
                }
                details.text = lastReport
                start.isEnabled = s.mayRequestStart
                stop.isEnabled = s.mayRequestStop
                verify.isEnabled = s.mayVerifySamples && !growthCheckInFlight
                verifySave.isEnabled = stopBaseline != null && !savedCheckInFlight
            }.onFailure {
                lastSnapshot = null
                lastReport = "Monitor status unavailable: " +
                    it.javaClass.simpleName +
                    ". Do not open a new recording until status is verified."
                details.text = lastReport
                start.isEnabled = false
                stop.isEnabled = false
                verify.isEnabled = false
                verifySave.isEnabled = false
            }
        }
    }

    /**
     * User-requested, non-destructive evidence. A sample count from the UI
     * cannot be trusted: re-read the private monitor AND original SQLite row
     * at both ends, compare one immutable original session ID.
     */
    private fun verifySampleGrowth() {
        if (growthCheckInFlight || lastSnapshot?.mayVerifySamples != true) {
            refresh()
            return
        }
        growthCheckInFlight = true
        verify.isEnabled = false
        details.text = "Verifying native SQLite sample growth for 8 seconds… " +
            "STOP remains available if needed."
        lifecycleScope.launch {
            val first = withContext(Dispatchers.IO) {
                runCatching { readVerified() }
            }
            if (first.isFailure || first.getOrNull()?.mayVerifySamples != true) {
                growthReport = "Sample growth: NOT VERIFIED — first snapshot unavailable"
            } else {
                delay(8_000L)
                val second = withContext(Dispatchers.IO) {
                    runCatching { readVerified() }
                }
                growthReport = if (second.isFailure) {
                    "Sample growth: NOT VERIFIED — second snapshot unavailable"
                } else {
                    val check = HeimdallTrialSampleGrowth.evaluate(
                        first.getOrThrow().sampleEvidence,
                        second.getOrThrow().sampleEvidence)
                    "Sample growth: ${check.result.name}; original ID " +
                        "${check.sessionId}; new persisted samples +${check.delta}"
                }
            }
            growthCheckInFlight = false
            refresh()
        }
    }

    private fun verifySavedSession() {
        val baseline = stopBaseline ?: return
        if (savedCheckInFlight) return
        savedCheckInFlight = true
        verifySave.isEnabled = false
        details.text = "Checking original session finalization and durable samples…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readVerified() }
            }
            savedReport = if (result.isFailure) {
                "Saved session: STATUS_UNAVAILABLE; original ID " +
                    baseline.originalSessionId
            } else {
                val check = HeimdallTrialSavedSession.evaluate(
                    baseline, result.getOrThrow().savedEvidence)
                "Saved session: ${check.result.name}; original ID " +
                    "${check.originalSessionId}; retained samples ${check.preservedSamples}"
            }
            savedCheckInFlight = false
            refresh()
        }
    }

    private fun submitStart() {
        val s = lastSnapshot ?: return
        if (!s.mayRequestStart) {
            refresh()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            details.text = "Allow notification permission, then tap Start again."
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 19)
            return
        }
        // A displayed snapshot is advisory and can become stale after
        // service death or a Quick Settings command. Re-read BOTH monitor
        // status and the real SQLite row before requesting a native START.
        // This is a UI defense; RecordingService independently revalidates.
        lastSnapshot = null
        start.isEnabled = false
        stop.isEnabled = false
        details.text = "Rechecking exclusive recording ownership…"
        lifecycleScope.launch {
            val checked = withContext(Dispatchers.IO) {
                runCatching { readVerified() }
            }
            checked.onSuccess { fresh ->
                if (!fresh.mayRequestStart) {
                    lastReport = "Native START blocked: recording owner, " +
                        "recovery lease, or unfinished session needs review."
                    details.text = lastReport
                    refresh()
                    return@onSuccess
                }
                // This proof belongs to a previous STOP only. Never reuse it
                // to validate a later newly created session.
                stopBaseline = null
                savedReport = "Saved session: NOT VERIFIED"
                runCatching {
                    startForegroundService(Intent(
                        this@HeimdallTrialActivity, RecordingService::class.java)
                        .setAction(RecordingService.HEIMDALL_DEBUG_START))
                }.onFailure {
                    details.text = "Start request rejected: " + it.javaClass.simpleName
                }
                handler.postDelayed({ refresh() }, 650L)
            }.onFailure {
                lastReport = "Could not verify recorder status: " +
                    it.javaClass.simpleName + ". Native START was not requested."
                details.text = lastReport
                start.isEnabled = false
                stop.isEnabled = false
            }
        }
    }

    private fun submitStop() {
        val snapshot = lastSnapshot ?: return
        if (!snapshot.mayRequestStop) {
            refresh()
            return
        }
        // Recheck the authoritative owner on the IO dispatcher. A stale
        // display phase must not send STOP to a different session/engine.
        lastSnapshot = null
        stop.isEnabled = false
        start.isEnabled = false
        verify.isEnabled = false
        verifySave.isEnabled = false
        details.text = "Confirming original recording owner before STOP…"
        lifecycleScope.launch {
            val checked = withContext(Dispatchers.IO) {
                runCatching { readVerified() }
            }
            checked.onSuccess { fresh ->
                if (!fresh.mayRequestStop) {
                    details.text = "STOP not sent: the original owner could " +
                        "not be verified. Refresh and inspect diagnostics."
                    refresh()
                    return@onSuccess
                }
                if (fresh.rawOwner == "HEIMDALL_DEBUG" &&
                    fresh.persistedRowStatus == "RECORDING" &&
                    fresh.sessionId > 0L &&
                    (stopBaseline == null ||
                        stopBaseline?.originalSessionId != fresh.sessionId)) {
                    stopBaseline = fresh.savedEvidence
                    savedReport = "Saved session: STOP issued; verify after saving"
                }
                runCatching {
                    startForegroundService(Intent(
                        this@HeimdallTrialActivity, RecordingService::class.java)
                        .setAction(RecordingService.HEIMDALL_DEBUG_STOP))
                }.onFailure {
                    details.text = "STOP request rejected: " +
                        it.javaClass.simpleName
                }
                handler.postDelayed({ refresh() }, 650L)
            }.onFailure {
                details.text = "STOP not sent: monitor status unavailable (" +
                    it.javaClass.simpleName + "). Use the existing recorder " +
                    "notification if the monitoring service is still active."
                stop.isEnabled = false
                start.isEnabled = false
                verifySave.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
