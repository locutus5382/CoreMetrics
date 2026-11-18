package com.locutus5382.coremetrics.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Monotonic clock is injected so cadence is testable without Android hardware. */
internal interface SamplingClock {
    fun elapsedMs(): Long
    suspend fun waitMs(durationMs: Long)
}

internal object CoroutineSamplingClock : SamplingClock {
    override fun elapsedMs(): Long = System.nanoTime() / 1_000_000L
    override suspend fun waitMs(durationMs: Long) { delay(durationMs) }
}

internal interface SamplingChannel<S> {
    suspend fun collect(): S
    suspend fun persist(sessionId: Long, sample: S)
    /** UI/HUD is best-effort, and cannot change data integrity. */
    suspend fun committed(sessionId: Long, sample: S) {}
}

internal data class SamplingSummary(
    val sessionId: Long,
    val stored: Long,
    val missed: Long,
    val fatalError: String? = null,
)

/**
 * HEIMDALL-owned sampling loop. Unlike the legacy RecordingService this class
 * never creates/finalizes a DB session or decides whether a user STOP is durable.
 * The single service owner must persist STOP before cancelAndJoin on this job.
 *
 * A dropped tick is never backfilled or represented as a zero-reading sample.
 */
internal class HeimdallSamplingLoop<S>(
    private val channel: SamplingChannel<S>,
    private val clock: SamplingClock = CoroutineSamplingClock,
    private val maxConsecutiveFailures: Int = 5,
    private val isFatal: (Exception) -> Boolean = { false },
) {
    init { require(maxConsecutiveFailures >= 1) }

    suspend fun run(sessionId: Long, intervalMs: Long): SamplingSummary {
        require(sessionId > 0L)
        require(intervalMs == 2_000L || intervalMs == 5_000L)
        var stored = 0L
        var missed = 0L
        var consecutiveFailures = 0
        var next = clock.elapsedMs()
        var fatal: String? = null
        while (currentCoroutineContext().isActive) {
            try {
                val sample = channel.collect()
                channel.persist(sessionId, sample)
                stored++
                consecutiveFailures = 0
                // A broken renderer must not discard an already-persisted sample.
                try { channel.committed(sessionId, sample) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* optional presentation */ }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                missed++
                consecutiveFailures++
                if (isFatal(failure) || consecutiveFailures >= maxConsecutiveFailures) {
                    fatal = failure.javaClass.simpleName.ifBlank { "SAMPLE_FAILURE" }
                    break
                }
            }
            next += intervalMs
            val remaining = next - clock.elapsedMs()
            if (remaining > 0) clock.waitMs(remaining)
            else next = clock.elapsedMs() // never burst to catch up
        }
        return SamplingSummary(sessionId, stored, missed, fatal)
    }
}
