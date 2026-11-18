package com.locutus5382.coremetrics.trial

import org.junit.Assert.*
import org.junit.Test

class HeimdallTrialSavedSessionTest {
    private fun baseline() = TrialSavedSessionEvidence(
        statusVerified = true, enginePhase = "RECORDING",
        rawOwner = "HEIMDALL_DEBUG", originalSessionId = 73L,
        rowStatus = "RECORDING", endedAtMs = null,
        persistedSamples = 4L, totalSessionRows = 6L,
        unfinishedRowExists = true, rawLeaseUnresolved = false,
        repairNeeded = false,
    )

    private fun completed() = baseline().copy(
        enginePhase = null, rawOwner = null,
        rowStatus = "COMPLETED", endedAtMs = 1_700_000L,
        persistedSamples = 7L, unfinishedRowExists = false,
    )

    private fun evaluate(
        b: TrialSavedSessionEvidence = baseline(),
        a: TrialSavedSessionEvidence = completed(),
    ) = HeimdallTrialSavedSession.evaluate(b, a)

    @Test fun originalCompletedRowPreservesSamplesAndClearsLease() {
        val result = evaluate()
        assertTrue(result.passed)
        assertEquals(TrialSavedSessionResult.VERIFIED_SAVED,result.result)
        assertEquals(73L,result.originalSessionId)
        assertEquals(7L,result.preservedSamples)
    }

    @Test fun baselineMustBeVerifiedNativeOwnerWithOriginalRecordingRow() {
        for (candidate in listOf(
            baseline().copy(statusVerified = false),
            baseline().copy(rawOwner = "LEGACY"),
            baseline().copy(rawOwner = null),
            baseline().copy(originalSessionId = 0L),
            baseline().copy(rowStatus = "COMPLETED"),
            baseline().copy(rawLeaseUnresolved = true),
            baseline().copy(repairNeeded = true),
            baseline().copy(totalSessionRows = 0L),
            baseline().copy(enginePhase = "IDLE"),
        )) {
            assertEquals(TrialSavedSessionResult.BASELINE_UNVERIFIED,
                evaluate(b = candidate).result)
        }
    }

    @Test fun monitorUnavailabilityAfterStopCannotProducePass() {
        assertEquals(TrialSavedSessionResult.STATUS_UNAVAILABLE,
            evaluate(a = completed().copy(statusVerified = false)).result)
    }

    @Test fun aReplacementSessionCannotPassOriginalSessionProof() {
        assertEquals(TrialSavedSessionResult.ORIGINAL_SESSION_CHANGED,
            evaluate(a = completed().copy(originalSessionId = 74L)).result)
    }

    @Test fun extraRowIsDetectedEvenIfOriginalRowCompleted() {
        assertEquals(TrialSavedSessionResult.EXTRA_SESSION_ROW,
            evaluate(a = completed().copy(totalSessionRows = 7L)).result)
    }

    @Test fun failedInterruptedOrUnfinishedRowNeverPasses() {
        for (status in listOf("RECORDING", "FAILED", "INTERRUPTED", null)) {
            assertEquals(TrialSavedSessionResult.ROW_NOT_COMPLETED,
                evaluate(a = completed().copy(rowStatus = status)).result)
        }
    }

    @Test fun completedRowWithoutPersistedEndTimeNeverPasses() {
        assertEquals(TrialSavedSessionResult.MISSING_END_TIME,
            evaluate(a = completed().copy(endedAtMs = null)).result)
    }

    @Test fun removingAnySavedSampleIsDetected() {
        assertEquals(TrialSavedSessionResult.SAMPLE_COUNT_DECREASED,
            evaluate(a = completed().copy(persistedSamples = 3L)).result)
    }

    @Test fun emptySessionIsNotAValidatedRecording() {
        assertEquals(TrialSavedSessionResult.NO_SAVED_SAMPLES,
            evaluate(baseline().copy(persistedSamples = 0L),
                completed().copy(persistedSamples = 0L)).result)
    }

    @Test fun rawOrUnresolvedLeaseCannotBeConsideredCleared() {
        assertEquals(TrialSavedSessionResult.LEASE_STILL_ACTIVE,
            evaluate(a = completed().copy(rawOwner = "HEIMDALL_DEBUG")).result)
        assertEquals(TrialSavedSessionResult.LEASE_STILL_ACTIVE,
            evaluate(a = completed().copy(rawLeaseUnresolved = true)).result)
        assertEquals(TrialSavedSessionResult.LEASE_STILL_ACTIVE,
            evaluate(a = completed().copy(repairNeeded = true)).result)
    }

    @Test fun anotherUnfinishedSessionBlocksSuccess() {
        assertEquals(TrialSavedSessionResult.UNFINISHED_SESSION_REMAINS,
            evaluate(a = completed().copy(unfinishedRowExists = true)).result)
    }

    @Test fun liveEnginePhaseDoesNotBecomeSavedJustBecauseRowFinished() {
        for (phase in listOf("RECORDING", "STOPPING", "RETRY_SAVE",
            "FINALIZING", "NEEDS_REPAIR")) {
            assertEquals(TrialSavedSessionResult.ENGINE_STILL_ACTIVE,
                evaluate(a = completed().copy(enginePhase = phase)).result)
        }
    }

    @Test fun retainedSampleCountCanGrowBetweenStopAndFinalization() {
        val result=evaluate(a = completed().copy(persistedSamples = 8L))
        assertTrue(result.passed)
        assertEquals(8L,result.preservedSamples)
    }
}
