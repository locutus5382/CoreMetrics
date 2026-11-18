package com.locutus5382.coremetrics.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-B tests run on the JVM: no Android environment or real database required. */
class EngineControllerTest {
    private class FakePort : EngineEffectPort {
        val calls = mutableListOf<String>()
        var openGate: CompletableDeferred<Unit>? = null
        var persistFails = false
        var pauseFails = false
        var finalizationFails = false
        var closeFails = false
        var resumeFails = false
        var attachFails = false

        override suspend fun openSession(startId: String, profileId: Long?): EngineEffectPort.Opened {
            calls += "open:$startId"
            openGate?.await()
            return EngineEffectPort.Opened(42, 1234)
        }
        override suspend fun persistStopIntent(sessionId: Long, stopId: String) {
            calls += "persist:$sessionId:$stopId"
            if (persistFails) throw IllegalStateException("durable write denied")
        }
        override suspend fun pauseSampling(sessionId: Long) {
            calls += "pause:$sessionId"
            if (pauseFails) throw IllegalStateException("sampler was not stopped")
        }
        override suspend fun finalizeSession(sessionId: Long, stopId: String, reason: StopReason) {
            calls += "finalize:$sessionId:$stopId"
            if (finalizationFails) throw IllegalStateException("database error")
        }
        override suspend fun resumeSession(sessionId: Long) {
            calls += "resume:$sessionId"
            if (resumeFails) throw IllegalStateException("resume blocked")
        }
        override suspend fun closeInterruptedSession(sessionId: Long) {
            calls += "closeInterrupted:$sessionId"
            if (closeFails) throw IllegalStateException("close blocked")
        }
        override suspend fun attachOverlay(operationId: Long) {
            calls += "attach:$operationId"
            if (attachFails) throw SecurityException("overlay denied")
        }
        override suspend fun detachOverlay(operationId: Long) { calls += "detach:$operationId" }
    }

