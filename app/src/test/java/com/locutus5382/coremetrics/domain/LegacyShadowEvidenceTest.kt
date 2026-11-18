package com.locutus5382.coremetrics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyShadowEvidenceTest {
    private fun fields(
        serviceAlive: Boolean = false, pendingRecovery: Boolean = false,
        recoveringRecording: Boolean = false, recoverySessionId: Long = 0L,
        running: Boolean = false, starting: Boolean = false, standby: Boolean = false,
        sessionId: Long = 0L, sampleCount: Long = 0L, lastSample: Long = 0L,
        visible: Boolean = false,
    ) = LegacyStatusFields(serviceAlive, pendingRecovery, recoveringRecording,
        recoverySessionId, running, starting, standby, sessionId, sampleCount, lastSample, visible)

    private fun observed(f: LegacyStatusFields, now: Long = 200_000L) =
        LegacyStatusPolicy.interpret(f, now)

    @Test fun providerOnlyDoesNotClaimRecorderAlive() {
        val o = observed(fields())
        assertEquals(LegacyPhase.PROVIDER_ONLY_IDLE, o.phase)
        assertFalse(o.liveRecording)
        assertNull(o.sessionId)
    }

    @Test fun coldRecordingLeaseIsCandidateNotLiveRecording() {
        val o = observed(fields(pendingRecovery = true, recoveringRecording = true,
            recoverySessionId = 42))
        assertEquals(LegacyPhase.RECOVERY_RECORDING_CANDIDATE, o.phase)
        assertEquals(42L, o.sessionId)
        assertFalse(o.liveRecording)
        assertFalse(o.sampleFresh)
    }

    @Test fun standbyRecoveryDoesNotClaimActiveRecording() {
        val o = observed(fields(pendingRecovery = true))
        assertEquals(LegacyPhase.RECOVERY_STANDBY_CANDIDATE, o.phase)
        assertNull(o.sessionId)
    }

    @Test fun badColdRecordingIdIsInconsistent() {
        assertEquals(LegacyPhase.INCONSISTENT,
            observed(fields(pendingRecovery = true, recoveringRecording = true)).phase)
    }

    @Test fun liveRecordingMustHaveServiceAndValidSession() {
        val o = observed(fields(serviceAlive = true, running = true, sessionId = 42,
            sampleCount = 4, lastSample = 195_000L, visible = true))
        assertEquals(LegacyPhase.LIVE_RECORDING, o.phase)
        assertTrue(o.liveRecording)
        assertEquals(42L, o.sessionId)
        assertEquals(4L, o.recordedSampleCount)
        assertEquals(5_000L, o.sampleAgeMs)
        assertTrue(o.sampleFresh)
        assertTrue(o.reportedHudVisible)
    }

    @Test fun staleLiveReadingIsNeverLabeledFresh() {
        val o = observed(fields(serviceAlive = true, running = true, sessionId = 42,
            lastSample = 180_000L))
        assertEquals(LegacyPhase.LIVE_RECORDING, o.phase)
        assertFalse(o.sampleFresh)
        assertEquals(20_000L, o.sampleAgeMs)
    }

    @Test fun missingReadingIsNotReplacedByFakeZero() {
        val o = observed(fields(serviceAlive = true, running = true, sessionId = 42))
        assertNull(o.sampleAgeMs)
        assertFalse(o.sampleFresh)
    }

    @Test fun futureWallClockTimestampIsUntrusted() {
        val o = observed(fields(serviceAlive = true, running = true,
            sessionId = 42, lastSample = 250_000L))
        assertNull(o.sampleAgeMs)
        assertFalse(o.sampleFresh)
    }

    @Test fun conflictingServiceStatesAreInconsistent() {
        assertEquals(LegacyPhase.INCONSISTENT,
            observed(fields(serviceAlive = true, running = true, starting = true, sessionId = 5)).phase)
        assertEquals(LegacyPhase.INCONSISTENT,
            observed(fields(serviceAlive = true, running = true, standby = true, sessionId = 5)).phase)
        assertEquals(LegacyPhase.INCONSISTENT,
            observed(fields(serviceAlive = false, running = true, sessionId = 5)).phase)
        assertEquals(LegacyPhase.INCONSISTENT,
            observed(fields(serviceAlive = true, pendingRecovery = true)).phase)
    }

    @Test fun startingAndStandbyAreNotLiveRecordings() {
        assertEquals(LegacyPhase.LIVE_STARTING,
            observed(fields(serviceAlive = true, starting = true)).phase)
        val idle = observed(fields(serviceAlive = true, standby = true, visible = true))
        assertEquals(LegacyPhase.LIVE_STANDBY, idle.phase)
        assertTrue(idle.reportedHudVisible)
        assertFalse(idle.sampleFresh)
        assertFalse(idle.liveRecording)
    }

    @Test fun inactiveServiceReportsLiveIdleButNotRecording() {
        assertEquals(LegacyPhase.LIVE_IDLE,
            observed(fields(serviceAlive = true)).phase)
    }

    @Test fun noValidLeaseDoesNotProveNoOpenDatabaseRows() {
        val r = LegacyRecoveryPolicy.interpret(null, null)
        assertEquals(LegacyRecoveryKind.NO_VALID_LEASE, r.kind)
        assertFalse(r.requiresServiceReconciliation)
    }

    @Test fun standbyLeaseIsNotRecordingResume() {
        val r = LegacyRecoveryPolicy.interpret(LegacyLeaseFields(false, 0, false), null)
        assertEquals(LegacyRecoveryKind.STANDBY_ONLY, r.kind)
        assertNull(r.sessionId)
    }

    @Test fun openRecordingAndStopHaveDistinctCandidates() {
        val resume = LegacyRecoveryPolicy.interpret(LegacyLeaseFields(true, 10L, false), "RECORDING")
        val stop = LegacyRecoveryPolicy.interpret(LegacyLeaseFields(true, 10L, true), "RECORDING")
        assertEquals(LegacyRecoveryKind.OPEN_RECORDING_CANDIDATE, resume.kind)
        assertEquals(LegacyRecoveryKind.PERSISTED_STOP_CANDIDATE, stop.kind)
        assertEquals(10L, resume.sessionId)
        assertEquals(10L, stop.sessionId)
        assertTrue(resume.requiresServiceReconciliation)
        assertTrue(stop.requiresServiceReconciliation)
    }

    @Test fun mismatchedDatabaseStatusPreventsBothCandidatePaths() {
        val saved = LegacyLeaseFields(true, 10L, true)
        for (status in listOf(null, "COMPLETED", "INTERRUPTED_STOPPED", "FAILED")) {
            val o = LegacyRecoveryPolicy.interpret(saved, status)
            assertEquals(LegacyRecoveryKind.DATABASE_ROW_NOT_OPEN, o.kind)
            assertFalse(o.requiresServiceReconciliation)
        }
    }

    @Test fun invalidSessionIdCannotGenerateResumeCandidate() {
        val r = LegacyRecoveryPolicy.interpret(LegacyLeaseFields(true, 0L, false), "RECORDING")
        assertEquals(LegacyRecoveryKind.DATABASE_ROW_NOT_OPEN, r.kind)
        assertNull(r.sessionId)
    }
}
