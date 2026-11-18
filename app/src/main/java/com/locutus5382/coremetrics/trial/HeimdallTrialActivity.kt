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
    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot: TrialSnapshot? = null
    private var lastReport = "Not checked yet"

    private data class TrialSnapshot(
        val serviceAlive: Boolean,
        val phase: String?,
        val rawOwner: String?,
        val sessionId: Long,
        val samples: Long,
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
        val mayRequestStart: Boolean
            get() = rawOwner == null && !unfinishedRow &&
                !running && !starting && !standby && !saving &&
                !hudAttached && phase !in setOf(
                    "RETRY_STOP", "RETRY_SAVE", "NEEDS_REPAIR")
        val mayRequestStop: Boolean
            get() = rawOwner == "HEIMDALL_DEBUG" ||
                (serviceAlive && phase in setOf(
                    "RECORDING", "STOPPING", "RETRY_STOP",
                    "FINALIZING", "RETRY_SAVE"))
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
        val persisted = if (rowId > 0L) store.recordingStatus(rowId) else null
        val sampleCount = if (rowId > 0L) store.storedSampleCount(rowId) else 0L
        return TrialSnapshot(
            serviceAlive = provider?.getBoolean("serviceAlive") ?: false,
            phase = provider?.getString("heimdallRecorderPhase"),
            rawOwner = provider?.getString("heimdallRawRecoveryOwner"),
            sessionId = rowId,
            samples = sampleCount,
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
                    appendLine("Recording: ${s.running}; saving: ${s.saving}")
                    appendLine("Retry: ${s.retryNeeded}; repair: ${s.repairNeeded}")
                    appendLine("Unresolved lease: ${s.rawLeaseUnresolved}")
                    appendLine("Issue: ${s.issueCode ?: "NONE"}")
                    appendLine("Message: ${s.statusMessage}")
                }
                details.text = lastReport
                start.isEnabled = s.mayRequestStart
                stop.isEnabled = s.mayRequestStop
            }.onFailure {
                lastSnapshot = null
                lastReport = "Monitor status unavailable: " +
                    it.javaClass.simpleName +
                    ". Do not open a new recording until status is verified."
                details.text = lastReport
                start.isEnabled = false
                stop.isEnabled = false
            }
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
        runCatching {
            startForegroundService(Intent(this, RecordingService::class.java)
                .setAction(RecordingService.HEIMDALL_DEBUG_START))
        }.onFailure {
            details.text = "Start request rejected: " + it.javaClass.simpleName
        }
        lastSnapshot = null
        start.isEnabled = false
        handler.postDelayed({ refresh() }, 650L)
    }

    private fun submitStop() {
        val s = lastSnapshot ?: return
        if (!s.mayRequestStop) {
            refresh()
            return
        }
        runCatching {
            startForegroundService(Intent(this, RecordingService::class.java)
                .setAction(RecordingService.HEIMDALL_DEBUG_STOP))
        }.onFailure {
            details.text = "STOP request rejected: " + it.javaClass.simpleName
        }
        lastSnapshot = null
        stop.isEnabled = false
        handler.postDelayed({ refresh() }, 650L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
