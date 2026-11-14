package com.locutus5382.coremetrics.telemetry

import java.io.Reader

/** Bounded, read-only procfs parsers. No Android dependencies for JVM tests. */
internal object ProcTelemetryReader {
    private const val MAX_CPU_LINES = 64
    private const val MAX_MEM_LINES = 128

    fun cpuLines(source: Reader): List<String> = source.buffered().use { input ->
        val rows = ArrayList<String>(16)
        var lines = 0
        while (lines++ < MAX_CPU_LINES) {
            val line = input.readLine() ?: break
            val name = line.takeWhile { !it.isWhitespace() }
            if (name != "cpu" && !(name.startsWith("cpu") &&
                    name.length > 3 && name.drop(3).all { it.isDigit() })) break
            if (line.length > 2048) break
            rows.add(line)
        }
        rows
    }

    data class MemorySnapshot(val usedRamGiB: Double?, val swapUsedGiB: Double?)

    /** Read all four meminfo fields in one streaming pass, returning null on missing input. */
    fun memorySnapshot(source: Reader): MemorySnapshot {
        var total: Long? = null
        var available: Long? = null
        var swapTotal: Long? = null
        var swapFree: Long? = null
        source.buffered().use { input ->
            var lines = 0
            while (lines++ < MAX_MEM_LINES) {
                val line = input.readLine() ?: break
                if (line.length > 2048) continue
                val key = line.substringBefore(':')
                if (key != "MemTotal" && key != "MemAvailable" &&
                    key != "SwapTotal" && key != "SwapFree") continue
                val value = line.substringAfter(':', "").trim()
                    .takeWhile { !it.isWhitespace() }.toLongOrNull()
                    ?.takeIf { it >= 0L } ?: continue
                when (key) {
                    "MemTotal" -> total = value
                    "MemAvailable" -> available = value
                    "SwapTotal" -> swapTotal = value
                    "SwapFree" -> swapFree = value
                }
                if (total != null && available != null &&
                    swapTotal != null && swapFree != null) break
            }
        }
        val ram = if (total != null && total > 0L && available != null &&
            available <= total) (total - available).toDouble() / 1048576.0 else null
        val swap = if (swapTotal != null && swapTotal > 0L && swapFree != null &&
            swapFree <= swapTotal) (swapTotal - swapFree).toDouble() / 1048576.0 else null
        return MemorySnapshot(ram, swap)
    }

    /** Legacy wrapper; same RAM semantics as before. */
    fun usedGiB(source: Reader): Double? = memorySnapshot(source).usedRamGiB

    /** PSI some avg10 is a percentage of time at least one task was stalled. */
    fun memoryPsiSomeAvg10(source: Reader): Double? = source.buffered().use { input ->
        repeat(8) {
            val line = input.readLine() ?: return@use null
            if (line.length > 2048 || !line.startsWith("some ")) return@repeat
            val value = line.split(Regex("""\s+"""))
                .firstOrNull { it.startsWith("avg10=") }
                ?.substringAfter('=')?.toDoubleOrNull()
            return@use value?.takeIf { it.isFinite() && it in 0.0..100.0 }
        }
        null
    }
}
