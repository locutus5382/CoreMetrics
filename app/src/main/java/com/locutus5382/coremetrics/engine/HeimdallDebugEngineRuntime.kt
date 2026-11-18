package com.locutus5382.coremetrics.engine

import android.app.Application
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.domain.CommandOrigin
import com.locutus5382.coremetrics.domain.EngineCommand
import com.locutus5382.coremetrics.domain.EngineController
import com.locutus5382.coremetrics.domain.EngineState
import com.locutus5382.coremetrics.domain.LegacyGuardedEffectPort
import com.locutus5382.coremetrics.domain.StopReason
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
                engine.submit(EngineCommand.Stop(
                    "fatal-${UUID.randomUUID()}", CommandOrigin.RECOVERY,
                    StopReason.SAMPLE_FAILURE))
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

    suspend fun stop(): Boolean {
        val active = controller ?: return false
        active.submit(EngineCommand.Stop(
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
