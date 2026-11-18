package com.locutus5382.coremetrics.domain

/**
 * M2-A: Android-free *proposed* engine contract. Production RecordingService still owns
 * recording until a later, separately tested adapter replaces its mutable control state.
 * Session IDs remain SQLite Longs; commands are uniquely identified by their origin.
 */
internal enum class CommandOrigin { APP, QUICK_SETTINGS, NOTIFICATION, HUD, RECOVERY }
internal enum class StopReason { USER, SAMPLE_FAILURE, STORAGE_ERROR, INTERRUPTED }
internal enum class HudPresentation { COMPACT, EXPANDED, EDGE_DOCKED }
internal enum class DataIssue { STALE, SAMPLE_GAP, SOURCE_UNAVAILABLE, RECOVERED }
internal enum class CommandOutcome { ACCEPTED, IGNORED, REJECTED }

internal data class CommandAck(
    val commandId: String,
    val outcome: CommandOutcome,
    val reason: String? = null,
)

internal sealed interface RecordingState {
    data object Idle : RecordingState
    data class Starting(
        val startId: String,
        val profileId: Long?,
        val pendingStopId: String? = null,
    ) : RecordingState
    data class Recording(
        val sessionId: Long,
        val startedAtElapsedMs: Long,
        val profileId: Long?,
    ) : RecordingState
    /** STOP accepted locally, but no durable stop-intent confirmation yet. */
    data class StopRequested(
        val sessionId: Long,
        val stopId: String,
        val reason: StopReason,
        val persistError: String? = null,
        val durableStop: Boolean = false,
        val samplingPaused: Boolean = false,
        val pauseError: String? = null,
    ) : RecordingState
    /** STOP is durable; session finalization may safely be retried. */
    data class Finalizing(
        val sessionId: Long,
        val stopId: String,
        val reason: StopReason,
    ) : RecordingState
    /** Durable stop exists, but finalization failed. A retry must not resume sampling. */
    data class FinalizeFailed(
        val sessionId: Long,
        val stopId: String,
        val reason: StopReason,
        val error: String,
    ) : RecordingState
    data class Recovering(val sessionId: Long, val startedAtElapsedMs: Long, val profileId: Long?) : RecordingState
    data class Failed(val reason: String, val sessionId: Long? = null) : RecordingState
}

internal sealed interface OverlayAttachment {
    data object Detached : OverlayAttachment
    data class Attaching(val operationId: Long) : OverlayAttachment
    data object Attached : OverlayAttachment
    data class Detaching(val operationId: Long) : OverlayAttachment
    data class Blocked(val reason: String) : OverlayAttachment
    /** Host may still own the WindowManager view; do not create another overlay. */
    data class CleanupRequired(val operationId: Long, val reason: String) : OverlayAttachment
}

/**
 * epoch is assigned by the platform owner upon process creation. sequence increments on
 * transitions *within* that epoch. A consumer must not order snapshots across epochs.
 * A cold UI cache is never proof of active recording.
 */
internal data class EngineState(
    val epoch: String,
    val sequence: Long = 0,
    val recording: RecordingState = RecordingState.Idle,
    val overlay: OverlayAttachment = OverlayAttachment.Detached,
    val wantsOverlay: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val presentation: HudPresentation = HudPresentation.COMPACT,
    val dataIssues: Set<DataIssue> = emptySet(),
    val nextOverlayOperation: Long = 1,
    val recentCommandIds: List<String> = emptyList(),
    val lastAck: CommandAck? = null,
)

internal sealed interface EngineCommand {
    val id: String
    val origin: CommandOrigin

    data class Start(
        override val id: String,
        override val origin: CommandOrigin,
        val profileId: Long? = null,
    ) : EngineCommand

    data class Stop(
        override val id: String,
        override val origin: CommandOrigin,
        val reason: StopReason = StopReason.USER,
    ) : EngineCommand

    /** Retry a persisted terminal stop after an I/O error. Never starts sampling. */
    data class RetryFinalization(
        override val id: String,
        override val origin: CommandOrigin,
    ) : EngineCommand

    data class SetOverlayVisible(
        override val id: String,
        override val origin: CommandOrigin,
        val visible: Boolean,
    ) : EngineCommand

    data class SetHudPresentation(
        override val id: String,
        override val origin: CommandOrigin,
        val value: HudPresentation,
    ) : EngineCommand
}

/** Effect completions carry their initiating request or operation IDs, so stale results
 * cannot resurrect a session or make a failed overlay appear 'attached'. */
internal sealed interface EngineEvent {
    data class Command(val value: EngineCommand) : EngineEvent
    data class SessionOpened(
        val startId: String, val sessionId: Long, val startedAtElapsedMs: Long,
    ) : EngineEvent
    data class SessionOpenFailed(val startId: String, val reason: String) : EngineEvent
    data class StopIntentPersisted(val sessionId: Long, val stopId: String) : EngineEvent
    data class SamplingPaused(val sessionId: Long, val stopId: String) : EngineEvent
    data class SamplingPauseFailed(val sessionId: Long, val stopId: String, val reason: String) : EngineEvent
    data class StopIntentFailed(val sessionId: Long, val stopId: String, val reason: String) : EngineEvent
    data class SessionFinalized(val sessionId: Long, val stopId: String) : EngineEvent
    data class SessionFinalizeFailed(val sessionId: Long, val stopId: String, val reason: String) : EngineEvent
    data class OverlayAttached(val operationId: Long) : EngineEvent
    data class OverlayAttachFailed(val operationId: Long, val reason: String) : EngineEvent
    data class OverlayDetached(val operationId: Long) : EngineEvent
    data class OverlayDetachFailed(val operationId: Long, val reason: String) : EngineEvent
    data class OverlayPermissionChanged(val granted: Boolean) : EngineEvent
    /** Emitted *only* after reading the real SQLite row and durable recovery lease. */
    data class Reconcile(
        val sessionId: Long?,
        val startedAtElapsedMs: Long = 0,
        val profileId: Long? = null,
        val leaseEligible: Boolean = false,
        val stopIntentPersisted: Boolean = false,
    ) : EngineEvent
    data class SessionResumed(val sessionId: Long) : EngineEvent
    data class SessionResumeFailed(val sessionId: Long, val reason: String) : EngineEvent
    data class InterruptedSessionClosed(val sessionId: Long) : EngineEvent
    data class InterruptedCloseFailed(val sessionId: Long, val reason: String) : EngineEvent
}

internal sealed interface EngineEffect {
    data class OpenSession(val startId: String, val profileId: Long?) : EngineEffect
    /** Pause the live sample loop promptly; does not claim STOP durability. */
    data class PauseSampling(val sessionId: Long, val stopId: String) : EngineEffect
    /** The platform must persist STOP *before* acknowledging it or finalizing. */
    data class PersistStopIntent(val sessionId: Long, val stopId: String) : EngineEffect
    data class FinalizeSession(val sessionId: Long, val stopId: String, val reason: StopReason) : EngineEffect
    data class ResumeSession(val sessionId: Long) : EngineEffect
    data class CloseInterruptedSession(val sessionId: Long) : EngineEffect
    data class AttachOverlay(val operationId: Long) : EngineEffect
    data class DetachOverlay(val operationId: Long) : EngineEffect
}

internal data class EngineTransition(
    val state: EngineState,
    val effects: List<EngineEffect> = emptyList(),
)
