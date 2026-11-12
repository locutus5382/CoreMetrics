package com.locutus5382.coremetrics.monitor

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.locutus5382.coremetrics.telemetry.Snapshot

/**
 * Private IPC endpoint; UID/manifest exported=false. No telemetry reads
 * occur here. Provider may be initialized while recorder is not running.
 */
class StatusProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "status") return null
        val s: Snapshot? = MonitorState.latest
        return Bundle().apply {
            putBoolean("running", MonitorState.running)
            putBoolean("starting", MonitorState.starting)
            putBoolean("standby", MonitorState.standby)
            putLong("id", MonitorState.sessionId)
            putLong("started", MonitorState.startedAt)
            putLong("count", MonitorState.sampleCount)
            putString("message", MonitorState.message)
            putString("hudStatus", MonitorState.hudState)
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
