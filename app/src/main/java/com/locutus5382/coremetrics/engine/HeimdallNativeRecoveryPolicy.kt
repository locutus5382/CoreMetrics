package com.locutus5382.coremetrics.engine

/**
 * M2-I: pure decision tree for an existing HEIMDALL_DEBUG-owned lease.
 *
 * This does NOT authorize mutations. The monitor service must verify the
 * actual lease/row immediately before using this decision, then revalidate
 * both through its session-host platform IO.
 */
internal data class NativeRecoveryEvidence(
    val rawOwner: String?,
    val leaseEligible: Boolean,
    val originalSessionId: Long?,
    val recordingLease: Boolean,
    val persistedStop: Boolean,
    val originalRowStatus: String?,
    val intervalMs: Long?,
    val userRequestedStop: Boolean,
)

internal enum class NativeRecoveryDecision {
    RESUME_ORIGINAL,
    FINALIZE_PERSISTED_STOP,
    PERSIST_STOP_AND_FINALIZE,
    RELEASE_ALREADY_FINALIZED_LEASE,
    QUARANTINE,
}

internal object HeimdallNativeRecoveryPolicy {
    private val terminalStatuses = setOf(
        "COMPLETED", "FAILED", "INTERRUPTED", "INTERRUPTED_RECOVERED",
        "INTERRUPTED_STOPPED",
    )

    fun decide(e: NativeRecoveryEvidence): NativeRecoveryDecision {
        if (e.rawOwner != "HEIMDALL_DEBUG" || !e.leaseEligible ||
            (e.originalSessionId ?: 0L) <= 0L || !e.recordingLease ||
            e.intervalMs !in setOf(2_000L, 5_000L))
            return NativeRecoveryDecision.QUARANTINE

        if (e.originalRowStatus in terminalStatuses)
            return NativeRecoveryDecision.RELEASE_ALREADY_FINALIZED_LEASE

        if (e.originalRowStatus != "RECORDING")
            return NativeRecoveryDecision.QUARANTINE

        if (e.persistedStop) return NativeRecoveryDecision.FINALIZE_PERSISTED_STOP
        if (e.userRequestedStop) return NativeRecoveryDecision.PERSIST_STOP_AND_FINALIZE
        return NativeRecoveryDecision.RESUME_ORIGINAL
    }
}
