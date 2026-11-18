package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.EngineState
import com.locutus5382.coremetrics.domain.RecordingState

/**
 * M2-L: describes state confirmed by Heimdall's authoritative reducer.
 * A UI/query snapshot MUST NOT grant authority to open or finalize a session.
 */
internal enum class HeimdallRecorderPhase {
    IDLE, STARTING, RECOVERING, RECORDING, STOPPING, RETRY_STOP,
    FINALIZING, RETRY_SAVE, NEEDS_REPAIR,
}

internal data class HeimdallRecorderPresentation(
    val phase: HeimdallRecorderPhase,
    val sessionId: Long? = null,
    val message: String,
    val recording: Boolean = false,
    val preparing: Boolean = false,
    val saving: Boolean = false,
    val retryAvailable: Boolean = false,
    val repairRequired: Boolean = false,
    val issueCode: String? = null,
)

/** Do not expose raw exception strings, or pretend a halted sampler is live. */
internal object HeimdallRecorderStatusPolicy {
    fun present(state: EngineState): HeimdallRecorderPresentation =
        when (val r = state.recording) {
            RecordingState.Idle -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.IDLE, message = "Heimdall · Idle")
            is RecordingState.Starting -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.STARTING, message = "Heimdall · Preparing",
                preparing = true)
            is RecordingState.Recovering -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.RECOVERING, r.sessionId,
                "Heimdall · Checking original recording", preparing = true)
            is RecordingState.Recording -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.RECORDING, r.sessionId,
                "Heimdall · Recording", recording = true)
            is RecordingState.StopRequested -> {
                val failed = r.persistError != null || r.pauseError != null
                HeimdallRecorderPresentation(
                    if (failed) HeimdallRecorderPhase.RETRY_STOP
                        else HeimdallRecorderPhase.STOPPING,
                    r.sessionId,
                    if (failed) "Heimdall · STOP needs retry"
                        else "Heimdall · Stopping",
                    saving = true, retryAvailable = failed,
                    issueCode = when {
                        r.persistError != null && r.pauseError != null ->
                            "STOP_AND_SAMPLER_UNCONFIRMED"
                        r.persistError != null -> "STOP_NOT_DURABLE"
                        r.pauseError != null -> "SAMPLER_NOT_QUIESCED"
                        else -> null
                    })
            }
            is RecordingState.Finalizing -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.FINALIZING, r.sessionId,
                "Heimdall · Saving session", saving = true)
            is RecordingState.FinalizeFailed -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.RETRY_SAVE, r.sessionId,
                "Heimdall · Save needs retry", saving = true,
                retryAvailable = true, issueCode = "FINALIZATION_UNCONFIRMED")
            is RecordingState.Failed -> HeimdallRecorderPresentation(
                HeimdallRecorderPhase.NEEDS_REPAIR, r.sessionId,
                "Heimdall · Recording unavailable", repairRequired = true,
                issueCode = "OWNER_REVIEW_REQUIRED")
        }
}
