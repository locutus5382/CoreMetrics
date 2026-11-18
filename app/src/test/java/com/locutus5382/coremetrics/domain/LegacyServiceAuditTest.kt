package com.locutus5382.coremetrics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyServiceAuditTest {
    private fun initial() = LegacyServiceAudit.begin("monitor-epoch-1")
    private fun active() = LegacyServiceAudit.observe(initial(), LegacyAuditObservation.SessionAcquired(42))
    private fun step(s: LegacyAuditSnapshot, e: LegacyAuditObservation) = LegacyServiceAudit.observe(s, e)

    @Test fun sessionAcquisitionIsObservableNotCommandable() {
        val s = active()
        assertEquals(42L, s.sessionId)
        assertEquals(LegacyAuditStop.NONE, s.stop)
        assertFalse(s.samplerExited)
    }
    @Test fun duplicateAcquisitionIsIdempotent() {
        val s = active()
        assertEquals(s, step(s, LegacyAuditObservation.SessionAcquired(42)))
    }
    @Test fun rejectsInvalidSessionIdWithoutCreatingRows() {
        val s = initial()
        assertEquals(s, step(s, LegacyAuditObservation.SessionAcquired(0)))
    }
    @Test fun stopIntentionCannotBeFakedIntoDurability() {
        val s = step(active(), LegacyAuditObservation.TerminalIntent(42, false))
        assertEquals(LegacyAuditStop.FAILED, s.stop)
        assertTrue(LegacyAuditFinding.STOP_PERSISTENCE_FAILED in s.findings)
    }
    @Test fun durableStopOnlyAfterActualCommit() {
        val s = step(active(), LegacyAuditObservation.TerminalIntent(42, true))
        assertEquals(LegacyAuditStop.PERSISTED, s.stop)
    }
    @Test fun samplerExitIsSeparateFromStopIntent() {
        val s = step(active(), LegacyAuditObservation.SamplerExited(42))
        assertTrue(s.samplerExited)
        assertEquals(LegacyAuditStop.NONE, s.stop)
    }
    @Test fun orderedStopAndFinishHaveNoFindings() {
        var s = active()
        s = step(s, LegacyAuditObservation.TerminalIntent(42, true))
        s = step(s, LegacyAuditObservation.SamplerExited(42))
        s = step(s, LegacyAuditObservation.Finalized(42, true))
        assertEquals(LegacyAuditFinish.CONFIRMED, s.finish)
        assertTrue(s.findings.isEmpty())
    }
    @Test fun unexpectedFinalizationWithoutUserStopIsNotAutomaticallyWrong() {
        var s = active()
        s = step(s, LegacyAuditObservation.SamplerExited(42))
        s = step(s, LegacyAuditObservation.Finalized(42, true))
        assertEquals(LegacyAuditFinish.CONFIRMED, s.finish)
        assertTrue(s.findings.isEmpty())
    }
    @Test fun finalizationBeforeLoopExitIsFlagged() {
        val s = step(active(), LegacyAuditObservation.Finalized(42, true))
        assertTrue(LegacyAuditFinding.FINALIZED_BEFORE_SAMPLER_EXIT in s.findings)
    }
    @Test fun databaseFinishFailureIsRecorded() {
        var s = step(active(), LegacyAuditObservation.SamplerExited(42))
        s = step(s, LegacyAuditObservation.Finalized(42, false))
        assertEquals(LegacyAuditFinish.FAILED, s.finish)
        assertTrue(LegacyAuditFinding.FINALIZATION_FAILED in s.findings)
    }
    @Test fun wrongIdNeverMutatesActualSession() {
        val s = step(active(), LegacyAuditObservation.TerminalIntent(43, true))
        assertEquals(42L, s.sessionId)
        assertEquals(LegacyAuditStop.NONE, s.stop)
        assertTrue(LegacyAuditFinding.WRONG_SESSION_EVENT in s.findings)
    }
    @Test fun overlappingNewSessionIsFlagged() {
        val s = step(active(), LegacyAuditObservation.SessionAcquired(43))
        assertTrue(LegacyAuditFinding.OVERLAPPING_SESSIONS in s.findings)
    }
    @Test fun completedSessionMayBeFollowedByNewSession() {
        var s = step(active(), LegacyAuditObservation.SamplerExited(42))
        s = step(s, LegacyAuditObservation.Finalized(42, true))
        s = step(s, LegacyAuditObservation.SessionAcquired(43))
        assertEquals(43L, s.sessionId)
        assertTrue(s.findings.isEmpty())
    }
    @Test fun destroyedServiceCannotAcceptFurtherEvents() {
        val s = step(active(), LegacyAuditObservation.ServiceDestroyed)
        assertTrue(s.serviceDestroyed)
        assertEquals(s, step(s, LegacyAuditObservation.SessionAcquired(99)))
    }
    @Test fun sequenceOnlyIncreasesOnChangedState() {
        val s = initial()
        assertEquals(0L, s.sequence)
        val a = step(s, LegacyAuditObservation.SessionAcquired(42))
        assertEquals(1L, a.sequence)
        assertEquals(a, step(a, LegacyAuditObservation.SessionAcquired(42)))
    }
    @Test fun requiresExplicitNonEmptyEpoch() {
        try { LegacyServiceAudit.begin(""); throw AssertionError("Expected empty epoch rejection") }
        catch (e: IllegalArgumentException) { assertTrue(e.message != null) }
    }
}
