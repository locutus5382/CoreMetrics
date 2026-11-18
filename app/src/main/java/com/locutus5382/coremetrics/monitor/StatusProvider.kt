package com.locutus5382.coremetrics.monitor

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Binder
import android.os.Process
import com.locutus5382.coremetrics.BuildConfig
import com.locutus5382.coremetrics.telemetry.Snapshot

/**
 * Private IPC endpoint; UID/manifest exported=false. No telemetry reads
 * occur here. Provider may be initialized while recorder is not running.
 */
class StatusProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // exported=false is primary protection; UID check is defense-in-depth
        // for alternate provider entry points on modified Android systems.
        if(Binder.getCallingUid()!=Process.myUid() || method!="status")
            return null
        val s: Snapshot? = MonitorState.latest
        // A status-provider query may start the :monitor process without
        // starting the Service. A fresh lease is pending recovery, NOT idle.
        val lease=if(!MonitorState.serviceCreated)
            context?.let{RecorderLease.recover(it)} else null
        return Bundle().apply {
            putBoolean("serviceAlive",MonitorState.serviceCreated)
            putBoolean("pendingRecovery",lease!=null)
            putBoolean("recoveringRecording",lease?.recording==true)
            if (BuildConfig.DEBUG) {
                // This key MUST exist whether the service is alive or not.
                // A missing key previously triggered the trial's fail-closed
                // monitor-contract exception and disabled its controls.
                val raw = context?.let { RecorderLease.rawSession(it) }
                val unresolved = raw?.active == true && (
                    raw.owner == RecorderLease.Owner.UNKNOWN ||
                        (!MonitorState.serviceCreated && lease == null))
                putBoolean("heimdallUnresolvedRawLease", unresolved)
            }
            putLong("recoverySessionId",lease?.id?:0L)
            putBoolean("running", MonitorState.running)
            putBoolean("starting", MonitorState.starting)
            putBoolean("standby", MonitorState.standby)
            // M2-M: Trial Activity rejects missing mandatory fields. Keep
            // the monitor->console contract explicit and instrumented.
            putBoolean("saving", MonitorState.saving)
            putBoolean("hudAttached", MonitorState.hudAttached)
            putLong("id", MonitorState.sessionId)
            putLong("started", MonitorState.startedAt)
            putLong("count", MonitorState.sampleCount)
            putString("message", MonitorState.message)
            if (BuildConfig.DEBUG) {
                putInt("heimdallStatusContractVersion", 1)
                // Diagnostic presentation only: never a recorder authority token.
                putString("heimdallRecorderPhase",
                    MonitorState.heimdallRecorderPhase)
                putBoolean("heimdallStopOrSaveRetry",
                    MonitorState.heimdallNeedsRetry)
                putBoolean("heimdallRecorderNeedsRepair",
                    MonitorState.heimdallNeedsRepair)
                putLong("heimdallRecorderOriginalId",
                    MonitorState.heimdallOriginalSessionId)
                putString("heimdallRecorderIssueCode",
                    MonitorState.heimdallIssueCode)
            }
            putString("hudStatus", MonitorState.hudState)
            putBoolean("hudVisible",MonitorState.hudVisible)
            putBoolean("hudLocked", MonitorState.hudLocked)
            putLong("hudOptionRevision",MonitorState.hudOptionRevision)
            MonitorState.hudOptions?.let { h ->
                putFloat("hudOptOpacity",h.opacity)
                putString("hudOptSize",h.size)
                putString("hudOptLayout",h.layout)
                putString("hudOptIndicator",h.indicatorColor)
                putBoolean("hudOptBlink",h.blink)
                putBoolean("hudOptCpuTemp",h.showCpuTemp)
                putBoolean("hudOptGpuTemp",h.showGpuTemp)
                putBoolean("hudOptCpuLoad",h.showCpuLoad)
                putBoolean("hudOptGpuLoad",h.showGpuLoad)
                putBoolean("hudOptRam",h.showRam)
                putBoolean("hudOptPower",h.showPower)
                putBoolean("hudOptFps",h.showFps)
                putBoolean("hudOptSparkline",false)
                putBoolean("hudOptAdaptive",h.adaptiveColors)
            }
            putString("cpu", s?.cpuTempC?.let { "%.1f°C".format(java.util.Locale.US,it) })
            putString("gpu", s?.gpuTempC?.let { "%.1f°C".format(java.util.Locale.US,it) })
            putString("cpuLoad", s?.cpuPercent?.toString())
            putString("gpuLoad", s?.gpuPercent?.toString())
            putString("peakCore", s?.busiestPercent?.toString())
            putString("ram", s?.ramGiB?.let { "%.2f GiB".format(java.util.Locale.US,it) })
            putString("clock", s?.cpuClockSummary)
            putString("observedLimits", s?.observedCpuLimits)
            putString("coreLoads", s?.perCoreLoads)
            putString("power", s?.signedBatteryWatts?.let {
                "%.2f W".format(java.util.Locale.US,it)
            })
            putString("powerStatus", s?.batteryPowerStatus)
            putString("powerSource", s?.batteryPowerSource)
            putString("fps", s?.surfaceFps?.let {
                "%.1f".format(java.util.Locale.US,it)
            })
            putString("frameMs", s?.surfaceFrameMs?.let {
                "%.1f ms".format(java.util.Locale.US,it)
            })
            putString("fpsStatus", s?.surfaceFpsStatus)
            putString("app", s?.appPackage)
            putLong("lastSampleAt", s?.timeMs ?: 0L)
            // Debug-only service-owned diagnostic observations. This is not
            // recording truth and never authorizes resume, STOP or overlay work.
            if (BuildConfig.DEBUG) putInt("heimdallMonitorPid", Process.myPid())
            if (BuildConfig.DEBUG) context?.let { ctx ->
                // Read from the actual :monitor owner process. The UI process
                // must NOT use its own SharedPreferences cache as lease truth.
                val owner = RecorderLease.rawOwner(ctx)
                val saved = RecorderLease.recover(ctx)
                putString("heimdallRawRecoveryOwner", owner?.name)
                putLong("heimdallRecoveryLeaseId", saved?.id ?: 0L)
                putBoolean("heimdallRecoveryStopPersisted",
                    saved?.stopRequested == true)
            }
            if (BuildConfig.DEBUG) HeimdallServiceShadowAudit.snapshot()?.let { audit ->
                putString("heimdallShadowEpoch", audit.epoch)
                putLong("heimdallShadowSequence", audit.sequence)
                putLong("heimdallShadowSessionId", audit.sessionId ?: 0L)
                putString("heimdallShadowStop", audit.stop.name)
                putBoolean("heimdallShadowSamplerExited", audit.samplerExited)
                putBoolean("heimdallShadowRecoveredTerminal", audit.recoveredTerminal)
                putString("heimdallShadowFinish", audit.finish.name)
                putBoolean("heimdallShadowServiceDestroyed", audit.serviceDestroyed)
                putStringArrayList("heimdallShadowFindings",
                    ArrayList(audit.findings.map { it.name }.sorted()))
            }
        }
    }
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<String>?): Int = 0
}
