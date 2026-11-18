package com.locutus5382.coremetrics.trial

import org.junit.Assert.*
import org.junit.Test

class HeimdallTrialFinalizationProofTest {
    private fun completed() = TrialFinalizationEvidence(
        statusVerified = true,
        originalSessionId = 73L,
        sqliteStatus = "COMPLETED",
        persistedSamples = 8L,
        rawRecoveryOwner = null,
        rawLeaseUnresolved = false,
        unfinishedRecordingExists = false,
        monitorRecording = false,
        monitorStarting = false,
        monitorSaving = false,
        enginePhase = null,
    )

    @Test fun originalSessionWithSamplesAndNoLeaseIsVerified() {
        val s = HeimdallTrialFinalizationProof.evaluate(completed())
        assertTrue(s.passed)
        assertEquals(TrialFinalizationResult.VERIFIED_COMPLETION, s.result)
        assertEquals(73L, s.sessionId)
        assertEquals(8L, s.samples)
    }
    @Test fun monitorStatusMustBeAvailable() {
        assertEquals(TrialFinalizationResult.MONITOR_UNAVAILABLE,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(statusVerified = false)).result)
    }
    @Test fun unknownOrZeroOriginalIdMustNeverPass() {
        assertEquals(TrialFinalizationResult.ORIGINAL_ID_MISSING,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(originalSessionId = 0L)).result)
    }
    @Test fun missingDatabaseRowMustNotBeClaimedSaved() {
        assertEquals(TrialFinalizationResult.ORIGINAL_ROW_MISSING,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(sqliteStatus = null)).result)
    }
    @Test fun unfinishedOrFailedRowsAreNotCompleted() {
        for (s in listOf("RECORDING", "FAILED", "INTERRUPTED", "UNKNOWN")) {
            assertEquals(TrialFinalizationResult.ORIGINAL_NOT_COMPLETED,
                HeimdallTrialFinalizationProof.evaluate(
                    completed().copy(sqliteStatus = s)).result)
        }
    }
    @Test fun noSamplesCannotProveUsefulTestRecording() {
        assertEquals(TrialFinalizationResult.SAMPLES_MISSING,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(persistedSamples = 0L)).result)
    }
    @Test fun leaseRetainedAfterSqliteCompletionIsNotFullyFinalized() {
        for (owner in listOf("HEIMDALL_DEBUG", "LEGACY", "UNKNOWN")) {
            assertEquals(TrialFinalizationResult.RECOVERY_LEASE_UNRESOLVED,
                HeimdallTrialFinalizationProof.evaluate(
                    completed().copy(rawRecoveryOwner = owner)).result)
        }
        assertEquals(TrialFinalizationResult.RECOVERY_LEASE_UNRESOLVED,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(rawLeaseUnresolved = true)).result)
    }
    @Test fun anotherUnfinishedRowBlocksCompletionProof() {
        assertEquals(TrialFinalizationResult.UNFINISHED_RECORDING_REMAINS,
            HeimdallTrialFinalizationProof.evaluate(
                completed().copy(unfinishedRecordingExists = true)).result)
    }
    @Test fun activeMonitorFlagsCannotBeMistakenForStopped() {
        for (e in listOf(
            completed().copy(monitorRecording = true),
            completed().copy(monitorStarting = true),
            completed().copy(monitorSaving = true),
        )) {
            assertEquals(TrialFinalizationResult.MONITOR_STILL_ACTIVE,
                HeimdallTrialFinalizationProof.evaluate(e).result)
        }
    }
    @Test fun nativePendingPhasesCannotPassDespiteCompletedRow() {
        for (phase in listOf("RECORDING", "STARTING", "RECOVERING",
            "STOPPING", "RETRY_STOP", "FINALIZING", "RETRY_SAVE")) {
            assertEquals(TrialFinalizationResult.MONITOR_STILL_ACTIVE,
                HeimdallTrialFinalizationProof.evaluate(
                    completed().copy(enginePhase = phase)).result)
        }
    }
    @Test fun idleOrAbsentPhaseCanBeVerifiedFromDurableRowAndLease() {
        assertTrue(HeimdallTrialFinalizationProof.evaluate(
            completed().copy(enginePhase = "IDLE")).passed)
    }
}
