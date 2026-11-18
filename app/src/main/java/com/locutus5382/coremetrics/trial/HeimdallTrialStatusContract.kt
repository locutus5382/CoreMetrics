package com.locutus5382.coremetrics.trial

import android.os.Bundle

/**
 * Versioned IPC contract between the :monitor provider and the isolated
 * Heimdall Trial UI. Never treat a missing required key as "idle".
 */
internal object HeimdallTrialStatusContract {
    const val VERSION = 1
    val requiredKeys: Set<String> = linkedSetOf(
        "heimdallStatusContractVersion",
        "serviceAlive",
        "running",
        "starting",
        "standby",
        "saving",
        "hudAttached",
        "heimdallRawRecoveryOwner",
        "heimdallUnresolvedRawLease",
        "heimdallRecorderPhase",
        "heimdallRecorderOriginalId",
        "heimdallRecorderNeedsRepair",
    )

    fun missingFields(data: Bundle): List<String> =
        requiredKeys.filterNot(data::containsKey)

    fun verify(data: Bundle) {
        val missing = missingFields(data)
        check(missing.isEmpty()) {
            "MONITOR_STATUS_INCOMPLETE: " + missing.joinToString(",")
        }
        check(data.getInt("heimdallStatusContractVersion", -1) == VERSION) {
            "MONITOR_STATUS_VERSION_MISMATCH"
        }
    }
}
