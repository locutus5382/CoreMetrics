package com.locutus5382.coremetrics.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class HeimdallCutoverPolicyTest {
    private fun approved() = HeimdallCutoverEvidence(
        nativeDefaultCompiled = true,
        odinDeviceValidated = true,
        allCriticalCiPassed = true,
        userApprovedDefault = true,
        priorSessionsBackedUp = true,
        rollbackBuildVerified = true,
        legacyRecorderCurrentlyActive = false,
        legacyLeaseStillPresent = false,
        unresolvedHeimdallLease = false,
        unfinishedSqliteSession = false,
        overlayStillAttached = false,
    )
    private fun decision(e: HeimdallCutoverEvidence) =
        HeimdallCutoverPolicy.decide(e)

    @Test fun featureGateDefaultsToOldRecorder() {
        assertEquals(HeimdallCutoverDecision.LEGACY_DEFAULT,
            decision(approved().copy(nativeDefaultCompiled = false)))
    }
    @Test fun allApprovalAndSafetyGatesYieldEligibilityOnly() {
        assertEquals(HeimdallCutoverDecision.HEIMDALL_DEFAULT_ELIGIBLE,
            decision(approved()))
    }
    @Test fun noOdinTestMeansNoCutover() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_TESTS,
            decision(approved().copy(odinDeviceValidated = false)))
    }
    @Test fun incompleteCiMeansNoCutover() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_TESTS,
            decision(approved().copy(allCriticalCiPassed = false)))
    }
    @Test fun userApprovalIsIndependentlyRequired() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_TESTS,
            decision(approved().copy(userApprovedDefault = false)))
    }
    @Test fun previousSessionsMustBeBackedUpFirst() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_TESTS,
            decision(approved().copy(priorSessionsBackedUp = false)))
    }
    @Test fun rollbackApkMustBeTested() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_TESTS,
            decision(approved().copy(rollbackBuildVerified = false)))
    }
    @Test fun existingLegacyRecorderCannotLoseOwnership() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(approved().copy(legacyRecorderCurrentlyActive = true)))
    }
    @Test fun legacyLeaseCannotBeClaimedByNativeStart() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(approved().copy(legacyLeaseStillPresent = true)))
    }
    @Test fun nativeLeaseCannotBeReplacedByAnotherSession() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(approved().copy(unresolvedHeimdallLease = true)))
    }
    @Test fun unfinishedSqliteRowCannotBeReplaced() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(approved().copy(unfinishedSqliteSession = true)))
    }
    @Test fun overlayMustBeDetachedBeforeTransfer() {
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(approved().copy(overlayStillAttached = true)))
    }
    @Test fun activeSessionTakesPrecedenceOverNominalValidation() {
        val e = approved().copy(
            odinDeviceValidated = false,
            legacyRecorderCurrentlyActive = true)
        assertEquals(HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY,
            decision(e))
    }
}
