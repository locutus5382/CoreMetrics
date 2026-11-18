package com.locutus5382.coremetrics.trial

import org.junit.Assert.*
import org.junit.Test

class HeimdallTrialAdmissionTest {
    private fun clean() = HeimdallTrialAdmissionEvidence(
        monitorStatusVerified = true,
        rawOwner = null,
        unfinishedRow = false,
        rawLeaseUnresolved = false,
        repairNeeded = false,
        running = false,
        starting = false,
        standby = false,
        saving = false,
        hudAttached = false,
        phase = null,
    )

    @Test fun verifiedIdleTrialMayRequestButCannotAuthorizeStart() {
        assertTrue(HeimdallTrialAdmission.mayRequestStart(clean()))
        assertFalse(HeimdallTrialAdmission.mayRequestStop(clean()))
    }
    @Test fun missingMonitorStatusMustNotAppearIdle() {
        assertFalse(HeimdallTrialAdmission.mayRequestStart(
            clean().copy(monitorStatusVerified = false)))
    }
    @Test fun rawOwnerBlocksNewSessionEvenWhenNoSamplerRuns() {
        for (owner in listOf("LEGACY", "HEIMDALL_DEBUG", "UNKNOWN")) {
            assertFalse(HeimdallTrialAdmission.mayRequestStart(
                clean().copy(rawOwner = owner)))
        }
    }
    @Test fun expiredOrUnreadableLeaseBlocksNativeStart() {
        assertFalse(HeimdallTrialAdmission.mayRequestStart(
            clean().copy(rawLeaseUnresolved = true)))
    }
    @Test fun unfinishedSqliteRowBlocksStartWithoutClearingData() {
        assertFalse(HeimdallTrialAdmission.mayRequestStart(
            clean().copy(unfinishedRow = true)))
    }
    @Test fun activeLegacyWorkAndOverlayAreIndependentBlockers() {
        val e = clean()
        for (conflict in listOf(e.copy(running = true),e.copy(starting = true),
            e.copy(standby = true),e.copy(saving = true),
            e.copy(hudAttached = true),e.copy(repairNeeded = true))) {
            assertFalse(HeimdallTrialAdmission.mayRequestStart(conflict))
        }
    }
    @Test fun statusMustBeKnownIdleRatherThanUnknownFuturePhase() {
        assertTrue(HeimdallTrialAdmission.mayRequestStart(
            clean().copy(phase = "IDLE")))
        for (phase in listOf("RECOVERING", "STOPPING", "RETRY_STOP",
            "FINALIZING", "RETRY_SAVE", "NEEDS_REPAIR", "UNKNOWN_FUTURE")) {
            assertFalse(HeimdallTrialAdmission.mayRequestStart(
                clean().copy(phase = phase)))
        }
    }
    @Test fun nativeOwnerCanRequestEmergencyStopEvenAfterServiceDeath() {
        assertTrue(HeimdallTrialAdmission.mayRequestStop(
            clean().copy(rawOwner = "HEIMDALL_DEBUG")))
    }
    @Test fun stopAndRetryPhasesAreRoutedToOriginalOwner() {
        for (phase in listOf("RECORDING", "STOPPING", "RETRY_STOP",
            "FINALIZING", "RETRY_SAVE")) {
            assertTrue(HeimdallTrialAdmission.mayRequestStop(
                clean().copy(phase = phase)))
        }
    }
    @Test fun monitorReadFailureDisablesBothButtons() {
        val evidence = clean().copy(
            rawOwner = "HEIMDALL_DEBUG", monitorStatusVerified = false)
        assertFalse(HeimdallTrialAdmission.mayRequestStart(evidence))
        assertFalse(HeimdallTrialAdmission.mayRequestStop(evidence))
    }
    @Test fun repairRequiredCannotStartButCanRequestStopOfExistingOwner() {
        val e = clean().copy(rawOwner = "HEIMDALL_DEBUG",
            repairNeeded = true, phase = "NEEDS_REPAIR")
        assertFalse(HeimdallTrialAdmission.mayRequestStart(e))
        assertTrue(HeimdallTrialAdmission.mayRequestStop(e))
    }
}
