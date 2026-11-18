package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private class FastClock : SamplingClock {
    private var now = 0L
    override fun elapsedMs(): Long = synchronized(this) { now }
    override suspend fun waitMs(durationMs: Long) {
        delay(1)
        synchronized(this) { now += durationMs }
    }
}

private class FakeIo : HeimdallSessionIo<Int> {
    var begins = 0
    var collected = 0
    var writes = 0
    var leaseOk = true
    var stopOk = true
    var finishOk = true
    var resumeOk = true
    var renewOk = true
    var terminalOk = true
    var pendingRow = true
    var collectFails = false
    var storeFails = false
    var attached: Long? = null
    val actions = mutableListOf<String>()
    override suspend fun begin(profileId: Long?): EngineEffectPort.Opened {
        begins++
        actions += "begin"
        return EngineEffectPort.Opened(42, 400)
    }
    override suspend fun startLease(sessionId: Long): Boolean {
        actions += "lease"
        return leaseOk
    }
    override suspend fun collect(): Int {
        collected++
        if (collectFails) throw IOException("sensor unavailable")
        return collected
    }
    override suspend fun persist(sessionId: Long, sample: Int) {
        if (storeFails) throw IOException("db error")
        writes++
    }
    override suspend fun persistStop(sessionId: Long): Boolean {
        actions += "stop"
        return stopOk
    }
    override suspend fun finish(sessionId: Long, reason: StopReason): Boolean {
        actions += "finish"
        return finishOk
    }
    override suspend fun validatedRecovery(sessionId: Long) = resumeOk
    override suspend fun terminalRecovery(sessionId: Long) = terminalOk
    override suspend fun renewRecoveryLease(sessionId: Long) = renewOk
    override suspend fun unfinishedRow(sessionId: Long) = pendingRow
    override suspend fun closeInterrupted(sessionId: Long): Boolean {
        actions += "close-interrupted:$sessionId"
        return true
    }
    override suspend fun attachOverlay(operationId: Long): Boolean {
        attached = operationId
        return true
    }
    override suspend fun detachOverlay(operationId: Long): Boolean {
        attached = null
        return true
    }
}

class HeimdallNativeEngineTest {
    private fun host(io: FakeIo, scope: CoroutineScope,
        onFatal: suspend (Long, String) -> Unit = { _, _ -> }
    ) = HeimdallSessionHost("epoch", io, scope, 2_000L, FastClock(),
        onFatalSampling = onFatal)

    private val clean = LegacyHandoffEvidence("epoch", true, true, true, true)

    private suspend fun failContaining(expected: String, call: suspend () -> Unit) {
        try { call(); fail("Expected $expected") }
        catch (e: IllegalStateException) {
            assertTrue(e.message ?: "Missing reason", e.message?.contains(expected) == true)
        }
    }

