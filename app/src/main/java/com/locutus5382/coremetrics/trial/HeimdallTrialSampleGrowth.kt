package com.locutus5382.coremetrics.trial

/**
 * Compares two READ-ONLY persisted snapshots of the independently installed
 * trial app, never telemetry from a UI cache or notification.
 *
 * A pass is evidence that original-session SQLite samples increased during
 * the observation window. It is not proof of Odin thermal/CPU correctness.
 */
internal data class TrialSampleEvidence(
    val statusVerified: Boolean,
    val phase: String?,
    val owner: String?,
    val sessionId: Long,
    val persistedSamples: Long,
    val rowStatus: String?,
    val unresolvedLease: Boolean,
)

internal enum class TrialSampleGrowthResult {
    VERIFIED_GROWTH,
    NOT_RECORDING,
    STATUS_UNAVAILABLE,
    OWNER_MISMATCH,
    SESSION_CHANGED,
    ROW_NOT_RECORDING,
    NO_NEW_SAMPLES,
}

internal data class TrialSampleGrowthCheck(
    val result: TrialSampleGrowthResult,
    val sessionId: Long,
    val delta: Long = 0L,
) {
    val passed: Boolean get() = result == TrialSampleGrowthResult.VERIFIED_GROWTH
}

/** Fail closed; UI buttons are advisory, not recording authority. */
internal object HeimdallTrialSampleGrowth {
    fun evaluate(first: TrialSampleEvidence, second: TrialSampleEvidence):
        TrialSampleGrowthCheck {
        val id = first.sessionId
        if (!first.statusVerified || !second.statusVerified)
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.STATUS_UNAVAILABLE, id)
        if (first.unresolvedLease || second.unresolvedLease ||
            first.owner != "HEIMDALL_DEBUG" ||
            second.owner != "HEIMDALL_DEBUG")
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.OWNER_MISMATCH, id)
        if (id <= 0L || second.sessionId != id)
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.SESSION_CHANGED, id)
        if (first.rowStatus != "RECORDING" ||
            second.rowStatus != "RECORDING")
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.ROW_NOT_RECORDING, id)
        if (first.phase != "RECORDING" ||
            second.phase != "RECORDING")
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.NOT_RECORDING, id)
        val increase = second.persistedSamples - first.persistedSamples
        if (increase <= 0L)
            return TrialSampleGrowthCheck(TrialSampleGrowthResult.NO_NEW_SAMPLES, id)
        return TrialSampleGrowthCheck(TrialSampleGrowthResult.VERIFIED_GROWTH,
            id, increase)
    }
}
