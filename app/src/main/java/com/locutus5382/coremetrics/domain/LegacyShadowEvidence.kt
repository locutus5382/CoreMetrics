package com.locutus5382.coremetrics.domain

/**
 * M2-C: read-only observations of the current CoreMetrics runtime. They are NOT
 * EngineState and MUST NOT be fed to EngineReducer as reconciliation events.
 * In particular, provider status never proves that SQLite and RecorderLease agree.
 */
internal enum class LegacyPhase {
    LIVE_RECORDING, LIVE_STARTING, LIVE_STANDBY, LIVE_IDLE,
    RECOVERY_RECORDING_CANDIDATE, RECOVERY_STANDBY_CANDIDATE,
    PROVIDER_ONLY_IDLE, INCONSISTENT,
}

/** Strict subset of StatusProvider's existing "status" Bundle contract. */
internal data class LegacyStatusFields(
    val serviceAlive: Boolean,
    val pendingRecovery: Boolean,
    val recoveringRecording: Boolean,
    val recoverySessionId: Long,
    val running: Boolean,
    val starting: Boolean,
    val standby: Boolean,
    val sessionId: Long,
    val sampleCount: Long,
    val lastSampleWallMs: Long,
    val reportedHudVisible: Boolean,
)

internal data class LegacyStatusObservation(
    val phase: LegacyPhase,
    val sessionId: Long? = null,
    val recordedSampleCount: Long? = null,
    val sampleAgeMs: Long? = null,
    val sampleFresh: Boolean = false,
    /** Provider-reported visibility, NOT confirmation Android is displaying the window. */
    val reportedHudVisible: Boolean = false,
) {
    /** A pending lease is not active recording. Only a live service can report this. */
    val liveRecording: Boolean get() = phase == LegacyPhase.LIVE_RECORDING
}

internal object LegacyStatusPolicy {
    // A 2s/5s sampler can legitimately be late; this is only a display staleness
    // threshold. It does NOT alter the recorder's missed-tick/failure policy.
    const val DISPLAY_FRESHNESS_WINDOW_MS: Long = 15_000L

    fun interpret(value: LegacyStatusFields, nowWallMs: Long): LegacyStatusObservation {
        if (value.serviceAlive && value.pendingRecovery ||
            value.running && (!value.serviceAlive || value.starting || value.standby || value.sessionId <= 0L) ||
            value.starting && (!value.serviceAlive || value.standby) ||
            value.standby && !value.serviceAlive ||
            !value.serviceAlive && value.pendingRecovery &&
                value.recoveringRecording && value.recoverySessionId <= 0L
        ) return LegacyStatusObservation(LegacyPhase.INCONSISTENT)

        if (!value.serviceAlive) {
            if (value.pendingRecovery) return LegacyStatusObservation(
                if (value.recoveringRecording) LegacyPhase.RECOVERY_RECORDING_CANDIDATE
                else LegacyPhase.RECOVERY_STANDBY_CANDIDATE,
                sessionId = value.recoverySessionId.takeIf { value.recoveringRecording && it > 0L },
            )
            return LegacyStatusObservation(LegacyPhase.PROVIDER_ONLY_IDLE)
        }
        if (value.running) {
            val age = if (value.lastSampleWallMs > 0L &&
                nowWallMs >= value.lastSampleWallMs)
                nowWallMs - value.lastSampleWallMs else null
            return LegacyStatusObservation(
                phase = LegacyPhase.LIVE_RECORDING,
                sessionId = value.sessionId,
                recordedSampleCount = value.sampleCount.takeIf { it >= 0L },
                sampleAgeMs = age,
                sampleFresh = age != null && age <= DISPLAY_FRESHNESS_WINDOW_MS,
                reportedHudVisible = value.reportedHudVisible,
            )
        }
        if (value.starting) return LegacyStatusObservation(LegacyPhase.LIVE_STARTING)
        if (value.standby) return LegacyStatusObservation(LegacyPhase.LIVE_STANDBY,
            reportedHudVisible = value.reportedHudVisible)
        return LegacyStatusObservation(LegacyPhase.LIVE_IDLE)
    }
}

/** Recovery is authoritative only after a separate, service-owned reconciliation. */
internal enum class LegacyRecoveryKind {
    NO_VALID_LEASE, STANDBY_ONLY, OPEN_RECORDING_CANDIDATE,
    PERSISTED_STOP_CANDIDATE, DATABASE_ROW_NOT_OPEN, UNAVAILABLE,
}

internal data class LegacyLeaseFields(
    val recording: Boolean,
    val sessionId: Long,
    val stopRequested: Boolean,
)

internal data class LegacyRecoveryCandidate(
    val kind: LegacyRecoveryKind,
    val sessionId: Long? = null,
) {
    /** A read-only hint; never directly dispatch ResumeSession or FinalizeSession. */
    val requiresServiceReconciliation: Boolean get() =
        kind == LegacyRecoveryKind.OPEN_RECORDING_CANDIDATE ||
        kind == LegacyRecoveryKind.PERSISTED_STOP_CANDIDATE
}

internal object LegacyRecoveryPolicy {
    fun interpret(lease: LegacyLeaseFields?, rowStatus: String?): LegacyRecoveryCandidate {
        if (lease == null) return LegacyRecoveryCandidate(LegacyRecoveryKind.NO_VALID_LEASE)
        if (!lease.recording) return LegacyRecoveryCandidate(LegacyRecoveryKind.STANDBY_ONLY)
        if (lease.sessionId <= 0L || rowStatus != "RECORDING")
            return LegacyRecoveryCandidate(LegacyRecoveryKind.DATABASE_ROW_NOT_OPEN)
        return LegacyRecoveryCandidate(
            if (lease.stopRequested) LegacyRecoveryKind.PERSISTED_STOP_CANDIDATE
            else LegacyRecoveryKind.OPEN_RECORDING_CANDIDATE,
            lease.sessionId,
        )
    }
}
