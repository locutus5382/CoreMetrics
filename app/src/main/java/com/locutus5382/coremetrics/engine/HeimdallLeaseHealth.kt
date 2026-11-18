package com.locutus5382.coremetrics.engine

/** Stable, non-PII fault codes for the recorder's mandatory lease checkpoint. */
internal enum class HeimdallLeaseHealthIssue {
    OWNER_CHANGED,
    TERMINAL_STOP_SEEN,
    COMMIT_FAILED,
}

internal class HeimdallLeaseHealthException(
    val issue: HeimdallLeaseHealthIssue,
) : IllegalStateException("LEASE_" + issue.name)
