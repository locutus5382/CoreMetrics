package com.locutus5382.coremetrics.telemetry

/**
 * Monotonic-time backoff for read-only, OPTIONAL OS sensor probes.
 *
 * A valid zero is success. Null/NaN/inaccessible readings are failures.
 * Never permanently blacklist a sensor: capability can change after an
 * Android permission / kernel state transition. Does not retry in standby.
 */
internal class ReadOnlyProbeSchedule(
    private val regularIntervalMs: Long,
    private val cooldownMs: Long,
    private val failuresBeforeCooldown: Int = 3
) {
    init {
        require(regularIntervalMs >= 1L && cooldownMs >= regularIntervalMs)
        require(failuresBeforeCooldown >= 1)
    }

    private var lastAttemptMs: Long? = null
    private var consecutiveFailures = 0

    fun due(nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < 0L) return false
        val last = lastAttemptMs ?: return true
        if (nowElapsedMs < last) return true // monotonic reset or test fixture
        val next = if (consecutiveFailures >= failuresBeforeCooldown)
            cooldownMs else regularIntervalMs
        return nowElapsedMs - last >= next
    }

    fun mark(nowElapsedMs: Long, valid: Boolean) {
        lastAttemptMs = nowElapsedMs
        consecutiveFailures = if (valid) 0
            else (consecutiveFailures + 1).coerceAtMost(100)
    }
}
