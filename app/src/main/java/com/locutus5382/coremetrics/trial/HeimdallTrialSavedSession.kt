package com.locutus5382.coremetrics.trial

/**
 * Read-only trial evidence captured immediately before sending a user STOP,
 * and independently from the original SQLite row after STOP.
 *
 * Never clears a lease, closes a row, authorizes a new recording or edits
 * session data. A success is limited to the isolated app's persisted state.
 */
internal data class TrialSavedSessionEvidence(
    val statusVerified: Boolean,
    val enginePhase: String?,
    val rawOwner: String?,
    val originalSessionId: Long,
    val rowStatus: String?,
    val endedAtMs: Long?,
    val persistedSamples: Long,
    val totalSessionRows: Long,
    val unfinishedRowExists: Boolean,
    val rawLeaseUnresolved: Boolean,
    val repairNeeded: Boolean,
)

internal enum class TrialSavedSessionResult {
    VERIFIED_SAVED,
    BASELINE_UNVERIFIED,
    STATUS_UNAVAILABLE,
    ORIGINAL_SESSION_CHANGED,
    EXTRA_SESSION_ROW,
    ROW_NOT_COMPLETED,
    MISSING_END_TIME,
    SAMPLE_COUNT_DECREASED,
    NO_SAVED_SAMPLES,
    LEASE_STILL_ACTIVE,
    UNFINISHED_SESSION_REMAINS,
    ENGINE_STILL_ACTIVE,
}

internal data class TrialSavedSessionCheck(
    val result: TrialSavedSessionResult,
    val originalSessionId: Long,
    val preservedSamples: Long = 0,
) {
    val passed: Boolean get() = result == TrialSavedSessionResult.VERIFIED_SAVED
}

/**
 * A completion is proved only by the original row and raw lease evidence.
 * UI phase is a cross-check, never proof of finalization by itself.
 */
internal object HeimdallTrialSavedSession {
    fun evaluate(
        beforeStop: TrialSavedSessionEvidence,
        afterStop: TrialSavedSessionEvidence,
    ): TrialSavedSessionCheck {
        val id = beforeStop.originalSessionId
        fun failure(code: TrialSavedSessionResult) =
            TrialSavedSessionCheck(code, id)

        if (!beforeStop.statusVerified || id <= 0L ||
            beforeStop.enginePhase !in setOf("RECORDING", "STOPPING", "RETRY_STOP",
                "FINALIZING", "RETRY_SAVE") ||
            beforeStop.rawOwner != "HEIMDALL_DEBUG" ||
            beforeStop.rowStatus != "RECORDING" ||
            beforeStop.totalSessionRows <= 0L ||
            beforeStop.rawLeaseUnresolved || beforeStop.repairNeeded)
            return failure(TrialSavedSessionResult.BASELINE_UNVERIFIED)

        if (!afterStop.statusVerified)
            return failure(TrialSavedSessionResult.STATUS_UNAVAILABLE)
        if (afterStop.originalSessionId != id)
            return failure(TrialSavedSessionResult.ORIGINAL_SESSION_CHANGED)
        if (afterStop.totalSessionRows != beforeStop.totalSessionRows)
            return failure(TrialSavedSessionResult.EXTRA_SESSION_ROW)
        if (afterStop.rowStatus != "COMPLETED")
            return failure(TrialSavedSessionResult.ROW_NOT_COMPLETED)
        if ((afterStop.endedAtMs ?: 0L) <= 0L)
            return failure(TrialSavedSessionResult.MISSING_END_TIME)
        if (afterStop.persistedSamples < beforeStop.persistedSamples)
            return failure(TrialSavedSessionResult.SAMPLE_COUNT_DECREASED)
        if (afterStop.persistedSamples <= 0L)
            return failure(TrialSavedSessionResult.NO_SAVED_SAMPLES)
        if (afterStop.rawOwner != null || afterStop.rawLeaseUnresolved ||
            afterStop.repairNeeded)
            return failure(TrialSavedSessionResult.LEASE_STILL_ACTIVE)
        if (afterStop.unfinishedRowExists)
            return failure(TrialSavedSessionResult.UNFINISHED_SESSION_REMAINS)
        if (afterStop.enginePhase !in setOf(null, "IDLE"))
            return failure(TrialSavedSessionResult.ENGINE_STILL_ACTIVE)
        return TrialSavedSessionCheck(
            TrialSavedSessionResult.VERIFIED_SAVED, id,
            afterStop.persistedSamples)
    }
}
