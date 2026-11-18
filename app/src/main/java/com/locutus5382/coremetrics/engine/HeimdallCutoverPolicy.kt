package com.locutus5382.coremetrics.engine

/**
 * Prepared M2-N cutover contract. THIS IS NOT the live service dispatcher.
 *
 * No successful trial test alone may select the native engine as the default.
 * Explicit approval, a complete regression gate, clean session ownership and
 * a verified rollback build are independently required.
 */
internal data class HeimdallCutoverEvidence(
    val nativeDefaultCompiled: Boolean,
    val odinDeviceValidated: Boolean,
    val nativeProcessDeathAndStopValidated: Boolean,
    val sessionAndExportRegressionPassed: Boolean,
    val allCriticalCiPassed: Boolean,
    val userApprovedDefault: Boolean,
    val priorSessionsBackedUp: Boolean,
    val rollbackBuildVerified: Boolean,
    val legacyRecorderCurrentlyActive: Boolean,
    val legacyLeaseStillPresent: Boolean,
    val unresolvedHeimdallLease: Boolean,
    val unfinishedSqliteSession: Boolean,
    val overlayStillAttached: Boolean,
)

internal enum class HeimdallCutoverDecision {
    LEGACY_DEFAULT,
    HOLD_FOR_TESTS,
    HOLD_FOR_SESSION_RECOVERY,
    HEIMDALL_DEFAULT_ELIGIBLE,
}

internal object HeimdallCutoverPolicy {
    fun decide(e: HeimdallCutoverEvidence): HeimdallCutoverDecision = when {
        // Normal CoreMetrics builds use their original runtime. A trial APK
        // never changes the default of the independently installed app.
        !e.nativeDefaultCompiled -> HeimdallCutoverDecision.LEGACY_DEFAULT
        e.legacyRecorderCurrentlyActive || e.legacyLeaseStillPresent ||
            e.unresolvedHeimdallLease || e.unfinishedSqliteSession ||
            e.overlayStillAttached ->
                HeimdallCutoverDecision.HOLD_FOR_SESSION_RECOVERY
        !e.odinDeviceValidated || !e.nativeProcessDeathAndStopValidated ||
            !e.sessionAndExportRegressionPassed || !e.allCriticalCiPassed ||
            !e.userApprovedDefault || !e.priorSessionsBackedUp ||
            !e.rollbackBuildVerified ->
                HeimdallCutoverDecision.HOLD_FOR_TESTS
        else -> HeimdallCutoverDecision.HEIMDALL_DEFAULT_ELIGIBLE
    }
}
