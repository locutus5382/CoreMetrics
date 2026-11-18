package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.EngineEffectPort
import com.locutus5382.coremetrics.domain.LegacyServiceEffectEvidence
import com.locutus5382.coremetrics.domain.LegacyServiceEffectHost
import com.locutus5382.coremetrics.domain.StopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform operations owned by exactly one foreground monitor service. */
internal interface HeimdallSessionIo<S> : SamplingChannel<S> {
    suspend fun begin(profileId: Long?): EngineEffectPort.Opened
    suspend fun startLease(sessionId: Long): Boolean
    suspend fun persistStop(sessionId: Long): Boolean
    /** Finish the original SQLite ID before clearing the recovery lease. */
    suspend fun finish(sessionId: Long, reason: StopReason): Boolean
    /** Checks original SQLite row and unexpired lease, excluding persisted STOP. */
    suspend fun validatedRecovery(sessionId: Long): Boolean
    /** A persisted terminal STOP requiring finalization, never resampling. */
    suspend fun terminalRecovery(sessionId: Long): Boolean
    suspend fun renewRecoveryLease(sessionId: Long): Boolean
    suspend fun unfinishedRow(sessionId: Long): Boolean
    suspend fun closeInterrupted(sessionId: Long): Boolean
    suspend fun attachOverlay(operationId: Long): Boolean
    suspend fun detachOverlay(operationId: Long): Boolean
}

/** The live service must verify these facts; a UI snapshot cannot grant ownership. */
internal data class LegacyHandoffEvidence(
    val serviceEpoch: String,
    val legacyTaskAbsent: Boolean,
    val legacySessionInactive: Boolean,
    val legacyLeaseInactive: Boolean,
    val legacyOverlayDetached: Boolean,
)

/**
 * HEIMDALL's own session/sampler owner, reusing the existing guarded port.
 * No legacy service constructs this host in M2-F. It may become active only
 * after explicit single-owner handoff is validated on the Android service.
 */
