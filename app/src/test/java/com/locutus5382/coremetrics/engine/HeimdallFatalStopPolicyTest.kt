package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.StopReason
import org.junit.Assert.assertEquals
import org.junit.Test

class HeimdallFatalStopPolicyTest {
    @Test fun leaseCommitFailureIsStorageError() {
        assertEquals(StopReason.STORAGE_ERROR,
            HeimdallFatalStopPolicy.reason("LEASE_COMMIT_FAILED"))
    }
    @Test fun wrongLeaseOwnerIsStorageError() {
        assertEquals(StopReason.STORAGE_ERROR,
            HeimdallFatalStopPolicy.reason("LEASE_OWNER_CHANGED"))
    }
    @Test fun persistedStopObservedDuringSampleIsStorageIntegrityEvent() {
        assertEquals(StopReason.STORAGE_ERROR,
            HeimdallFatalStopPolicy.reason("LEASE_TERMINAL_STOP_SEEN"))
    }
    @Test fun ordinaryCollectorFailureRemainsSampleFailure() {
        assertEquals(StopReason.SAMPLE_FAILURE,
            HeimdallFatalStopPolicy.reason("IOException"))
    }
}