    @Test fun deniedHandoffCannotOpenSecondEngine() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            assertFalse(h.acceptExclusiveHandoff(clean.copy(legacyTaskAbsent = false)))
            assertFalse(h.acceptExclusiveHandoff(clean.copy(legacyLeaseInactive = false)))
            assertFalse(h.acceptExclusiveHandoff(clean.copy(legacyOverlayDetached = false)))
            assertFalse(h.acceptExclusiveHandoff(clean.copy(serviceEpoch = "wrong")))
            failContaining("OWNERSHIP_NOT_TRANSFERRED") { h.createSession("x", null) }
            assertEquals(0, io.begins)
            assertTrue(h.acceptExclusiveHandoff(clean))
            assertFalse(h.acceptExclusiveHandoff(clean))
        } finally { scope.cancel() }
    }

    @Test fun leaseMustBeDurableBeforeSampling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { leaseOk = false }
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            failContaining("RECOVERY_LEASE_NOT_DURABLE") { h.createSession("a", null) }
            delay(15)
            assertEquals(0, io.collected)
            assertTrue("Failed lease must not strand an unfinished row",
                "close-interrupted:42" in io.actions)
        } finally { scope.cancel() }
    }

    @Test fun controllerExecutesNativeStartSampleStopFinalize() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            val c = EngineController(EngineState(epoch = "epoch"),
                LegacyGuardedEffectPort("epoch", h), scope)
            c.submit(EngineCommand.Start("s1", CommandOrigin.APP))
            withTimeout(3_000) {
                while (c.state.value.recording !is RecordingState.Recording ||
                    io.writes < 2) delay(3)
            }
            assertEquals(42L, (c.state.value.recording as RecordingState.Recording).sessionId)
            c.submit(EngineCommand.Stop("stop1", CommandOrigin.APP))
            withTimeout(3_000) {
                while (c.state.value.recording !is RecordingState.Idle) delay(3)
            }
            val committed = io.writes
            delay(20)
            assertEquals(committed, io.writes)
            assertTrue(io.actions.indexOf("stop") < io.actions.indexOf("finish"))
            assertEquals(1, io.begins)
            c.cancel()
        } finally { scope.cancel() }
    }

    @Test fun stopCommitFailureCannotFinalize() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { stopOk = false }
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            val p = LegacyGuardedEffectPort("epoch", h)
            p.openSession("s", null)
            failContaining("STOP_NOT_DURABLE") { p.persistStopIntent(42, "stop") }
            // STOP persistence can fail, but the sampling coroutine must still stop.
            p.pauseSampling(42)
            val stoppedAt = io.writes
            delay(15)
            assertEquals(stoppedAt, io.writes)
            failContaining("STOP_NOT_DURABLE") {
                p.finalizeSession(42, "stop", StopReason.USER)
            }
            assertFalse("finish" in io.actions)
        } finally { scope.cancel() }
    }

    @Test fun stopWaitsForActualSamplerTermination() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            val p = LegacyGuardedEffectPort("epoch", h)
            p.openSession("s", null)
            // Even an unpersisted STOP must stop additional sensor/SQLite work.
            p.pauseSampling(42)
            failContaining("STOP_NOT_DURABLE") {
                p.finalizeSession(42, "stop", StopReason.USER)
            }
            p.persistStopIntent(42, "stop")
            val n = io.writes
            delay(20)
            assertEquals(n, io.writes)
            p.finalizeSession(42, "stop", StopReason.USER)
            assertEquals(42L, h.evidence().finalizedSessionId)
        } finally { scope.cancel() }
    }

    @Test fun failedDatabaseFinishCanRetryOriginalSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { finishOk = false }
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            val p = LegacyGuardedEffectPort("epoch", h)
            p.openSession("s", null)
            p.persistStopIntent(42, "stop")
            p.pauseSampling(42)
            failContaining("FINALIZATION_NOT_PERSISTED") {
                p.finalizeSession(42, "stop", StopReason.USER)
            }
            io.finishOk = true
            p.finalizeSession(42, "stop", StopReason.USER)
            assertEquals(42L, h.evidence().finalizedSessionId)
        } finally { scope.cancel() }
    }

    @Test fun nativeHandoffRequiresRealMatchingLeaseAndRow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { resumeOk = false; terminalOk = false }
            val h = host(io, scope)
            val valid = NativeRecoveryHandoffEvidence(
                "epoch", 73L, legacyTaskAbsent = true,
                legacySessionInactive = true, legacyOverlayDetached = true)
            assertFalse(h.acceptNativeRecoveryHandoff(valid))
            io.resumeOk = true
            assertFalse(h.acceptNativeRecoveryHandoff(valid.copy(serviceEpoch = "wrong")))
            assertFalse(h.acceptNativeRecoveryHandoff(valid.copy(legacyTaskAbsent = false)))
            assertFalse(h.acceptNativeRecoveryHandoff(valid.copy(legacyOverlayDetached = false)))
            assertTrue(h.acceptNativeRecoveryHandoff(valid))
            assertFalse(h.acceptNativeRecoveryHandoff(valid))
            assertTrue(h.preflightRecovery(73L))
            LegacyGuardedEffectPort("epoch", h).resumeSession(73L)
            assertEquals(73L, h.evidence().activeSessionId)
            assertEquals(0, io.begins)
        } finally { scope.cancel() }
    }

    @Test fun nativeTerminalRecoveryFinalizesWithoutSamplingOrNewRow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { resumeOk = false; terminalOk = true }
            val h = host(io, scope)
            assertTrue(h.acceptNativeRecoveryHandoff(
                NativeRecoveryHandoffEvidence("epoch", 91L, true, true, true)))
            assertTrue(h.preflightPersistedStop(91L))
            val port = LegacyGuardedEffectPort("epoch", h)
            port.finalizeSession(91L, "recovery-stop-91", StopReason.USER)
            assertEquals(91L, h.evidence().finalizedSessionId)
            assertEquals(0, io.begins)
            assertEquals(0, io.collected)
        } finally { scope.cancel() }
    }

    @Test fun nativeRecoveryCannotClaimUnverifiedRow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { resumeOk = false; terminalOk = false }
            val h = host(io, scope)
            assertFalse(h.acceptNativeRecoveryHandoff(
                NativeRecoveryHandoffEvidence("epoch", 92L, true, true, true)))
            failContaining("OWNERSHIP_NOT_TRANSFERRED") {
                h.preflightRecovery(92L)
            }
            assertEquals(0, io.begins)
        } finally { scope.cancel() }
    }

    @Test fun restoreOriginalIdNeverStartsNewSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            assertTrue(h.preflightRecovery(77))
            val p = LegacyGuardedEffectPort("epoch", h)
            p.resumeSession(77)
            assertEquals(77L, h.evidence().activeSessionId)
            p.persistStopIntent(77, "stop")
            p.pauseSampling(77)
            p.finalizeSession(77, "stop", StopReason.USER)
            assertEquals(0, io.begins)
        } finally { scope.cancel() }
    }

    @Test fun failedRecoveryRenewalPreventsSampling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { renewOk = false }
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            h.preflightRecovery(77)
            failContaining("RECOVERY_LEASE_NOT_DURABLE") {
                LegacyGuardedEffectPort("epoch", h).resumeSession(77)
            }
            assertEquals(0, io.collected)
        } finally { scope.cancel() }
    }

    @Test fun persistedTerminalStopFinalizesWithoutSampler() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            assertTrue(h.preflightPersistedStop(91))
            LegacyGuardedEffectPort("epoch", h)
                .finalizeSession(91, "recovery-91", StopReason.USER)
            assertEquals(91L, h.evidence().finalizedSessionId)
            assertEquals(0, io.collected)
        } finally { scope.cancel() }
    }

    @Test fun invalidLeaseMayOnlyCloseOriginalIncompleteRow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo().apply { resumeOk = false }
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            assertFalse(h.preflightRecovery(79))
            assertTrue(h.preflightInterruptedClose(79))
            LegacyGuardedEffectPort("epoch", h).closeInterruptedSession(79)
            assertEquals(79L, h.evidence().finalizedSessionId)
            assertEquals(0, io.begins)
        } finally { scope.cancel() }
    }

    @Test fun errorsCreateSampleGapsNotFakeRows() = runBlocking {
        val io = FakeIo().apply { collectFails = true }
        val summary = withTimeout(2_000) {
            HeimdallSamplingLoop(io, FastClock(), maxConsecutiveFailures = 3)
                .run(42, 2_000)
        }
        assertEquals(0L, summary.stored)
        assertEquals(3L, summary.missed)
        assertEquals("IOException", summary.fatalError)
    }

    @Test fun permanentStorageErrorsTerminateWithoutRetryStorm() = runBlocking {
        val io = FakeIo().apply { storeFails = true }
        val summary = withTimeout(2_000) {
            HeimdallSamplingLoop(io, FastClock(), isFatal = { it is IOException })
                .run(42, 5_000)
        }
        assertEquals(1L, summary.missed)
        assertEquals(0L, summary.stored)
    }

    @Test fun brokenHudCannotUndoCommittedSample() = runBlocking {
        val seen = AtomicInteger()
        val channel = object : SamplingChannel<Int> {
            override suspend fun collect() = seen.incrementAndGet()
            override suspend fun persist(sessionId: Long, sample: Int) {}
            override suspend fun committed(sessionId: Long, sample: Int) {
                throw IllegalStateException("HUD renderer failed")
            }
        }
        val job = launch { HeimdallSamplingLoop(channel, FastClock()).run(42, 2_000) }
        withTimeout(2_000) { while (seen.get() < 3) delay(2) }
        job.cancelAndJoin()
        assertTrue(seen.get() >= 3)
    }

    @Test fun overlayCannotAttachTwiceWithoutConfirmedCleanup() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val io = FakeIo()
            val h = host(io, scope)
            h.acceptExclusiveHandoff(clean)
            val p = LegacyGuardedEffectPort("epoch", h)
            p.attachOverlay(7)
            failContaining("OVERLAY_STILL_ATTACHED") { p.attachOverlay(8) }
            p.detachOverlay(7)
            p.attachOverlay(8)
            assertEquals(8L, h.evidence().overlayAttachedOperationId)
        } finally { scope.cancel() }
    }

    @Test fun fatalSamplingIsSurfacedForCoordinatedStop() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val events = mutableListOf<String>()
        try {
            val io = FakeIo().apply { collectFails = true }
            val h = host(io, scope) { id, type ->
                synchronized(events) { events += "$id:$type" }
            }
            h.acceptExclusiveHandoff(clean)
            val p = LegacyGuardedEffectPort("epoch", h)
            p.openSession("start", null)
            withTimeout(3_000) {
                while (synchronized(events) { events.isEmpty() }) delay(3)
            }
            assertEquals("42:IOException", synchronized(events) { events.single() })
            assertEquals(null, h.evidence().finalizedSessionId)
            p.persistStopIntent(42, "fatal")
            p.pauseSampling(42)
            p.finalizeSession(42, "fatal", StopReason.SAMPLE_FAILURE)
        } finally { scope.cancel() }
    }
}