internal class HeimdallSessionHost<S>(
    private val serviceEpoch: String,
    private val io: HeimdallSessionIo<S>,
    private val parentScope: CoroutineScope,
    private val intervalMs: Long,
    private val clock: SamplingClock = CoroutineSamplingClock,
    private val fatalError: (Exception) -> Boolean = { false },
    /** Fatal collection failure is a STOP request to the sole coordinator. */
    private val onFatalSampling: suspend (sessionId: Long, errorType: String) -> Unit,
) : LegacyServiceEffectHost {
    private val lock = Mutex()
    private var transferred = false
    private var activeId: Long? = null
    private var durableStopId: Long? = null
    private var pausedId: Long? = null
    private var finalizedId: Long? = null
    private var recoverableId: Long? = null
    private var overlayId: Long? = null
    private var samplerJob: Job? = null
    private var latestSummary: SamplingSummary? = null

    init {
        require(serviceEpoch.isNotBlank())
        require(intervalMs == 2_000L || intervalMs == 5_000L)
    }

    suspend fun acceptExclusiveHandoff(e: LegacyHandoffEvidence): Boolean = lock.withLock {
        if (transferred) return@withLock false
        if (e.serviceEpoch != serviceEpoch || !e.legacyTaskAbsent ||
            !e.legacySessionInactive || !e.legacyLeaseInactive ||
            !e.legacyOverlayDetached || samplerJob != null || activeId != null)
            return@withLock false
        transferred = true
        true
    }

    override suspend fun evidence(): LegacyServiceEffectEvidence = lock.withLock {
        LegacyServiceEffectEvidence(serviceEpoch, transferred, activeId, durableStopId,
            pausedId, finalizedId, recoverableId, overlayId)
    }

    override suspend fun createSession(startId: String, profileId: Long?): EngineEffectPort.Opened =
        lock.withLock {
            owner()
            check(startId.isNotBlank() && activeId == null && samplerJob == null) {
                "SESSION_ALREADY_ACTIVE"
            }
            check(parentScope.isActive) { "SAMPLER_SCOPE_STOPPED" }
            val opened = io.begin(profileId)
            check(opened.sessionId > 0L) { "INVALID_SESSION_ID" }
            // Failed lease means the row needs explicit repair; never sample it.
            check(io.startLease(opened.sessionId)) { "RECOVERY_LEASE_NOT_DURABLE" }
            activate(opened.sessionId)
            opened
        }

    /** Called only by the live service after inspecting DB/lease, not IPC. */
    suspend fun preflightRecovery(sessionId: Long): Boolean = lock.withLock {
        owner()
        if (activeId != null || sessionId <= 0L || !io.validatedRecovery(sessionId))
            return@withLock false
        recoverableId = sessionId
        true
    }

    /** A restarted service must finalize persisted STOP without starting sampling. */
    suspend fun preflightPersistedStop(sessionId: Long): Boolean = lock.withLock {
        owner()
        if (sessionId <= 0L || activeId != null || !io.terminalRecovery(sessionId))
            return@withLock false
        activeId = sessionId
        durableStopId = sessionId
        pausedId = sessionId // the previous process no longer has a local sampler
        recoverableId = sessionId
        true
    }

    /** A dead lease may still leave an unfinished row to close as interrupted. */
    suspend fun preflightInterruptedClose(sessionId: Long): Boolean = lock.withLock {
        owner()
        if (sessionId <= 0L || activeId != null || !io.unfinishedRow(sessionId))
            return@withLock false
        recoverableId = sessionId
        pausedId = sessionId
        true
    }

    override suspend fun resumeValidatedSession(sessionId: Long) = lock.withLock {
        owner()
        check(activeId == null && recoverableId == sessionId &&
            durableStopId != sessionId && io.validatedRecovery(sessionId)) {
            "RECOVERY_UNVALIDATED"
        }
        check(parentScope.isActive) { "SAMPLER_SCOPE_STOPPED" }
        check(io.renewRecoveryLease(sessionId)) { "RECOVERY_LEASE_NOT_DURABLE" }
        activate(sessionId)
    }

    private fun activate(id: Long) {
        activeId = id
        pausedId = null
        durableStopId = null
        finalizedId = null
        latestSummary = null
        val loop = HeimdallSamplingLoop(io, clock, isFatal = fatalError)
        samplerJob = parentScope.launch {
            val summary = loop.run(id, intervalMs)
            latestSummary = summary
            // Never finalize autonomously; the coordinator must persist STOP.
            summary.fatalError?.let { errorType -> onFatalSampling(id, errorType) }
        }
    }

    override suspend fun persistTerminalStop(sessionId: Long, stopId: String) = lock.withLock {
        owner()
        check(stopId.isNotBlank() && activeId == sessionId) { "SESSION_NOT_OWNED" }
        if (durableStopId != sessionId) {
            check(io.persistStop(sessionId)) { "STOP_NOT_DURABLE" }
            durableStopId = sessionId
        }
    }

    override suspend fun pauseAndJoinSampler(sessionId: Long) = lock.withLock {
        owner()
        // A STOP must quiesce sampling even when the durable lease write failed.
        // Finalization remains blocked until both proofs are confirmed.
        check(activeId == sessionId) { "SESSION_NOT_OWNED" }
        if (pausedId != sessionId) {
            val job = samplerJob ?: error("SAMPLER_NOT_OWNED")
            job.cancelAndJoin() // actual quiescence, not merely cancel request
            samplerJob = null
            pausedId = sessionId
        }
    }

    override suspend fun finishOriginalSession(
        sessionId: Long, stopId: String, reason: StopReason
    ) = lock.withLock {
        owner()
        check(stopId.isNotBlank() && activeId == sessionId &&
            durableStopId == sessionId && pausedId == sessionId) { "STOP_NOT_QUIESCENT" }
        if (finalizedId != sessionId) {
            check(io.finish(sessionId, reason)) { "FINALIZATION_NOT_PERSISTED" }
            finalizedId = sessionId
            activeId = null
        }
    }

    override suspend fun closeInterruptedOriginalSession(sessionId: Long) = lock.withLock {
        owner()
        check(recoverableId == sessionId && samplerJob == null) {
            "RECOVERY_UNVALIDATED"
        }
        check(io.closeInterrupted(sessionId)) { "INTERRUPTED_CLOSE_FAILED" }
        finalizedId = sessionId
    }

    override suspend fun attachOwnedOverlay(operationId: Long) = lock.withLock {
        owner()
        check(operationId > 0 && overlayId == null) { "OVERLAY_STILL_ATTACHED" }
        check(io.attachOverlay(operationId)) { "OVERLAY_ATTACH_FAILED" }
        overlayId = operationId
    }

    override suspend fun detachOwnedOverlay(operationId: Long) = lock.withLock {
        owner()
        check(operationId > 0 && (overlayId == operationId || overlayId == null)) {
            "DIFFERENT_OVERLAY_OWNER"
        }
        check(io.detachOverlay(operationId)) { "OVERLAY_CLEANUP_UNCONFIRMED" }
        overlayId = null
    }

    fun lastRunSummary(): SamplingSummary? = latestSummary

    private fun owner() = check(transferred) { "RECORDER_OWNERSHIP_NOT_TRANSFERRED" }
}
