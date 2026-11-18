package com.locutus5382.coremetrics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pure-JVM contract tests, independent of Android and SQLite. */
class EngineReducerTest {
    private fun base() = EngineState(epoch = "boot-epoch-A")
    private fun start(id: String = "start-1") = EngineEvent.Command(
        EngineCommand.Start(id, CommandOrigin.APP, 11L))
    private fun stop(id: String = "stop-1") = EngineEvent.Command(
        EngineCommand.Stop(id, CommandOrigin.QUICK_SETTINGS))
    private fun reduce(s: EngineState, e: EngineEvent) = EngineReducer.reduce(s, e)
    private fun recording(id: Long = 42L) = base().copy(recording =
        RecordingState.Recording(id, 1_000L, 11L))

    @Test fun startOpensExactlyOneSession() {
        val first = reduce(base(), start())
        assertEquals(RecordingState.Starting("start-1", 11L), first.state.recording)
        assertEquals(listOf(EngineEffect.OpenSession("start-1", 11L)), first.effects)
        assertEquals(CommandOutcome.ACCEPTED, first.state.lastAck?.outcome)
        val second = reduce(first.state, start("start-2"))
        assertTrue(second.state.recording is RecordingState.Starting)
        assertTrue(second.effects.isEmpty())
        assertEquals(CommandOutcome.IGNORED, second.state.lastAck?.outcome)
    }

    @Test fun repeatedIntentIdIsIgnoredWithoutGenerationChurn() {
        val first = reduce(base(), start())
        val repeated = reduce(first.state, start())
        assertEquals(first.state, repeated.state)
        assertTrue(repeated.effects.isEmpty())
    }

    @Test fun sessionOpenAcceptsOnlyMatchingRequest() {
        val s = reduce(base(), start()).state
        val bad = reduce(s, EngineEvent.SessionOpened("wrong", 42L, 500L))
        assertEquals(s, bad.state)
        val good = reduce(s, EngineEvent.SessionOpened("start-1", 42L, 500L))
        assertEquals(RecordingState.Recording(42L, 500L, 11L), good.state.recording)
    }

    @Test fun stopWhileStartingAppliesOnceSessionIsOpened() {
        val s = reduce(base(), start()).state
        val stopped = reduce(s, stop())
        assertEquals("stop-1", (stopped.state.recording as RecordingState.Starting).pendingStopId)
        assertTrue(stopped.effects.isEmpty()) // no row to journal STOP against yet
        val opened = reduce(stopped.state, EngineEvent.SessionOpened("start-1", 42L, 500L))
        assertTrue(opened.state.recording is RecordingState.StopRequested)
        assertEquals(listOf(EngineEffect.PauseSampling(42),
            EngineEffect.PersistStopIntent(42, "stop-1")), opened.effects)
    }

    @Test fun durableStopIsRequiredBeforeFinalization() {
        val pending = reduce(recording(), stop())
        assertEquals(RecordingState.StopRequested(42L, "stop-1", StopReason.USER),
            pending.state.recording)
        assertTrue(pending.effects.none { it is EngineEffect.FinalizeSession })
        val confirmed = reduce(pending.state, EngineEvent.StopIntentPersisted(42, "stop-1"))
        assertTrue(confirmed.state.recording is RecordingState.Finalizing)
        assertEquals(listOf(EngineEffect.FinalizeSession(42, "stop-1", StopReason.USER)),
            confirmed.effects)
        val saved = reduce(confirmed.state, EngineEvent.SessionFinalized(42, "stop-1"))
        assertEquals(RecordingState.Idle, saved.state.recording)
    }

    @Test fun duplicateStopNeverSchedulesSecondPersistence() {
        val pending = reduce(recording(), stop())
        val again = reduce(pending.state, stop("stop-2"))
        assertTrue(again.effects.isEmpty())
        assertTrue(again.state.recording is RecordingState.StopRequested)
        val finalizing = reduce(pending.state, EngineEvent.StopIntentPersisted(42, "stop-1"))
        assertTrue(reduce(finalizing.state, stop("stop-3")).effects.isEmpty())
    }

    @Test fun stopPersistenceFailureCannotFinalizeAndCanBeRetried() {
        val pending = reduce(recording(), stop()).state
        val failed = reduce(pending, EngineEvent.StopIntentFailed(42, "stop-1", "DISK_FULL"))
        assertEquals("DISK_FULL", (failed.state.recording as RecordingState.StopRequested).persistError)
        assertTrue(failed.effects.isEmpty())
        val retry = reduce(failed.state, stop("stop-retry"))
        assertEquals(listOf(EngineEffect.PersistStopIntent(42, "stop-1")), retry.effects)
    }

    @Test fun startWhileStopPendingCannotCreateNewSession() {
        val pending = reduce(recording(), stop()).state
        assertTrue(reduce(pending, start("other-start")).effects.isEmpty())
    }

