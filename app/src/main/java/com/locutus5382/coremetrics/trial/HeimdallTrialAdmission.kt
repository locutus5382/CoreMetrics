package com.locutus5382.coremetrics.trial

/**
 * Presentation-only policy for an independently installed HEIMDALL Trial.
 * A pass NEVER grants recorder authority: RecordingService must independently
 * validate its current task, raw lease, overlay and SQLite session row.
 */
internal data class HeimdallTrialAdmissionEvidence(
    val monitorStatusVerified: Boolean,
    val rawOwner: String?,
    val unfinishedRow: Boolean,
    val rawLeaseUnresolved: Boolean,
    val repairNeeded: Boolean,
    val running: Boolean,
    val starting: Boolean,
    val standby: Boolean,
    val saving: Boolean,
    val hudAttached: Boolean,
    val phase: String?,
)

internal object HeimdallTrialAdmission {
    fun mayRequestStart(e: HeimdallTrialAdmissionEvidence): Boolean =
        e.monitorStatusVerified &&
            e.rawOwner == null &&
            !e.unfinishedRow &&
            !e.rawLeaseUnresolved &&
            !e.repairNeeded &&
            !e.running && !e.starting &&
            !e.standby && !e.saving &&
            !e.hudAttached &&
            (e.phase == null || e.phase == "IDLE")

    fun mayRequestStop(e: HeimdallTrialAdmissionEvidence): Boolean =
        e.monitorStatusVerified &&
            // A stale native phase is NOT authority to touch a LEGACY or
            // unknown future engine owner, even in the isolated trial app.
            (e.rawOwner == null || e.rawOwner == "HEIMDALL_DEBUG") &&
            (e.rawOwner == "HEIMDALL_DEBUG" ||
                e.phase in setOf("RECORDING", "STOPPING",
                    "RETRY_STOP", "FINALIZING", "RETRY_SAVE"))
}
