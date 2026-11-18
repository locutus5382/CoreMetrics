package com.locutus5382.coremetrics.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests: no legacy service, recording or SQLite writes are invoked. */
class LegacyGuardedEffectPortTest {
    private class Host : LegacyServiceEffectHost {
        var proof = LegacyServiceEffectEvidence("epoch1", true)
        val calls = mutableListOf<String>()
        var skipProof = false
        override suspend fun evidence() = proof
        override suspend fun createSession(startId: String, profileId: Long?): EngineEffectPort.Opened {
            calls += "open:$startId"
            if (!skipProof) proof = proof.copy(activeSessionId = 42)
            return EngineEffectPort.Opened(42, 123)
        }
        override suspend fun persistTerminalStop(sessionId: Long, stopId: String) {
            calls += "persist:$stopId"
            if (!skipProof) proof = proof.copy(durableStopSessionId = sessionId)
        }
        override suspend fun pauseAndJoinSampler(sessionId: Long) {
            calls += "pause:$sessionId"
            if (!skipProof) proof = proof.copy(samplerPausedSessionId = sessionId)
        }
        override suspend fun finishOriginalSession(
            sessionId: Long, stopId: String, reason: StopReason
        ) {
            calls += "finish:$sessionId"
            if (!skipProof) proof = proof.copy(finalizedSessionId = sessionId)
        }
        override suspend fun resumeValidatedSession(sessionId: Long) {
            calls += "resume:$sessionId"
            if (!skipProof) proof = proof.copy(activeSessionId = sessionId)
        }
        override suspend fun closeInterruptedOriginalSession(sessionId: Long) {
            calls += "interrupted:$sessionId"
            if (!skipProof) proof = proof.copy(finalizedSessionId = sessionId)
        }
        override suspend fun attachOwnedOverlay(operationId: Long) {
            calls += "attach:$operationId"
            if (!skipProof) proof = proof.copy(overlayAttachedOperationId = operationId)
        }
        override suspend fun detachOwnedOverlay(operationId: Long) {
            calls += "detach:$operationId"
            if (!skipProof) proof = proof.copy(overlayAttachedOperationId = null)
        }
    }

    private inline fun assertFailsContaining(fragment: String, block: () -> Unit) {
        try { block(); throw AssertionError("Expected failure containing $fragment") }
        catch (e: IllegalStateException) {
            assertTrue(e.message?.contains(fragment) == true)
        }
    }
    private fun port(h: Host) = LegacyGuardedEffectPort("epoch1", h)

