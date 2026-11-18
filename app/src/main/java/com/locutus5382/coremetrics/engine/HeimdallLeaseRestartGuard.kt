package com.locutus5382.coremetrics.engine

/**
 * Legacy recovery is allowed only for a known legacy lease (including older
 * installs with no engine_owner key). A Heimdall-owned or unknown active lease
 * must never fall through to the legacy sampler or SessionStore.begin().
 *
 * In M2-H, foreign-owner lease recovery is quarantined without clearing
 * the original database row. M2-I will add native, service-owned resume.
 */
internal enum class LeaseOwnerRoute {
    LEGACY_ALLOWED, NATIVE_RECOVERY_REQUIRED, UNKNOWN_OWNER_QUARANTINED,
}
internal object HeimdallLeaseRestartGuard {
    fun route(rawActive: Boolean, recordedOwner: String?): LeaseOwnerRoute = when {
        !rawActive -> LeaseOwnerRoute.LEGACY_ALLOWED
        recordedOwner == null || recordedOwner == "LEGACY" ->
            LeaseOwnerRoute.LEGACY_ALLOWED
        recordedOwner == "HEIMDALL_DEBUG" ->
            LeaseOwnerRoute.NATIVE_RECOVERY_REQUIRED
        else -> LeaseOwnerRoute.UNKNOWN_OWNER_QUARANTINED
    }
}
