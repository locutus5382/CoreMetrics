package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.os.PowerManager
import java.io.File

/**
 * Read-only, on-demand capability inspection for the future diagnostics
 * phase. Not called from the 2s/5s telemetry sampler; never writes SQLite.
 * A readable Linux path is not evidence of accurate device-specific data.
 */
internal object DeviceCapabilityProbe {
    fun report(context:Context):String=buildString {
        appendLine("Platform capability probe (at report export, NOT at process exit):")
        val power=context.getSystemService(PowerManager::class.java)
        val status=runCatching {power?.currentThermalStatus?.toString()}
            .getOrNull() ?: "unavailable"
        val headroom=runCatching {power?.getThermalHeadroom(10)}
            .getOrNull()?.takeIf {it.isFinite() && it>=0f}
        appendLine("Android thermal status: "+status+" (OS classification, not a measured clock cap)")
        appendLine("Android 10s thermal headroom: "+
            (headroom?.toString() ?: "unavailable / not supported by device"))
        val meminfo=runCatching {
            File("/proc/meminfo").bufferedReader().use {it.readText()}
        }.getOrNull()
        val swapKiB=meminfo?.let{SystemCapabilityParser.swapUsedKiB(it)}
        appendLine("Swap used: "+(swapKiB?.toString()?.plus(" KiB")
            ?: "unavailable")+" (swap allocation, NOT zRAM physical compression)")
        val psi=runCatching {
            File("/proc/pressure/memory").bufferedReader().use {
                it.readLine()?.take(115)
            }
        }.getOrNull()
        appendLine("Memory PSI (some, current): "+
            (psi ?: "unavailable / restricted by Android"))
        appendLine("These signals are availability probes only; no values stored per session.")
    }
}
