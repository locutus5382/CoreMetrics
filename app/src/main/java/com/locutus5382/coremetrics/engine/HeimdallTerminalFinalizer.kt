package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.StopReason

/**
 * Durable terminal-finalization protocol for the original session.
 *
 * The recorder already established a single service owner and joined its
 * sampler before calling this. A completed database write is NEVER replayed
 * on lease-clear retry: the original ended_at and status are preserved.
 *
 * No Android dependency: fault scenarios can be injected deterministically
 * without altering live SQLite or SharedPreferences.
 */
internal data class TerminalLeaseProof(
    val active: Boolean,
    val matchesHeimdallSession: Boolean,
    val persistedStop: Boolean,
)

internal interface TerminalCommitPort {
    suspend fun lease(sessionId: Long): TerminalLeaseProof
    suspend fun status(sessionId: Long): String?
    /** CAS: UPDATE sessions SET ... WHERE id=? AND status='RECORDING'. */
    suspend fun commitIfRecording(sessionId: Long, terminalStatus: String): Boolean
    /** Verify the raw owner and ID again immediately before clear(). */
    suspend fun clearMatchingLease(sessionId: Long): Boolean
}

internal object HeimdallTerminalFinalizer {
    fun statusFor(reason: StopReason) = when (reason) {
        StopReason.USER -> "COMPLETED"
        StopReason.SAMPLE_FAILURE, StopReason.STORAGE_ERROR -> "FAILED"
        StopReason.INTERRUPTED -> "INTERRUPTED"
    }

    suspend fun finalizeOriginal(
        sessionId: Long,
        reason: StopReason,
        port: TerminalCommitPort,
    ): Boolean {
        require(sessionId > 0L)
        val target = statusFor(reason)
        val existing = port.status(sessionId) ?: return false
        val lease = port.lease(sessionId)
        // Crash/ack loss after a successful clear may re-deliver the same
        // effect. It is safe to acknowledge an already-terminal original row
        // with NO active lease; do not open, rewrite or clear anything.
        if (!lease.active) return existing == target
        if (!lease.matchesHeimdallSession || !lease.persistedStop) return false

        if (existing == "RECORDING") {
            // A concurrent transition must not silently overwrite another
            // terminal outcome. Verify persisted status after the CAS.
            if (!port.commitIfRecording(sessionId, target) &&
                port.status(sessionId) != target) return false
        } else if (existing != target) {
            return false
        }

        // A failed commit, changed row, or transient exception MUST NOT
        // release a STOP lease. Retry will reconcile the same original ID.
        if (port.status(sessionId) != target) return false
        val beforeClear = port.lease(sessionId)
        if (!beforeClear.active ||
            !beforeClear.matchesHeimdallSession ||
            !beforeClear.persistedStop) return false
        return port.clearMatchingLease(sessionId)
    }
}
