package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.StopReason

/** A lease/ownership fault is a storage-integrity failure, not sensor noise. */
internal object HeimdallFatalStopPolicy {
    fun reason(errorType: String): StopReason = when {
        // A matching lease already contains the user's terminal STOP.
        // This is not evidence of storage corruption and must not turn
        // a normal session into FAILED merely because sampling raced STOP.
        errorType == "LEASE_TERMINAL_STOP_SEEN" -> StopReason.USER
        errorType.startsWith("LEASE_") -> StopReason.STORAGE_ERROR
        else -> StopReason.SAMPLE_FAILURE
    }
}