    @Test fun staleCompletionsDoNotMutateState() {
        val pending = reduce(recording(), stop()).state
        assertEquals(pending, reduce(pending, EngineEvent.StopIntentPersisted(99, "stop-1")).state)
        assertEquals(pending, reduce(pending, EngineEvent.StopIntentPersisted(42, "other-id")).state)
        assertEquals(pending, reduce(pending, EngineEvent.SessionFinalized(42, "stop-1")).state)
    }

    @Test fun missingPermissionBlocksHudButNotRecording() {
        val s = recording()
        val show = EngineEvent.Command(EngineCommand.SetOverlayVisible("hud-1", CommandOrigin.HUD, true))
        val result = reduce(s, show)
        assertEquals(s.recording, result.state.recording)
        assertEquals(OverlayAttachment.Blocked("PERMISSION_MISSING"), result.state.overlay)
        assertTrue(result.effects.isEmpty())
    }

    @Test fun hudMustBeAcknowledgedAfterActualAttachment() {
        val s = recording().copy(hasOverlayPermission = true)
        val result = reduce(s, EngineEvent.Command(
            EngineCommand.SetOverlayVisible("hud-1", CommandOrigin.APP, true)))
        val attaching = result.state.overlay as OverlayAttachment.Attaching
        assertEquals(EngineEffect.AttachOverlay(attaching.operationId), result.effects.single())
        assertFalse(result.state.overlay is OverlayAttachment.Attached)
        val confirmed = reduce(result.state, EngineEvent.OverlayAttached(attaching.operationId))
        assertEquals(OverlayAttachment.Attached, confirmed.state.overlay)
    }

    @Test fun attachFailureIsIsolatedFromRecording() {
        val s = recording().copy(hasOverlayPermission = true)
        val attaching = reduce(s, EngineEvent.Command(
            EngineCommand.SetOverlayVisible("hud-1", CommandOrigin.APP, true)))
        val id = (attaching.state.overlay as OverlayAttachment.Attaching).operationId
        val failed = reduce(attaching.state, EngineEvent.OverlayAttachFailed(id, "BAD_TOKEN"))
        assertEquals(s.recording, failed.state.recording)
        assertEquals(OverlayAttachment.Blocked("BAD_TOKEN"), failed.state.overlay)
    }

    @Test fun hiddenDuringPendingAttachDetachesOnConfirmation() {
        val s = base().copy(hasOverlayPermission = true)
        val requesting = reduce(s, EngineEvent.Command(
            EngineCommand.SetOverlayVisible("hud-1", CommandOrigin.APP, true))).state
        val oldId = (requesting.overlay as OverlayAttachment.Attaching).operationId
        val hidden = reduce(requesting, EngineEvent.Command(
            EngineCommand.SetOverlayVisible("hud-2", CommandOrigin.APP, false)))
        assertTrue(hidden.state.overlay is OverlayAttachment.Attaching)
        val attached = reduce(hidden.state, EngineEvent.OverlayAttached(oldId))
        assertTrue(attached.state.overlay is OverlayAttachment.Detaching)
        assertEquals(1, attached.effects.size)
        assertTrue(attached.effects.single() is EngineEffect.DetachOverlay)
    }

    @Test fun staleAttachCompletionCannotResurrectHud() {
        val s = base().copy(hasOverlayPermission = true)
        val a = reduce(s, EngineEvent.Command(
            EngineCommand.SetOverlayVisible("hud-a", CommandOrigin.APP, true))).state
        val oldId = (a.overlay as OverlayAttachment.Attaching).operationId
        val b = reduce(a, EngineEvent.OverlayAttachFailed(oldId, "DENIED")).state
        assertEquals(b, reduce(b, EngineEvent.OverlayAttached(oldId)).state)
    }

    @Test fun overlayPermissionRevocationDoesNotStopRecorder() {
        val s = recording().copy(hasOverlayPermission = true, wantsOverlay = true,
            overlay = OverlayAttachment.Attached)
        val revoked = reduce(s, EngineEvent.OverlayPermissionChanged(false))
        assertEquals(s.recording, revoked.state.recording)
        assertTrue(revoked.state.overlay is OverlayAttachment.Detaching)
        assertTrue(revoked.effects.single() is EngineEffect.DetachOverlay)
        val opId = (revoked.state.overlay as OverlayAttachment.Detaching).operationId
        val done = reduce(revoked.state, EngineEvent.OverlayDetached(opId))
        assertEquals(OverlayAttachment.Blocked("PERMISSION_MISSING"), done.state.overlay)
    }

