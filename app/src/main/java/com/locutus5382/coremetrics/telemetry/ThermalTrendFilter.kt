package com.locutus5382.coremetrics.telemetry

import kotlin.math.max

/**
 * Presentation-only CPU thermal smoothing. Never modifies the sampled raw sensor
 * or SQLite/CSV data. Trends are estimated from the existing recorder samples,
 * not from an extra polling loop.
 */
class ThermalTrendFilter(private val alpha: Double = 0.35) {
    init { require(alpha > 0.0 && alpha <= 1.0) }
    enum class Direction { RISING, FALLING, STABLE, UNKNOWN }
    data class Reading(
        val smoothedC: Double?,
        val rawC: Double?,
        val rawPeakC: Double?,
        val slopeCPerMinute: Double?,
        val direction: Direction
    )
    private val history = ArrayDeque<Pair<Long, Double>>()
    private var previousTime: Long? = null
    private var filtered: Double? = null
    private var peak: Double? = null

    fun reset() {
        history.clear()
        previousTime = null
        filtered = null
        peak = null
    }

    fun update(timeMs: Long, rawC: Double?): Reading {
        if (rawC == null || !rawC.isFinite() || rawC !in -20.0..150.0)
            return Reading(null, null, peak, null, Direction.UNKNOWN)
        // Unexpected clock regression or long pause: don't compare unrelated readings.
        if (previousTime != null && (timeMs <= previousTime!! ||
                    timeMs - previousTime!! > 60_000L)) reset()
        val current = filtered?.let { (alpha * rawC) + (1.0 - alpha) * it } ?: rawC
        filtered = current
        peak = max(peak ?: rawC, rawC)
        previousTime = timeMs
        history.addLast(timeMs to current)
        while (history.size > 1 && history.first().first < timeMs - 15_000L)
            history.removeFirst()
        val earliest = history.first()
        val elapsed = timeMs - earliest.first
        val slope = if (elapsed >= 5_000L)
            (current - earliest.second) * 60_000.0 / elapsed.toDouble()
            else null
        val direction = when {
            slope == null -> Direction.UNKNOWN
            slope > 1.5 -> Direction.RISING
            slope < -1.5 -> Direction.FALLING
            else -> Direction.STABLE
        }
        return Reading(current, rawC, peak, slope, direction)
    }
}
