package com.locutus5382.coremetrics.trial

/**
 * Read-only post-STOP proof for the last original trial session. The report is
 * diagnostic, not an authority to clear a lease or create a new session.
 */
internal data class TrialFinalizationEvidence(
    val statusVerified: Boolean,
    val originalSessionId: Long,
    val sqliteStatus: String?,
    val persistedSamples: Long,
    val rawRecoveryOwner: String?,
    val rawLeaseUnresolved: Boolean,
    val unfinishedRecordingExists: Boolean,
    val monitorRecording: Boolean,
    val monitorStarting: Boolean,
    val monitorSaving: Boolean,
    val enginePhase: String?,
)

internal enum class TrialFinalizationResult {
    VERIFIED_COMPLETION,
    MONITOR_UNAVAILABLE,
    ORIGINAL_ID_MISSING,
    ORIGINAL_ROW_MISSING,
    ORIGINAL_NOT_COMPLETED,
    SAMPLES_MISSING,
    RECOVERY_LEASE_UNRESOLVED,
    UNFINISHED_RECORDING_REMAINS,
    MONITOR_STILL_ACTIVE,
}

internal data class TrialFinalizationCheck(
    val result: TrialFinalizationResult,
    val sessionId: Long,
    val samples: Long,
) {
    val passed: Boolean get() = result == TrialFinalizationResult.VERIFIED_COMPLETION
}

/** Conservative: a missing proof is not a successful STOP. */
internal object HeimdallTrialFinalizationProof {
    fun evaluate(e: TrialFinalizationEvidence): TrialFinalizationCheck {
        val result = when {
            !e.statusVerified -> TrialFinalizationResult.MONITOR_UNAVAILABLE
            e.originalSessionId <= 0L -> TrialFinalizationResult.ORIGINAL_ID_MISSING
            e.sqliteStatus == null -> TrialFinalizationResult.ORIGINAL_ROW_MISSING
            e.sqliteStatus != "COMPLETED" ->
                TrialFinalizationResult.ORIGINAL_NOT_COMPLETED
            e.persistedSamples <= 0L -> TrialFinalizationResult.SAMPLES_MISSING
            e.rawRecoveryOwner != null || e.rawLeaseUnresolved ->
                TrialFinalizationResult.RECOVERY_LEASE_UNRESOLVED
            e.unfinishedRecordingExists ->
                TrialFinalizationResult.UNFINISHED_RECORDING_REMAINS
            e.monitorRecording || e.monitorStarting || e.monitorSaving ||
                e.enginePhase in setOf("RECORDING", "STARTING", "RECOVERING",
                    "STOPPING", "RETRY_STOP", "FINALIZING", "RETRY_SAVE") ->
                TrialFinalizationResult.MONITOR_STILL_ACTIVE
            else -> TrialFinalizationResult.VERIFIED_COMPLETION
        }
        return TrialFinalizationCheck(result, e.originalSessionId, e.persistedSamples)
    }
}
