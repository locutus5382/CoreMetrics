package com.locutus5382.coremetrics.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeimdallDebugOwnershipPolicyTest {
    private fun good() = DebugEngineAdmissionEvidence(
        debugBuild=true, explicitDebugStart=true, notificationsAllowed=true,
        legacyTaskPresent=false, legacyStartingOrRunning=false,
        legacyStandbyArmed=false, legacySaving=false,
        legacyOverlayAttached=false, unresolvedRawLease=false,
        unfinishedSqliteSession=false, debugSlotOccupied=false,
    )
    @Test fun explicitDebugAndCleanLegacyStatePermitsHandoff() {
        assertNull(HeimdallDebugOwnershipPolicy.denial(good()))
    }
    @Test fun releaseBuildCannotActivateDebugEngine() {
        assertEquals(DebugEngineDenial.NOT_DEBUG_BUILD,
            HeimdallDebugOwnershipPolicy.denial(good().copy(debugBuild=false)))
    }
    @Test fun normalAppOrQuickSettingsStartIsNeverDebugStart() {
        assertEquals(DebugEngineDenial.NO_EXPLICIT_REQUEST,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(explicitDebugStart=false)))
    }
    @Test fun deniedNotificationPreventsForegroundlessStart() {
        assertEquals(DebugEngineDenial.NOTIFICATIONS_DENIED,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(notificationsAllowed=false)))
    }
    @Test fun existingLegacySamplerCannotShareOwner() {
        assertEquals(DebugEngineDenial.LEGACY_TASK_PRESENT,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(legacyTaskPresent=true)))
    }
    @Test fun startingOrRunningLegacySessionCannotShareOwner() {
        assertEquals(DebugEngineDenial.LEGACY_ACTIVE,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(legacyStartingOrRunning=true)))
    }
    @Test fun armedReadyHudForbidsHandoff() {
        assertEquals(DebugEngineDenial.LEGACY_STANDBY,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(legacyStandbyArmed=true)))
    }
    @Test fun finalizationInProgressForbidsHandoff() {
        assertEquals(DebugEngineDenial.LEGACY_SAVING,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(legacySaving=true)))
    }
    @Test fun existingOverlayForbidsHandoff() {
        assertEquals(DebugEngineDenial.LEGACY_OVERLAY_ATTACHED,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(legacyOverlayAttached=true)))
    }
    @Test fun expiredButUnclearedLeaseStillForbidsHandoff() {
        assertEquals(DebugEngineDenial.UNRESOLVED_LEASE,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(unresolvedRawLease=true)))
    }
    @Test fun unfinishedDatabaseSessionForbidsHandoff() {
        assertEquals(DebugEngineDenial.UNFINISHED_SQLITE_SESSION,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(unfinishedSqliteSession=true)))
    }
    @Test fun duplicateDebugStartIsRejected() {
        assertEquals(DebugEngineDenial.DEBUG_ALREADY_RESERVED,
            HeimdallDebugOwnershipPolicy.denial(
                good().copy(debugSlotOccupied=true)))
    }
    @Test fun existingNotificationStopIsRoutedToHeimdallOwner() {
        assertEquals(ReservedCommandRoute.HEIMDALL_STOP,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.STOP"))
    }
    @Test fun legacyDisarmActsAsEmergencyStop() {
        assertEquals(ReservedCommandRoute.HEIMDALL_STOP,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.DISARM_HUD"))
    }
    @Test fun internalDebugStopIsRoutedToHeimdall() {
        assertEquals(ReservedCommandRoute.HEIMDALL_STOP,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.DEBUG_HEIMDALL_STOP"))
    }
    @Test fun staleStartCannotCreateLegacySessionWhileDebugOwnsService() {
        assertEquals(ReservedCommandRoute.BLOCK_LEGACY,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.START"))
    }
    @Test fun staleRecoverAndNullRestartCannotEnterLegacyDispatcher() {
        assertEquals(ReservedCommandRoute.BLOCK_LEGACY,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(null))
        assertEquals(ReservedCommandRoute.BLOCK_LEGACY,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.RECOVER_LEASE"))
    }
    @Test fun staleHudActionsCannotCreateCompetingOverlay() {
        assertEquals(ReservedCommandRoute.BLOCK_LEGACY,
            HeimdallDebugOwnershipPolicy.routeWhileReserved(
                "com.locutus5382.coremetrics.HUD_SHOW"))
    }
}
