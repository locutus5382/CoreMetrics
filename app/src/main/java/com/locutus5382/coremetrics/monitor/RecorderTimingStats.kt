package com.locutus5382.coremetrics.monitor

import java.util.Locale
import kotlin.math.ceil

/**
 * Bounded per-process diagnostic timing. Does not write at each tick.
 * Includes dispatcher waits: these are elapsed times, not process CPU%.
 */
internal class RecorderTimingStats(private val capacity: Int = 256) {
    init { require(capacity in 4..4096) }
    private val collector = DoubleArray(capacity)
    private val database = DoubleArray(capacity)
    private val hud = DoubleArray(capacity) { Double.NaN }
    private val total = DoubleArray(capacity)
    private val lateness = DoubleArray(capacity)
    private var cursor = 0
    var count: Long = 0
        private set

    fun record(
        collectorMs: Double,
        databaseMs: Double,
        hudMs: Double?,
        totalMs: Double,
        startLateMs: Double
    ) {
        if (!(collectorMs.isFinite() && databaseMs.isFinite() &&
                    totalMs.isFinite() && startLateMs.isFinite()) ||
            collectorMs < 0.0 || databaseMs < 0.0 ||
            totalMs < 0.0 || startLateMs < 0.0) return
        val i = cursor
        collector[i] = collectorMs
        database[i] = databaseMs
        hud[i] = hudMs?.takeIf { it.isFinite() && it >= 0.0 } ?: Double.NaN
        total[i] = totalMs
        lateness[i] = startLateMs
        cursor = (cursor + 1) % capacity
        count++
    }

    private fun p95(samples: DoubleArray): Double? {
        val valid = samples.take(count.coerceAtMost(capacity.toLong()).toInt())
            .filter { it.isFinite() && it >= 0.0 }.sorted()
        if (valid.isEmpty()) return null
        return valid[(ceil(valid.size * 0.95).toInt() - 1)
            .coerceIn(0, valid.lastIndex)]
    }

    fun report(): String {
        fun fmt(x: Double?): String = x?.let {
            String.format(Locale.US, "%.1f", it)
        } ?: "na"
        return "ticks=" + count + " p95_ms collector=" + fmt(p95(collector)) +
            " db=" + fmt(p95(database)) + " hud=" + fmt(p95(hud)) +
            " total=" + fmt(p95(total)) + " start_late=" + fmt(p95(lateness)) +
            " window=" + minOf(count, capacity.toLong())
    }
}
