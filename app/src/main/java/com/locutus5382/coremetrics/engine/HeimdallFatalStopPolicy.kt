package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.StopReason

/** A lease/ownership fault is a storage-integrity failure, not sensor noise. */
internal object HeimdallFatalStopPolicy {
    fun reason(errorType: String): StopReason =
        if (errorType.startsWith("LEASE_")) StopReason.STORAGE_ERROR
        else StopReason.SAMPLE_FAILURE
}
