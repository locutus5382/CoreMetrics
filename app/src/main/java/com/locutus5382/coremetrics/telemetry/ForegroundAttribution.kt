package com.locutus5382.coremetrics.telemetry

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Optional permission-gated Android PACKAGE attribution, NOT game identification.
 * A first 60-minute lookback supports starting while an emulator is already open.
 */
class ForegroundAttribution(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val ops = context.getSystemService(AppOpsManager::class.java)
    private var lastQueryEnd = 0L
    private var lastProcessed = 0L
    private var pkg: String? = null
    private var activity: String? = null

    fun permissionGranted(): Boolean = try {
        ops?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    fun current(now: Long): String? {
        if (!permissionGranted()) { pkg = null; return null }
        val from = if (lastQueryEnd == 0L) (now - 60L * 60_000L).coerceAtLeast(0L)
            else (lastQueryEnd - 5000L).coerceAtLeast(0L)
        val events = try { usage?.queryEvents(from, now + 1L) }
            catch (_: Exception) { null } ?: return null
        lastQueryEnd = now
        val item = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(item)
            if (item.timeStamp <= lastProcessed || item.timeStamp > now) continue
            lastProcessed = maxOf(lastProcessed, item.timeStamp)
            val p = item.packageName ?: continue
            val a = item.className
            when (item.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> { pkg = p; activity = a }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (p == pkg && (activity == null || activity == a)) {
                        pkg = null; activity = null
                    }
            }
        }
        return pkg?.take(256)
    }
}
