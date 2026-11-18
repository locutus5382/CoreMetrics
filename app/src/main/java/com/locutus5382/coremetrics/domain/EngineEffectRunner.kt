package com.locutus5382.coremetrics.domain

import kotlinx.coroutines.CancellationException

/**
 * M2-B boundary. The production Android service does NOT implement this yet.
 * Adapters must acknowledge only completed platform operations, never intended ones.
 * All methods must be idempotent with respect to their identifiers and durable DB state.
 */
internal interface EngineEffectPort {
    data class Opened(val sessionId: Long, val startedAtElapsedMs: Long)

    suspend fun openSession(startId: String, profileId: Long?): Opened
    /** This must durably write RecorderLease's terminal STOP intent before returning. */
    suspend fun persistStopIntent(sessionId: Long, stopId: String)
    /** This must ensure the recording sample loop cannot keep writing after returning. */
    suspend fun pauseSampling(sessionId: Long)
    /** This must persist session finalization; not just queue the request. */
    suspend fun finalizeSession(sessionId: Long, stopId: String, reason: StopReason)
    suspend fun resumeSession(sessionId: Long)
    suspend fun closeInterruptedSession(sessionId: Long)
    /** Return only after WindowManager confirms successful attachment. */
    suspend fun attachOverlay(operationId: Long)
    /** Return only after the view is actually removed or confirmed absent. */
    suspend fun detachOverlay(operationId: Long)
}

/** Converts side-effect results into correlated events; unexpected failures NEVER become success. */
internal class EngineEffectRunner(private val port: EngineEffectPort) {
    suspend fun execute(effect: EngineEffect): EngineEvent = try {
        when (effect) {
            is EngineEffect.OpenSession -> port.openSession(effect.startId, effect.profileId).let {
                require(it.sessionId > 0) { "INVALID_SESSION_ID" }
                EngineEvent.SessionOpened(effect.startId, it.sessionId, it.startedAtElapsedMs)
            }
            is EngineEffect.PersistStopIntent -> {
                port.persistStopIntent(effect.sessionId, effect.stopId)
                EngineEvent.StopIntentPersisted(effect.sessionId, effect.stopId)
            }
            is EngineEffect.PauseSampling -> {
                port.pauseSampling(effect.sessionId)
                EngineEvent.SamplingPaused(effect.sessionId, effect.stopId)
            }
            is EngineEffect.FinalizeSession -> {
                port.finalizeSession(effect.sessionId, effect.stopId, effect.reason)
                EngineEvent.SessionFinalized(effect.sessionId, effect.stopId)
            }
            is EngineEffect.ResumeSession -> {
                port.resumeSession(effect.sessionId)
                EngineEvent.SessionResumed(effect.sessionId)
            }
            is EngineEffect.CloseInterruptedSession -> {
                port.closeInterruptedSession(effect.sessionId)
                EngineEvent.InterruptedSessionClosed(effect.sessionId)
            }
            is EngineEffect.AttachOverlay -> {
                port.attachOverlay(effect.operationId)
                EngineEvent.OverlayAttached(effect.operationId)
            }
            is EngineEffect.DetachOverlay -> {
                port.detachOverlay(effect.operationId)
                EngineEvent.OverlayDetached(effect.operationId)
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Exception) {
        // Do not put exception messages (possible private file paths/labels) in UI state.
        val reason = failure.javaClass.simpleName.ifBlank { "PLATFORM_EFFECT_FAILED" }
        when (effect) {
            is EngineEffect.OpenSession -> EngineEvent.SessionOpenFailed(effect.startId, reason)
            is EngineEffect.PersistStopIntent -> EngineEvent.StopIntentFailed(
                effect.sessionId, effect.stopId, reason)
            is EngineEffect.PauseSampling -> EngineEvent.SamplingPauseFailed(
                effect.sessionId, effect.stopId, reason)
            is EngineEffect.FinalizeSession -> EngineEvent.SessionFinalizeFailed(
                effect.sessionId, effect.stopId, reason)
            is EngineEffect.ResumeSession -> EngineEvent.SessionResumeFailed(effect.sessionId, reason)
            is EngineEffect.CloseInterruptedSession -> EngineEvent.InterruptedCloseFailed(
                effect.sessionId, reason)
            is EngineEffect.AttachOverlay -> EngineEvent.OverlayAttachFailed(effect.operationId, reason)
            is EngineEffect.DetachOverlay -> EngineEvent.OverlayDetachFailed(effect.operationId, reason)
        }
    }
}