    @Test fun validLeaseResumesOriginalSessionAndMarksGap() {
        val step = reduce(base(), EngineEvent.Reconcile(
            sessionId = 42L, startedAtElapsedMs = 500, leaseEligible = true))
        assertEquals(RecordingState.Recovering(42L, 500, null), step.state.recording)
        assertEquals(listOf(EngineEffect.ResumeSession(42L)), step.effects)
        assertTrue(DataIssue.SAMPLE_GAP in step.state.dataIssues)
        val resumed = reduce(step.state, EngineEvent.SessionResumed(42))
        assertEquals(RecordingState.Recording(42L, 500, null), resumed.state.recording)
        assertEquals(step.state.epoch, resumed.state.epoch)
    }

    @Test fun persistedStopOnRestartFinalizesAndNeverResumes() {
        val step = reduce(base(), EngineEvent.Reconcile(
            42L, leaseEligible = true, stopIntentPersisted = true))
        assertTrue(step.state.recording is RecordingState.Finalizing)
        assertEquals(listOf(EngineEffect.FinalizeSession(42L, "recovery-stop-42", StopReason.USER)),
            step.effects)
        assertEquals(step.state, reduce(step.state, EngineEvent.SessionResumed(42)).state)
    }

    @Test fun invalidLeaseClosesAsInterrupted() {
        val step = reduce(base(), EngineEvent.Reconcile(42L, leaseEligible = false))
        assertEquals(listOf(EngineEffect.CloseInterruptedSession(42L)), step.effects)
        val done = reduce(step.state, EngineEvent.InterruptedSessionClosed(42L))
        assertEquals(RecordingState.Idle, done.state.recording)
    }

    @Test fun epochConstantWithinProcessAndSequenceIncrementsOnRealTransitions() {
        val first = reduce(base(), start()).state
        assertEquals(1L, first.sequence)
        val same = reduce(first, EngineEvent.SessionOpened("wrong", 22, 100)).state
        assertEquals(first, same)
        val next = reduce(same, EngineEvent.SessionOpened("start-1", 22, 100)).state
        assertEquals(2L, next.sequence)
        assertEquals("boot-epoch-A", next.epoch)
    }

    @Test fun failedFinalizationCanOnlyRetryFinalizingOriginalSession() {
        val request = reduce(recording(), stop()).state
        val durable = reduce(request, EngineEvent.StopIntentPersisted(42, "stop-1")).state
        val failed = reduce(durable, EngineEvent.SessionFinalizeFailed(42, "stop-1", "DISK_IO"))
        assertEquals(RecordingState.FinalizeFailed(42L, "stop-1", StopReason.USER, "DISK_IO"),
            failed.state.recording)
        assertTrue(reduce(failed.state, start("retry-as-new")).effects.isEmpty())
        val retried = reduce(failed.state, EngineEvent.Command(
            EngineCommand.RetryFinalization("retry-finish", CommandOrigin.APP)))
        assertEquals(listOf(EngineEffect.FinalizeSession(42, "stop-1", StopReason.USER)),
            retried.effects)
    }

    @Test fun unsuccessfulOverlayRemovalCannotTriggerDuplicateAttach() {
        val s = base().copy(wantsOverlay = true, hasOverlayPermission = true,
            overlay = OverlayAttachment.Attached)
        val remove = reduce(s, EngineEvent.OverlayPermissionChanged(false)).state
        val id = (remove.overlay as OverlayAttachment.Detaching).operationId
        val failed = reduce(remove, EngineEvent.OverlayDetachFailed(id, "REMOVE_VIEW_EXCEPTION")).state
        assertTrue(failed.overlay is OverlayAttachment.CleanupRequired)
        val retriedShow = reduce(failed, EngineEvent.Command(EngineCommand.SetOverlayVisible(
            "show-again", CommandOrigin.APP, true)))
        assertTrue(retriedShow.effects.isEmpty()) // never add a second view
        val retryRemove = reduce(retriedShow.state, EngineEvent.Command(EngineCommand.SetOverlayVisible(
            "hide-again", CommandOrigin.APP, false)))
        assertEquals(EngineEffect.DetachOverlay(id), retryRemove.effects.single())
        val confirmed = reduce(retryRemove.state, EngineEvent.OverlayDetached(id))
        assertEquals(OverlayAttachment.Detached, confirmed.state.overlay)
    }

    @Test fun recoveryRetainsPersistedProfileMetadata() {
        val resumed = reduce(base(), EngineEvent.Reconcile(42L, 500L, 99L, leaseEligible = true))
        assertEquals(RecordingState.Recovering(42L, 500L, 99L), resumed.state.recording)
        val confirmed = reduce(resumed.state, EngineEvent.SessionResumed(42L))
        assertEquals(99L, (confirmed.state.recording as RecordingState.Recording).profileId)
    }

    @Test fun commandHistoryIsBounded() {
        var s = base()
        repeat(80) { index ->
            s = reduce(s, EngineEvent.Command(EngineCommand.SetHudPresentation(
                "layout-$index", CommandOrigin.APP, HudPresentation.COMPACT))).state
        }
        assertEquals(32, s.recentCommandIds.size)
        assertEquals("layout-48", s.recentCommandIds.first())
    }
}
