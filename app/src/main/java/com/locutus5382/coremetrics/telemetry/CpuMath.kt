package com.locutus5382.coremetrics.telemetry

import kotlin.math.roundToInt

/** Pure parser and CPU delta logic; no Android APIs or privileged operations. */
object CpuMath {
    private val whitespace=Regex("""\s+""")
    data class Times(val idle: Long, val total: Long)

    fun parse(line: String?): Times? {
        val fields = line?.trim()?.split(whitespace) ?: return null
        if (fields.size < 5 || !fields[0].startsWith("cpu")) return null
        val ticks = fields.drop(1).map { it.toLongOrNull() ?: return null }
        if (ticks.any { it < 0L }) return null
        return Times(ticks[3] + ticks.getOrElse(4) { 0L }, ticks.sum())
    }

    /** maxByOrNull always prefers the *first* item on an exact tie.
     * A tied or idle interval has no identifiable busiest core. */
    /** Ties invalidate core identity, not observed peak utilization. */
    fun maxCoreLoad(loads:List<Pair<String,Int>>):Int? =
        loads.maxOfOrNull{it.second}?.takeIf{it>0}

    fun uniqueBusiest(loads:List<Pair<String,Int>>):Pair<String,Int>? {
        val max=loads.maxOfOrNull{it.second}?:return null
        if(max<=0)return null
        return loads.singleOrNull{it.second==max}
    }

    fun percent(old: Times?, now: Times?): Int? {
        if (old == null || now == null) return null
        val elapsed = now.total - old.total
        val idle = now.idle - old.idle
        if (elapsed <= 0 || idle < 0 || idle > elapsed) return null
        return ((elapsed - idle) * 100.0 / elapsed).roundToInt().coerceIn(0, 100)
    }
}