    @Test fun requiresTransferBeforeEveryMutation() = runBlocking {
        val h = Host().apply { proof = proof.copy(exclusiveOwnershipTransferred = false) }
        assertFailsContaining("OWNERSHIP_NOT_TRANSFERRED") {
            runBlocking { port(h).openSession("s", null) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun rejectsStaleEpoch() = runBlocking {
        val h = Host().apply { proof = proof.copy(serviceEpoch = "old") }
        assertFailsContaining("SERVICE_EPOCH_CHANGED") {
            runBlocking { port(h).openSession("s", null) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun openingNeedsConfirmedSession() = runBlocking {
        val h = Host().apply { skipProof = true }
        assertFailsContaining("SESSION_OPEN_UNCONFIRMED") {
            runBlocking { port(h).openSession("s", null) }
        }
    }

    @Test fun openingRejectsDuplicateActiveSession() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 42) }
        assertFailsContaining("SESSION_ALREADY_ACTIVE") {
            runBlocking { port(h).openSession("s", null) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun stopMustBeDurableBeforeAcknowledgment() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 42); skipProof = true }
        assertFailsContaining("STOP_NOT_DURABLE") {
            runBlocking { port(h).persistStopIntent(42, "stop") }
        }
    }

    @Test fun stopIsIdempotentWhenAlreadyDurable() = runBlocking {
        val h = Host().apply {
            proof = proof.copy(activeSessionId = 42, durableStopSessionId = 42)
        }
        port(h).persistStopIntent(42, "stop")
        assertTrue(h.calls.isEmpty())
    }

    @Test fun stopRejectsWrongSession() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 77) }
        assertFailsContaining("SESSION_NOT_OWNED") {
            runBlocking { port(h).persistStopIntent(42, "stop") }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun pauseMustJoinBeforeSuccess() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 42); skipProof = true }
        assertFailsContaining("SAMPLER_NOT_QUIESCED") {
            runBlocking { port(h).pauseSampling(42) }
        }
    }

    @Test fun finalizationRequiresBothStopAndPause() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 42) }
        assertFailsContaining("STOP_NOT_DURABLE") {
            runBlocking { port(h).finalizeSession(42, "x", StopReason.USER) }
        }
        h.proof = h.proof.copy(durableStopSessionId = 42)
        assertFailsContaining("SAMPLER_NOT_QUIESCED") {
            runBlocking { port(h).finalizeSession(42, "x", StopReason.USER) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun fullStopProtocolSucceedsInOrder() = runBlocking {
        val h = Host().apply { proof = proof.copy(activeSessionId = 42) }
        val p = port(h)
        p.persistStopIntent(42, "s")
        p.pauseSampling(42)
        p.finalizeSession(42, "s", StopReason.USER)
        assertEquals(listOf("persist:s", "pause:42", "finish:42"), h.calls)
    }

    @Test fun finalizationConfirmationIsRequired() = runBlocking {
        val h = Host().apply {
            proof = proof.copy(activeSessionId = 42, durableStopSessionId = 42,
                samplerPausedSessionId = 42)
            skipProof = true
        }
        assertFailsContaining("FINALIZATION_UNCONFIRMED") {
            runBlocking { port(h).finalizeSession(42, "s", StopReason.USER) }
        }
    }

    @Test fun recoveryCannotResumeUnvalidatedRow() = runBlocking {
        val h = Host()
        assertFailsContaining("RECOVERY_UNVALIDATED") {
            runBlocking { port(h).resumeSession(42) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun persistedStopPreventsResume() = runBlocking {
        val h = Host().apply {
            proof = proof.copy(validatedRecoverySessionId = 42,
                durableStopSessionId = 42)
        }
        assertFailsContaining("STOP_IS_TERMINAL") {
            runBlocking { port(h).resumeSession(42) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun originalIdIsResumedAfterValidatedRecovery() = runBlocking {
        val h = Host().apply { proof = proof.copy(validatedRecoverySessionId = 42) }
        port(h).resumeSession(42)
        assertEquals(42L, h.proof.activeSessionId)
        assertEquals(listOf("resume:42"), h.calls)
    }

    @Test fun interruptedClosureNeedsQuiescentSampler() = runBlocking {
        val h = Host().apply { proof = proof.copy(validatedRecoverySessionId = 42) }
        assertFailsContaining("SAMPLER_NOT_QUIESCED") {
            runBlocking { port(h).closeInterruptedSession(42) }
        }
    }

    @Test fun detachDoesNotCreateAnotherOverlay() = runBlocking {
        val h = Host().apply { proof = proof.copy(overlayAttachedOperationId = 3) }
        val p = port(h)
        assertFailsContaining("OVERLAY_STILL_ATTACHED") {
            runBlocking { p.attachOverlay(4) }
        }
        assertTrue(h.calls.isEmpty())
        p.detachOverlay(3)
        p.attachOverlay(4)
        assertEquals(listOf("detach:3", "attach:4"), h.calls)
    }

    @Test fun detachRequiresConfirmation() = runBlocking {
        val h = Host().apply { proof = proof.copy(overlayAttachedOperationId = 3); skipProof = true }
        assertFailsContaining("OVERLAY_DETACH_UNCONFIRMED") {
            runBlocking { port(h).detachOverlay(3) }
        }
    }

    @Test fun overlayDifferentOwnerCannotBeRemoved() = runBlocking {
        val h = Host().apply { proof = proof.copy(overlayAttachedOperationId = 3) }
        assertFailsContaining("DIFFERENT_OVERLAY_OWNER") {
            runBlocking { port(h).detachOverlay(4) }
        }
        assertTrue(h.calls.isEmpty())
    }

    @Test fun ownershipLossAfterMutationCannotBeSuccess() = runBlocking {
        val h = object : LegacyServiceEffectHost {
            var count = 0
            override suspend fun evidence(): LegacyServiceEffectEvidence {
                count++
                return LegacyServiceEffectEvidence(
                    if (count == 1) "epoch1" else "epoch2",
                    true, activeSessionId = 42)
            }
            override suspend fun createSession(startId: String, profileId: Long?) =
                EngineEffectPort.Opened(42, 1)
            override suspend fun persistTerminalStop(sessionId: Long, stopId: String) {}
            override suspend fun pauseAndJoinSampler(sessionId: Long) {}
            override suspend fun finishOriginalSession(
                sessionId: Long, stopId: String, reason: StopReason) {}
            override suspend fun resumeValidatedSession(sessionId: Long) {}
            override suspend fun closeInterruptedOriginalSession(sessionId: Long) {}
            override suspend fun attachOwnedOverlay(operationId: Long) {}
            override suspend fun detachOwnedOverlay(operationId: Long) {}
        }
        assertFailsContaining("SERVICE_EPOCH_CHANGED") {
            runBlocking { LegacyGuardedEffectPort("epoch1", h).persistStopIntent(42, "s") }
        }
    }
}
