package com.locutus5382.coremetrics.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * M2-B shadow coordinator: no Android service uses this controller yet.
 *
 * The reducer actor and effect executor use SEPARATE coroutines. Blocking I/O cannot
 * block processing another START/STOP command. All effects execute on one FIFO lane:
 * a durable STOP write is completed before PauseSampling and neither completion can
 * cause finalization until BOTH have been acknowledged by the reducer.
 *
 * Platform adapters will be wired in M2-C, after hardware and lifecycle tests.
 */
internal class EngineController(
    initial: EngineState,
    port: EngineEffectPort,
    parent: CoroutineScope,
    reducerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    effectDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(
        parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    // Only low-frequency lifecycle/command events go through these queues, not samples.
    // Unbounded queues prevent worker/event-actor deadlock; the future service boundary
    // must validate and rate-limit untrusted / repeat caller commands.
    private val inbox = Channel<EngineEvent>(Channel.UNLIMITED)
    private val effects = Channel<EngineEffect>(Channel.UNLIMITED)
    private val runner = EngineEffectRunner(port)
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<EngineState> = mutableState.asStateFlow()

    init {
        scope.launch(reducerDispatcher) {
            for (event in inbox) {
                val transition = EngineReducer.reduce(mutableState.value, event)
                mutableState.value = transition.state
                transition.effects.forEach { effects.send(it) }
            }
        }
        scope.launch(effectDispatcher) {
            for (effect in effects) {
                // Failures are encoded as explicit failure events by EngineEffectRunner.
                inbox.send(runner.execute(effect))
            }
        }
    }

    /** Commands and reconciler events must be admitted from the platform owner. */
    suspend fun submit(command: EngineCommand) = inbox.send(EngineEvent.Command(command))
    suspend fun post(event: EngineEvent) = inbox.send(event)

    /** For callback APIs where a suspend function is impossible; false requires explicit handling. */
    fun tryPost(event: EngineEvent): Boolean = inbox.trySend(event).isSuccess

    /** Cancels only this isolated controller. NEVER treat this as a user STOP operation. */
    fun cancel() { scope.cancel() }
}
