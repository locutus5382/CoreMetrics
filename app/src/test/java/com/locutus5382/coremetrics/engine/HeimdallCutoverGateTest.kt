package com.locutus5382.coremetrics.engine

import org.junit.Assert.*
import org.junit.Test

class HeimdallCutoverGateTest {
    private fun verified() = CutoverGateEvidence(
        realDeviceValidated = true,
        processDeathAndStopValidated = true,
        sessionAndExportRegressionPassed = true,
        fallbackRollbackVerified = true,
    )

    @Test fun unvalidatedDefaultRequestUsesLegacy() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT, CutoverGateEvidence())
        assertEquals(CutoverRoute.LEGACY, choice.route)
        assertEquals(4, choice.blockers.size)
    }

    @Test fun nativeRouteRequiresAllFourIndependentSignoffs() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT, verified())
        assertEquals(CutoverRoute.HEIMDALL_NATIVE, choice.route)
        assertTrue(choice.blockers.isEmpty())
    }

    @Test fun missingDeviceTestForcesLegacy() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(realDeviceValidated = false))
        assertEquals(CutoverRoute.LEGACY, choice.route)
        assertEquals(setOf(CutoverBlocker.DEVICE_VALIDATION_MISSING),choice.blockers)
    }

    @Test fun missingCrashAndStopEvidenceForcesLegacy() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(processDeathAndStopValidated = false))
        assertEquals(CutoverRoute.LEGACY, choice.route)
        assertTrue(CutoverBlocker.RECOVERY_VALIDATION_MISSING in choice.blockers)
    }

    @Test fun missingExportsOrReportRegressionForcesLegacy() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(sessionAndExportRegressionPassed = false))
        assertEquals(CutoverRoute.LEGACY,choice.route)
        assertTrue(CutoverBlocker.EXPORT_REGRESSIONS_MISSING in choice.blockers)
    }

    @Test fun noRollbackEvidenceForcesLegacy() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(fallbackRollbackVerified = false))
        assertEquals(CutoverRoute.LEGACY,choice.route)
        assertTrue(CutoverBlocker.ROLLBACK_NOT_VERIFIED in choice.blockers)
    }

    @Test fun existingLegacyTaskBlocksEvenDefaultLegacyRequest() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.LEGACY,
            verified().copy(legacyTaskActive = true))
        assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
        assertEquals(setOf(CutoverBlocker.EXISTING_LEGACY_RECORDING),choice.blockers)
    }

    @Test fun rawActiveLeaseBlocksEveryRequestedMode() {
        for (mode in CutoverRequestedMode.entries) {
            val choice = HeimdallCutoverGate.decide(
                mode,verified().copy(unresolvedLease = true))
            assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
            assertTrue(CutoverBlocker.UNRESOLVED_LEASE in choice.blockers)
        }
    }

    @Test fun unfinishedSqliteRowBlocksNativeCutover() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(unfinishedSqliteSession = true))
        assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
    }

    @Test fun oldOverlayMustBeRemovedBeforeTransfer() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(legacyOverlayAttached = true))
        assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
    }

    @Test fun anotherLiveOwnerCannotBePreempted() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(differentOwnerActive = true))
        assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
    }

    @Test fun shadowDoesNotSelectNativeSampler() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_SHADOW, CutoverGateEvidence())
        assertEquals(CutoverRoute.HEIMDALL_SHADOW,choice.route)
    }

    @Test fun legacyRequestedWithoutConflictsRemainsLegacy() {
        assertEquals(CutoverRoute.LEGACY,
            HeimdallCutoverGate.decide(
                CutoverRequestedMode.LEGACY,CutoverGateEvidence()).route)
    }

    @Test fun combinedOwnerConflictsReportedWithoutGuessing() {
        val choice = HeimdallCutoverGate.decide(
            CutoverRequestedMode.HEIMDALL_DEFAULT,
            verified().copy(unresolvedLease = true,unfinishedSqliteSession = true))
        assertEquals(CutoverRoute.REFUSE_AND_REPAIR,choice.route)
        assertEquals(2,choice.blockers.size)
    }
}
