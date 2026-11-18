package com.locutus5382.coremetrics.engine

import android.app.Application
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.domain.CommandOrigin
import com.locutus5382.coremetrics.domain.EngineCommand
import com.locutus5382.coremetrics.domain.EngineController
import com.locutus5382.coremetrics.domain.EngineEvent
import com.locutus5382.coremetrics.domain.EngineState
import com.locutus5382.coremetrics.domain.RecordingState
import com.locutus5382.coremetrics.domain.LegacyGuardedEffectPort
import com.locutus5382.coremetrics.domain.StopReason
import com.locutus5382.coremetrics.telemetry.Snapshot
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Opt-in DEBUG runtime attached to the existing RecordingService (no second
 * Android Service, process, sampler, database instance or overlay).
 *
 * Construction alone does nothing. Only start() after service-side admission
 * can transfer ownership. The legacy onStartCommand dispatch MUST NOT run
 * while the debug reservation is held.
 */
internal class HeimdallDebugEngineRuntime(
    private val app: Application,
    private val db: SessionStore,
    private val parent: CoroutineScope,
    private val intervalMs: Long,
    private val onState: suspend (EngineState) -> Unit,
) {
    val epoch: String = UUID.randomUUID().toString()
    private val scope = CoroutineScope(
        parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private var controller: EngineController? = null

    suspend fun start(
        handoff: LegacyHandoffEvidence,
        profileId: Long? = null,
    ): Boolean {
        check(controller == null) { "ENGINE_ALREADY_CREATED" }
        val io = HeimdallAndroidSessionIo(app, db, intervalMs)
        lateinit var engine: EngineController
        val host = HeimdallSessionHost(
            serviceEpoch = epoch,
            io = io,
            parentScope = scope,
            intervalMs = intervalMs,
            onFatalSampling = { _, _ ->
                // Only the coordinator may decide STOP. Never finalize here.
                // The only coordinator decides STOP. Lease integrity
                // faults must not be misreported as sensor sample noise.
                val reason = HeimdallFatalStopPolicy.reason(errorType)
                engine.submit(EngineCommand.Stop(
                    "fatal-${UUID.randomUUID()}", CommandOrigin.RECOVERY,
                    reason))
            },
        )
        if (!host.acceptExclusiveHandoff(handoff)) return false
        engine = EngineController(EngineState(epoch = epoch),
            LegacyGuardedEffectPort(epoch, host), scope)
        controller = engine
        scope.launch { engine.state.collect(onState) }
        engine.submit(EngineCommand.Start(
            "debug-start-${UUID.randomUUID()}", CommandOrigin.APP, profileId))
        return true
    }

    /**
     * Rebuilds the SAME original recording after a monitor-process restart.
     * Never invokes IO.begin(): the native host revalidates its own lease/row.
     *
     * A cold STOP is made durable BEFORE any sampler can resume.
     */
    suspend fun recover(
        handoff: NativeRecoveryHandoffEvidence,
        originalSessionId: Long,
        persistedStop: Boolean,
        stopRequestedNow: Boolean,
    ): Boolean {
        check(controller == null) { "ENGINE_ALREADY_CREATED" }
        check(handoff.originalSessionId == originalSessionId && originalSessionId > 0L) {
            "RECOVERY_SESSION_MISMATCH"
        }
        val io = HeimdallAndroidSessionIo(app, db, intervalMs)
        lateinit var engine: EngineController
        val host = HeimdallSessionHost(
            serviceEpoch = epoch,
            io = io,
            parentScope = scope,
            intervalMs = intervalMs,
            onFatalSampling = { _, _ ->
                // The only coordinator decides STOP. Lease integrity
                // faults must not be misreported as sensor sample noise.
                val reason = HeimdallFatalStopPolicy.reason(errorType)
                engine.submit(EngineCommand.Stop(
                    "fatal-${UUID.randomUUID()}", CommandOrigin.RECOVERY,
                    reason))
            },
        )
        if (!host.acceptNativeRecoveryHandoff(handoff)) return false
        // Only a persisted STOP, never a mere incoming Intent, authorizes
        // terminal reconciliation without restarting the sampler.
        if (stopRequestedNow && !persistedStop)
            check(io.persistStop(originalSessionId)) { "RECOVERY_STOP_NOT_DURABLE" }
        val terminal = persistedStop || stopRequestedNow
        if (terminal) {
            check(host.preflightPersistedStop(originalSessionId)) {
                "TERMINAL_RECOVERY_UNVALIDATED"
            }
        } else {
            check(host.preflightRecovery(originalSessionId)) {
                "RESUME_RECOVERY_UNVALIDATED"
            }
        }
        val originalWall = db.originalStartMs(originalSessionId)
            ?: error("ORIGINAL_START_TIME_UNAVAILABLE")
        val nowElapsed = SystemClock.elapsedRealtime()
        val originalElapsed = (nowElapsed -
            (System.currentTimeMillis() - originalWall).coerceAtLeast(0L))
            .coerceIn(0L, nowElapsed)
        engine = EngineController(EngineState(epoch = epoch),
            LegacyGuardedEffectPort(epoch, host), scope)
        controller = engine
        scope.launch { engine.state.collect(onState) }
        engine.post(EngineEvent.Reconcile(
            sessionId = originalSessionId,
            startedAtElapsedMs = originalElapsed,
            leaseEligible = !terminal,
            stopIntentPersisted = terminal,
        ))
        return true
    }

    suspend fun stop(): Boolean {
        val active = controller ?: return false
        // An unacknowledged native recovery must not be stopped before the
        // host actually resumes its sampler. The owning service retains
        // the STOP request and sends it once Recording is acknowledged.
        if (active.state.value.recording is RecordingState.Recovering ||
            active.state.value.recording is RecordingState.Idle) return false
        // Repeated notification/QS STOP must also recover a finalized-but-
        // uncommitted original row, rather than leaving a live foreground
        // service with an unresponsive STOP control.
        if (active.state.value.recording is RecordingState.FinalizeFailed)
            active.submit(EngineCommand.RetryFinalization(
                "debug-finalize-retry-${UUID.randomUUID()}", CommandOrigin.APP))
        else active.submit(EngineCommand.Stop(
            "debug-stop-${UUID.randomUUID()}", CommandOrigin.APP))
        return true
    }

    fun cancelWithoutImplicitStop() {
        // Android system destruction is NOT a user STOP: retain the lease
        // for validated original-session recovery, then cancel the jobs.
        controller?.cancel()
        scope.cancel()
    }
}
