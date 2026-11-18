package com.locutus5382.coremetrics.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class HeimdallLeaseRestartGuardTest {
    private fun route(active: Boolean, owner: String?) =
        HeimdallLeaseRestartGuard.route(active, owner)

    @Test fun noLeaseDoesNotBlockStandardStartup() {
        assertEquals(LeaseOwnerRoute.LEGACY_ALLOWED, route(false, null))
    }
    @Test fun noActiveLeaseWithStaleOwnerMetadataIsNotAuthority() {
        assertEquals(LeaseOwnerRoute.LEGACY_ALLOWED, route(false, "HEIMDALL_DEBUG"))
    }
    @Test fun oldLeaseWithoutOwnerMarkerRemainsLegacyCompatible() {
        assertEquals(LeaseOwnerRoute.LEGACY_ALLOWED, route(true, null))
    }
    @Test fun explicitLegacyOwnerMayResumeLegacyRecorder() {
        assertEquals(LeaseOwnerRoute.LEGACY_ALLOWED, route(true, "LEGACY"))
    }
    @Test fun heimdallOwnerCannotBeResumedByLegacySampler() {
        assertEquals(LeaseOwnerRoute.NATIVE_RECOVERY_REQUIRED,
            route(true, "HEIMDALL_DEBUG"))
    }
    @Test fun unknownOwnerIsQuarantinedNotDefaultedToLegacy() {
        assertEquals(LeaseOwnerRoute.UNKNOWN_OWNER_QUARANTINED,
            route(true, "UNKNOWN"))
    }
    @Test fun tamperedOrFutureOwnerCannotStartLegacySampler() {
        assertEquals(LeaseOwnerRoute.UNKNOWN_OWNER_QUARANTINED,
            route(true, "HEIMDALL_V2"))
    }
    @Test fun blankOwnerCannotBecomeLegacy() {
        assertEquals(LeaseOwnerRoute.UNKNOWN_OWNER_QUARANTINED,
            route(true, ""))
    }
    @Test fun caseMismatchCannotBeTreatedAsLegacy() {
        assertEquals(LeaseOwnerRoute.UNKNOWN_OWNER_QUARANTINED,
            route(true, "legacy"))
    }
    @Test fun ownerRoutingDoesNotDependOnSessionStatus() {
        // This guard must run before querying the SQLite row or recovery age.
        // Even an expired or nonrecoverable active lease cannot be claimed.
        assertEquals(LeaseOwnerRoute.NATIVE_RECOVERY_REQUIRED,
            route(true, "HEIMDALL_DEBUG"))
    }
}
