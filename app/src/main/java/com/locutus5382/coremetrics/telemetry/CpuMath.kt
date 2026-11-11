package com.locutus5382.coremetrics.telemetry

import kotlin.math.roundToInt

/** Pure parser and CPU delta logic; no Android APIs or privileged operations. */
object CpuMath {
    data class Times(val idle: Long, val total: Long)

    fun parse(line: String?): Times? {
        val fields = line?.trim()?.split(Regex("\\s+")) ?: return null
        if (fields.size < 5 || !fields[0].startsWith("cpu")) return null
        val ticks = fields.drop(1).map { it.toLongOrNull() ?: return null }
        if (ticks.any { it < 0L }) return null
        return Times(ticks[3] + ticks.getOrElse(4) { 0L }, ticks.sum())
    }

    fun percent(old: Times?, now: Times?): Int? {
        if (old == null || now == null) return null
        val elapsed = now.total - old.total
        val idle = now.idle - old.idle
        if (elapsed <= 0 || idle < 0 || idle > elapsed) return null
        return ((elapsed - idle) * 100.0 / elapsed).roundToInt().coerceIn(0, 100)
    }
}
