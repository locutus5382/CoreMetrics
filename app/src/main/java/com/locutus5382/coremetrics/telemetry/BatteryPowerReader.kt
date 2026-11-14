package com.locutus5382.coremetrics.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * Net battery-terminal power; not CPU/GPU or wall power.
 * Current sign: negative discharging, positive charging.
 */
data class BatteryPowerReading(
    val signedBatteryWatts: Double?,
    val currentMicroAmps: Long?,
    val voltageMilliVolts: Long?,
    val charging: Boolean?,
    val source: String,
    val status: String
)

object BatteryPowerMath {
    fun signedDischargeWatts(currentMicroAmps: Long?, voltageMilliVolts: Long?): Double? {
        if (currentMicroAmps == null || voltageMilliVolts == null) return null
        if (currentMicroAmps !in -20_000_000L..20_000_000L) return null
        if (voltageMilliVolts !in 2500L..6000L) return null
        return -currentMicroAmps.toDouble() * voltageMilliVolts / 1_000_000_000.0
    }
}

class BatteryPowerReader(private val context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)
    private fun sysfs(path: String): Long? = try {
        val file = File(path)
        if (file.isFile && file.canRead())
            file.bufferedReader().use { it.readLine()?.trim()?.toLongOrNull() }
        else null
    } catch (_: Exception) { null }

    /** Standalone clients retain the old read() API. DirectSampler passes the
     * same battery broadcast to read(state) to avoid a second IPC per tick. */
    fun read(): BatteryPowerReading {
        val state=try {
            context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }
        return read(state)
    }

    /** A null state means unavailable, NOT permission to fetch a second copy. */
    fun read(state: Intent?): BatteryPowerReading {
        val batteryStatus = state?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = when (batteryStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING -> false
            else -> null
        }
        val currentApi = try {
            manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?.takeUnless { it == Long.MIN_VALUE || it == Int.MIN_VALUE.toLong() }
                ?.takeIf { it in -20_000_000L..20_000_000L }
        } catch (_: Exception) { null }
        val voltageApi = state?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.toLong()?.takeIf { it in 2500L..6000L }
        val current = currentApi ?: sysfs("/sys/class/power_supply/battery/current_now")
        val voltage = voltageApi ?: sysfs("/sys/class/power_supply/battery/voltage_now")
            ?.div(1000L)
        val source = when {
            currentApi != null && voltageApi != null -> "ANDROID_BATTERY_MANAGER"
            current != null && voltage != null -> "ANDROID_SYSFS_READONLY"
            else -> "UNAVAILABLE"
        }
        val power = BatteryPowerMath.signedDischargeWatts(current, voltage)
        val status = when {
            power == null -> "CURRENT_OR_VOLTAGE_UNAVAILABLE"
            charging == true -> "CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY"
            charging == null -> "BATTERY_STATUS_UNKNOWN; BATTERY_NET_ONLY"
            else -> "DISCHARGING; BATTERY_NET_ONLY"
        }
        return BatteryPowerReading(power, current, voltage, charging, source, status)
    }
}
