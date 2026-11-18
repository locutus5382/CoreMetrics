package com.locutus5382.coremetrics.engine

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic tests: saved rows are never faked or deleted on lease failure. */
class HeimdallLeaseHealthTest {
    private class ImmediateClock : SamplingClock {
        private var ticks = 0L
        override fun elapsedMs() = ticks
        override suspend fun waitMs(durationMs: Long) {
            ticks += durationMs
            delay(1)
        }
    }

    @Test fun failedHeartbeatStopsImmediatelyButRetainsSavedSample() = runBlocking {
        var committed = 0
        var presented = 0
        val io = object : SamplingChannel<Int> {
            override suspend fun collect(): Int = ++committed
            override suspend fun persist(sessionId: Long, sample: Int) {}
            override suspend fun afterPersist(sessionId: Long) {
                throw HeimdallLeaseHealthException(HeimdallLeaseHealthIssue.COMMIT_FAILED)
            }
            override suspend fun committed(sessionId: Long, sample: Int) { presented++ }
        }
        val result = withTimeout(2_000) {
            HeimdallSamplingLoop(io, ImmediateClock()).run(73L, 2_000L)
        }
        assertEquals(1L, result.stored)
        assertEquals(0L, result.missed)
        assertEquals(1, committed)
        assertEquals(0, presented)
        assertEquals("LEASE_COMMIT_FAILED", result.fatalError)
    }

    @Test fun lostOwnerStopsSamplingBeforeAnotherPersist() = runBlocking {
        var writes = 0
        val io = object : SamplingChannel<Int> {
            override suspend fun collect() = 1
            override suspend fun persist(sessionId: Long, sample: Int) { writes++ }
            override suspend fun afterPersist(sessionId: Long) {
                throw HeimdallLeaseHealthException(HeimdallLeaseHealthIssue.OWNER_CHANGED)
            }
        }
        val result = withTimeout(2_000) {
            HeimdallSamplingLoop(io, ImmediateClock()).run(73L, 5_000L)
        }
        assertEquals(1, writes)
        assertEquals(1L, result.stored)
        assertEquals("LEASE_OWNER_CHANGED", result.fatalError)
    }

    @Test fun persistedStopObservedMidSampleCannotBeIgnored() = runBlocking {
        val io = object : SamplingChannel<Int> {
            override suspend fun collect() = 1
            override suspend fun persist(sessionId: Long, sample: Int) {}
            override suspend fun afterPersist(sessionId: Long) {
                throw HeimdallLeaseHealthException(
                    HeimdallLeaseHealthIssue.TERMINAL_STOP_SEEN)
            }
        }
        val result = withTimeout(2_000) {
            HeimdallSamplingLoop(io, ImmediateClock()).run(73L, 2_000L)
        }
        assertEquals("LEASE_TERMINAL_STOP_SEEN", result.fatalError)
        assertEquals(1L, result.stored)
        assertEquals(0L, result.missed)
    }

    @Test fun optionalPresentationExceptionDoesNotMaskHealthyLease() = runBlocking {
        val saved = AtomicInteger()
        val checked = AtomicInteger()
        val io = object : SamplingChannel<Int> {
            override suspend fun collect() = 1
            override suspend fun persist(sessionId: Long, sample: Int) {
                saved.incrementAndGet()
            }
            override suspend fun afterPersist(sessionId: Long) {
                checked.incrementAndGet()
            }
            override suspend fun committed(sessionId: Long, sample: Int) {
                throw IllegalStateException("HUD FAILED")
            }
        }
        val task = launch(Dispatchers.Default) {
            HeimdallSamplingLoop(io, ImmediateClock()).run(73L, 2_000L)
        }
        withTimeout(2_000) { while (checked.get() < 3) delay(2) }
        task.cancelAndJoin()
        assertTrue(saved.get() >= 3)
        assertEquals(saved.get(), checked.get())
    }

    @Test fun ordinarySensorFailureStillCountsMissingTickNotStoredRow() = runBlocking {
        var count = 0
        val io = object : SamplingChannel<Int> {
            override suspend fun collect(): Int {
                count++
                throw IllegalStateException("SENSOR_ERROR")
            }
            override suspend fun persist(sessionId: Long, sample: Int) {}
        }
        val result = withTimeout(2_000) {
            HeimdallSamplingLoop(io, ImmediateClock(), maxConsecutiveFailures = 2)
                .run(73L, 2_000L)
        }
        assertEquals(0L, result.stored)
        assertEquals(2L, result.missed)
        assertEquals(2, count)
        assertEquals("IllegalStateException", result.fatalError)
    }
}
