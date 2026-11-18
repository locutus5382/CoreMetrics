package com.locutus5382.coremetrics.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class HeimdallNativeRecoveryPolicyTest {
    private fun eligible() = NativeRecoveryEvidence(
        rawOwner = "HEIMDALL_DEBUG",
        leaseEligible = true,
        originalSessionId = 73L,
        recordingLease = true,
        persistedStop = false,
        originalRowStatus = "RECORDING",
        intervalMs = 2_000L,
        userRequestedStop = false,
    )
    private fun decision(e: NativeRecoveryEvidence) =
        HeimdallNativeRecoveryPolicy.decide(e)

    @Test fun eligibleNativeLeaseResumesOriginalId() {
        assertEquals(NativeRecoveryDecision.RESUME_ORIGINAL, decision(eligible()))
    }

    @Test fun terminalStopNeverRestartsSampler() {
        assertEquals(NativeRecoveryDecision.FINALIZE_PERSISTED_STOP,
            decision(eligible().copy(persistedStop = true)))
    }

    @Test fun incomingColdStopMustBePersistedBeforeFinalization() {
        assertEquals(NativeRecoveryDecision.PERSIST_STOP_AND_FINALIZE,
            decision(eligible().copy(userRequestedStop = true)))
    }

    @Test fun alreadyPersistedStopTakesPriorityOverRepeatedColdStop() {
        assertEquals(NativeRecoveryDecision.FINALIZE_PERSISTED_STOP,
            decision(eligible().copy(persistedStop = true,
                userRequestedStop = true)))
    }

    @Test fun completedRowWithUnclearedNativeLeaseOnlyReleasesLease() {
        assertEquals(NativeRecoveryDecision.RELEASE_ALREADY_FINALIZED_LEASE,
            decision(eligible().copy(originalRowStatus = "COMPLETED")))
    }

    @Test fun failedRowWithUnclearedNativeLeaseOnlyReleasesLease() {
        assertEquals(NativeRecoveryDecision.RELEASE_ALREADY_FINALIZED_LEASE,
            decision(eligible().copy(originalRowStatus = "FAILED")))
    }

    @Test fun interruptedStoppedRowCannotResume() {
        assertEquals(NativeRecoveryDecision.RELEASE_ALREADY_FINALIZED_LEASE,
            decision(eligible().copy(originalRowStatus = "INTERRUPTED_STOPPED")))
    }

    @Test fun legacyLeaseIsNeverAssignedToHeimdall() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(rawOwner = "LEGACY")))
    }

    @Test fun unknownLeaseOwnerIsQuarantined() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(rawOwner = "UNKNOWN")))
    }

    @Test fun staleLeaseCannotResumeOriginalSession() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(leaseEligible = false)))
    }

    @Test fun missingSqliteRowCannotRecreateSession() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(originalRowStatus = null)))
    }

    @Test fun zeroIdCannotCreateReplacementSession() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(originalSessionId = 0L)))
    }

    @Test fun nonRecordingStandbyLeaseCannotResume() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(recordingLease = false)))
    }

    @Test fun malformedIntervalCannotChangeSamplingCadence() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(intervalMs = 1_000L)))
    }

    @Test fun normalFiveSecondCadencePreserved() {
        assertEquals(NativeRecoveryDecision.RESUME_ORIGINAL,
            decision(eligible().copy(intervalMs = 5_000L)))
    }

    @Test fun unknownSqliteStatusMustNotBeFinalized() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(originalRowStatus = "UNKNOWN")))
    }

    @Test fun repeatedColdStopCannotTurnExpiredLeaseIntoResume() {
        assertEquals(NativeRecoveryDecision.QUARANTINE,
            decision(eligible().copy(leaseEligible = false, userRequestedStop = true)))
    }
}
