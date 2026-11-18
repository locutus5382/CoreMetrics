package com.locutus5382.coremetrics.domain

import kotlinx.coroutines.sync.Mutex

/**
 * M2-D boundary: evidence must originate from the SAME RecordingService instance
 * that executes effects. StatusProvider or a UI lease read never grants ownership.
 *
 * This host is not implemented or installed in the production service in M2-D.
 */
internal interface LegacyServiceEffectHost {
    suspend fun evidence(): LegacyServiceEffectEvidence
    suspend fun createSession(startId: String, profileId: Long?): EngineEffectPort.Opened
    suspend fun persistTerminalStop(sessionId: Long, stopId: String)
    /** Must JOIN the sample-writing coroutine, not merely call cancel(). */
    suspend fun pauseAndJoinSampler(sessionId: Long)
    suspend fun finishOriginalSession(sessionId: Long, stopId: String, reason: StopReason)
    suspend fun resumeValidatedSession(sessionId: Long)
    suspend fun closeInterruptedOriginalSession(sessionId: Long)
    /** Must confirm actual WindowManager ownership. */
    suspend fun attachOwnedOverlay(operationId: Long)
    suspend fun detachOwnedOverlay(operationId: Long)
}

/** Proofs must come from the live service owner and its persisted DB/lease. */
internal data class LegacyServiceEffectEvidence(
    val serviceEpoch: String,
    val exclusiveOwnershipTransferred: Boolean,
    val activeSessionId: Long? = null,
    val durableStopSessionId: Long? = null,
    val samplerPausedSessionId: Long? = null,
    val finalizedSessionId: Long? = null,
    val validatedRecoverySessionId: Long? = null,
    val overlayAttachedOperationId: Long? = null,
)

/**
 * Fail-closed sequential effect port. Constructible for tests, but dormant in
 * production until a service-owned host explicitly implements the contract.
 * Never starts an independent sampling loop or opens SQLite itself.
 */
internal class LegacyGuardedEffectPort(
    private val expectedServiceEpoch: String,
    private val host: LegacyServiceEffectHost,
) : EngineEffectPort {
    private val mutex = Mutex()
    init { require(expectedServiceEpoch.isNotBlank()) }

    private suspend fun <T> verified(action: suspend (LegacyServiceEffectEvidence) -> T): T {
        mutex.lock()
        try { return action(checked(host.evidence())) }
        finally { mutex.unlock() }
    }

    private fun checked(e: LegacyServiceEffectEvidence): LegacyServiceEffectEvidence {
        check(e.serviceEpoch == expectedServiceEpoch) { "SERVICE_EPOCH_CHANGED" }
        check(e.exclusiveOwnershipTransferred) { "RECORDER_OWNERSHIP_NOT_TRANSFERRED" }
        return e
    }

    private suspend fun after() = checked(host.evidence())

    override suspend fun openSession(startId: String, profileId: Long?): EngineEffectPort.Opened =
        verified { before ->
            check(startId.isNotBlank()) { "MISSING_START_ID" }
            check(before.activeSessionId == null) { "SESSION_ALREADY_ACTIVE" }
            val result = host.createSession(startId, profileId)
            check(result.sessionId > 0) { "INVALID_SESSION_ID" }
            check(after().activeSessionId == result.sessionId) { "SESSION_OPEN_UNCONFIRMED" }
            result
        }

    override suspend fun persistStopIntent(sessionId: Long, stopId: String) = verified { before ->
        requireValidSession(sessionId)
        check(stopId.isNotBlank()) { "MISSING_STOP_ID" }
        check(before.activeSessionId == sessionId) { "SESSION_NOT_OWNED" }
        if (before.durableStopSessionId != sessionId)
            host.persistTerminalStop(sessionId, stopId)
        check(after().durableStopSessionId == sessionId) { "STOP_NOT_DURABLE" }
    }

    override suspend fun pauseSampling(sessionId: Long) = verified { before ->
        requireValidSession(sessionId)
        check(before.activeSessionId == sessionId) { "SESSION_NOT_OWNED" }
        if (before.samplerPausedSessionId != sessionId)
            host.pauseAndJoinSampler(sessionId)
        check(after().samplerPausedSessionId == sessionId) { "SAMPLER_NOT_QUIESCED" }
    }

    override suspend fun finalizeSession(sessionId: Long, stopId: String, reason: StopReason) =
        verified { before ->
            requireValidSession(sessionId)
            check(stopId.isNotBlank()) { "MISSING_STOP_ID" }
            check(before.durableStopSessionId == sessionId) { "STOP_NOT_DURABLE" }
            check(before.samplerPausedSessionId == sessionId) { "SAMPLER_NOT_QUIESCED" }
            if (before.finalizedSessionId != sessionId) {
                check(before.activeSessionId == sessionId) { "SESSION_NOT_OWNED" }
                host.finishOriginalSession(sessionId, stopId, reason)
            }
            check(after().finalizedSessionId == sessionId) { "FINALIZATION_UNCONFIRMED" }
        }

    override suspend fun resumeSession(sessionId: Long) = verified { before ->
        requireValidSession(sessionId)
        check(before.validatedRecoverySessionId == sessionId) { "RECOVERY_UNVALIDATED" }
        check(before.durableStopSessionId != sessionId) { "STOP_IS_TERMINAL" }
        check(before.activeSessionId == null || before.activeSessionId == sessionId) {
            "DIFFERENT_ACTIVE_SESSION"
        }
        if (before.activeSessionId != sessionId) host.resumeValidatedSession(sessionId)
        check(after().activeSessionId == sessionId) { "RESUME_UNCONFIRMED" }
    }

    override suspend fun closeInterruptedSession(sessionId: Long) = verified { before ->
        requireValidSession(sessionId)
        check(before.validatedRecoverySessionId == sessionId) { "RECOVERY_UNVALIDATED" }
        check(before.samplerPausedSessionId == sessionId) { "SAMPLER_NOT_QUIESCED" }
        if (before.finalizedSessionId != sessionId)
            host.closeInterruptedOriginalSession(sessionId)
        check(after().finalizedSessionId == sessionId) { "INTERRUPTED_CLOSE_UNCONFIRMED" }
    }

    override suspend fun attachOverlay(operationId: Long) = verified { before ->
        check(operationId > 0) { "INVALID_OVERLAY_OPERATION" }
        check(before.overlayAttachedOperationId == null) { "OVERLAY_STILL_ATTACHED" }
        host.attachOwnedOverlay(operationId)
        check(after().overlayAttachedOperationId == operationId) { "OVERLAY_ATTACH_UNCONFIRMED" }
    }

    override suspend fun detachOverlay(operationId: Long) = verified { before ->
        check(operationId > 0) { "INVALID_OVERLAY_OPERATION" }
        check(before.overlayAttachedOperationId == null ||
            before.overlayAttachedOperationId == operationId) { "DIFFERENT_OVERLAY_OWNER" }
        host.detachOwnedOverlay(operationId)
        check(after().overlayAttachedOperationId == null) { "OVERLAY_DETACH_UNCONFIRMED" }
    }

    private fun requireValidSession(sessionId: Long) {
        require(sessionId > 0) { "INVALID_SESSION_ID" }
    }
}