    private fun newController(port: FakePort, initial: EngineState = EngineState(epoch = "test-epoch")) =
        EngineController(initial, port, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private fun start(id: String = "start-1") = EngineCommand.Start(id, CommandOrigin.APP, 7L)
    private fun stop(id: String = "stop-1") = EngineCommand.Stop(id, CommandOrigin.QUICK_SETTINGS)
    private suspend fun EngineController.awaitRecording() = withTimeout(3000) {
        state.first { it.recording is RecordingState.Recording }
    }
    private suspend fun EngineController.awaitIdleAfterStop() = withTimeout(3000) {
        state.first { it.recording is RecordingState.Idle && it.sequence > 0L }
    }

    @Test fun startStopRunsDurableSequenceAndOneSession() = runBlocking {
        val port = FakePort()
        val engine = newController(port)
        try {
            engine.submit(start()); engine.awaitRecording()
            engine.submit(stop()); engine.awaitIdleAfterStop()
            assertEquals(listOf("open:start-1", "persist:42:stop-1", "pause:42",
                "finalize:42:stop-1"), port.calls)
        } finally { engine.cancel() }
    }

    @Test fun duplicateStartAndStopDoNotCreateExtraEffects() = runBlocking {
        val port = FakePort()
        val engine = newController(port)
        try {
            engine.submit(start()); engine.submit(start()); engine.awaitRecording()
            engine.submit(stop()); engine.submit(stop()); engine.awaitIdleAfterStop()
            assertEquals(1, port.calls.count { it.startsWith("open:") })
            assertEquals(1, port.calls.count { it.startsWith("persist:") })
            assertEquals(1, port.calls.count { it.startsWith("finalize:") })
        } finally { engine.cancel() }
    }

    @Test fun actorAcceptsStopDuringPendingOpenWithoutSecondSession() = runBlocking {
        val port = FakePort()
        val gate = CompletableDeferred<Unit>()
        port.openGate = gate
        val engine = newController(port)
        try {
            engine.submit(start())
            withTimeout(3000) { engine.state.first { it.recording is RecordingState.Starting } }
            engine.submit(stop())
            val pending = withTimeout(3000) { engine.state.first {
                (it.recording as? RecordingState.Starting)?.pendingStopId == "stop-1"
            } }
            assertTrue(pending.recording is RecordingState.Starting)
            gate.complete(Unit)
            engine.awaitIdleAfterStop()
            assertEquals(1, port.calls.count { it.startsWith("open:") })
            assertEquals(1, port.calls.count { it.startsWith("finalize:") })
        } finally { engine.cancel() }
    }

    @Test fun durableStopFailureNeverFinalizesAndRetryPreservesId() = runBlocking {
        val port = FakePort().apply { persistFails = true }
        val engine = newController(port)
        try {
            engine.submit(start()); engine.awaitRecording(); engine.submit(stop())
            withTimeout(3000) { engine.state.first {
                (it.recording as? RecordingState.StopRequested)?.persistError != null &&
                    (it.recording as? RecordingState.StopRequested)?.samplingPaused == true
            } }
            assertFalse(port.calls.any { it.startsWith("finalize:") })
            port.persistFails = false
            engine.submit(stop("retry-stop")); engine.awaitIdleAfterStop()
            assertEquals(42L, (port.calls.first { it.startsWith("finalize:") }
                .split(':')[1]).toLong())
        } finally { engine.cancel() }
    }

    @Test fun samplerPauseFailureCannotBecomeSuccess() = runBlocking {
        val port = FakePort().apply { pauseFails = true }
        val engine = newController(port)
        try {
            engine.submit(start()); engine.awaitRecording(); engine.submit(stop())
            withTimeout(3000) { engine.state.first {
                (it.recording as? RecordingState.StopRequested)?.pauseError != null
            } }
            assertFalse(port.calls.any { it.startsWith("finalize:") })
            port.pauseFails = false
            engine.submit(stop("retry-pause")); engine.awaitIdleAfterStop()
            assertEquals(1, port.calls.count { it.startsWith("finalize:") })
        } finally { engine.cancel() }
    }

    @Test fun unexpectedFinalizationErrorStaysRetryableWithoutResampling() = runBlocking {
        val port = FakePort().apply { finalizationFails = true }
        val engine = newController(port)
        try {
            engine.submit(start()); engine.awaitRecording(); engine.submit(stop())
            withTimeout(3000) { engine.state.first {
                it.recording is RecordingState.FinalizeFailed
            } }
            port.finalizationFails = false
            engine.submit(EngineCommand.RetryFinalization("retry", CommandOrigin.APP))
            engine.awaitIdleAfterStop()
            assertEquals(2, port.calls.count { it.startsWith("finalize:") })
            assertEquals(1, port.calls.count { it.startsWith("pause:") })
        } finally { engine.cancel() }
    }

    @Test fun overlayFailureIsIndependentFromActiveRecorder() = runBlocking {
        val port = FakePort().apply { attachFails = true }
        val engine = newController(port, EngineState(epoch = "test-epoch",
            hasOverlayPermission = true,
            recording = RecordingState.Recording(42, 1234, 7)))
        try {
            engine.submit(EngineCommand.SetOverlayVisible("hud", CommandOrigin.HUD, true))
            val s = withTimeout(3000) { engine.state.first {
                it.overlay is OverlayAttachment.Blocked
            } }
            assertTrue(s.recording is RecordingState.Recording)
            assertEquals(listOf("attach:1"), port.calls)
        } finally { engine.cancel() }
    }

    @Test fun recoveryResumesOriginalIdAndDoesNotOpenNew() = runBlocking {
        val port = FakePort()
        val engine = newController(port)
        try {
            engine.post(EngineEvent.Reconcile(42, 1234, 7, leaseEligible = true))
            engine.awaitRecording()
            assertEquals(listOf("resume:42"), port.calls)
        } finally { engine.cancel() }
    }

    @Test fun interruptedClosureFailureIsRecoverableByRetryFinalization() = runBlocking {
        val port = FakePort().apply { closeFails = true }
        val engine = newController(port)
        try {
            engine.post(EngineEvent.Reconcile(42, leaseEligible = false))
            withTimeout(3000) { engine.state.first { it.recording is RecordingState.FinalizeFailed } }
            port.closeFails = false
            engine.submit(EngineCommand.RetryFinalization("retry", CommandOrigin.APP))
            engine.awaitIdleAfterStop()
            assertEquals(listOf("closeInterrupted:42", "closeInterrupted:42"), port.calls)
        } finally { engine.cancel() }
    }

    @Test fun resumeFailureKeepsOriginalSessionReference() = runBlocking {
        val port = FakePort().apply { resumeFails = true }
        val engine = newController(port)
        try {
            engine.post(EngineEvent.Reconcile(42, 1234, 7, leaseEligible = true))
            val s = withTimeout(3000) { engine.state.first {
                it.recording is RecordingState.Failed
            } }
            assertEquals(42L, (s.recording as RecordingState.Failed).sessionId)
            assertEquals(listOf("resume:42"), port.calls)
        } finally { engine.cancel() }
    }
}
