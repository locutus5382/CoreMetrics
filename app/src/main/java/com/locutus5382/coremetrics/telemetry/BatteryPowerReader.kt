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

    /** Contradictory Android current/status signals cannot be valid
     * discharge-comparison samples. Tolerate small near-zero fluctuations.
     * OEM sign conventions require real device validation.
     */
    fun signConflict(currentMicroAmps:Long?,charging:Boolean?):Boolean =
        currentMicroAmps!=null && when(charging) {
            false -> currentMicroAmps>200_000L
            true -> currentMicroAmps < -200_000L
            null -> false
        }

    /** Provenance strings never imply that voltage broadcast has the
     * same update age as BatteryManager.current_now.
     */
    fun source(currentApi:Boolean,voltageBroadcast:Boolean,
        currentAvailable:Boolean,voltageAvailable:Boolean):String =
        if(!currentAvailable || !voltageAvailable)"UNAVAILABLE"
        else "CURRENT="+(if(currentApi)"BATTERY_PROPERTY_CURRENT_NOW" else "SYSFS_CURRENT_NOW")+
            "; VOLTAGE="+(if(voltageBroadcast)"ACTION_BATTERY_CHANGED" else "SYSFS_VOLTAGE_NOW")+
            "; SNAPSHOT_AGE_UNVERIFIED"
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
        // Read sysfs only when its corresponding Android source is absent.
        // These two source readings may have different ages; do not describe
        // their product as synchronized or as a CPU/GPU-only measurement.
        val current = currentApi ?: sysfs("/sys/class/power_supply/battery/current_now")
        val voltageSysfs = if(voltageApi==null)
            sysfs("/sys/class/power_supply/battery/voltage_now")
                ?.div(1000L)?.takeIf { it in 2500L..6000L } else null
        val voltage = voltageApi ?: voltageSysfs
        val source = BatteryPowerMath.source(
            currentApi!=null,voltageApi!=null,current!=null,voltage!=null)
        val conflict=BatteryPowerMath.signConflict(current,charging)
        val power = if(conflict)null
            else BatteryPowerMath.signedDischargeWatts(current,voltage)
        val status = when {
            conflict -> "CURRENT_SIGN_STATUS_CONFLICT; BATTERY_NET_ONLY"
            power == null -> "CURRENT_OR_VOLTAGE_UNAVAILABLE"
            charging == true -> "CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY"
            charging == null -> "BATTERY_STATUS_UNKNOWN; BATTERY_NET_ONLY"
            else -> "DISCHARGING; BATTERY_NET_ONLY"
        }
        return BatteryPowerReading(power, current, voltage, charging, source, status)
    }
}
