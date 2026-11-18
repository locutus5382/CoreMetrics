package com.locutus5382.coremetrics.engine

/**
 * Prepared default-engine cutover policy, deliberately NOT connected to the
 * production START dispatcher in M2-M/N/O preparation.
 *
 * The caller must supply verified evidence from the service owner, not a UI
 * preference, status-provider snapshot, or cached Quick Settings state.
 */
internal enum class CutoverRequestedMode {
    LEGACY, HEIMDALL_SHADOW, HEIMDALL_DEFAULT,
}
internal enum class CutoverRoute {
    LEGACY, HEIMDALL_SHADOW, HEIMDALL_NATIVE, REFUSE_AND_REPAIR,
}
internal enum class CutoverBlocker {
    DEVICE_VALIDATION_MISSING,
    RECOVERY_VALIDATION_MISSING,
    EXPORT_REGRESSIONS_MISSING,
    ROLLBACK_NOT_VERIFIED,
    EXISTING_LEGACY_RECORDING,
    UNRESOLVED_LEASE,
    EXISTING_OVERLAY,
    UNFINISHED_SQLITE_SESSION,
    OTHER_RECORDER_OWNER_ACTIVE,
}
internal data class CutoverGateEvidence(
    val realDeviceValidated: Boolean = false,
    val processDeathAndStopValidated: Boolean = false,
    val sessionAndExportRegressionPassed: Boolean = false,
    val fallbackRollbackVerified: Boolean = false,
    val legacyTaskActive: Boolean = false,
    val unresolvedLease: Boolean = false,
    val legacyOverlayAttached: Boolean = false,
    val unfinishedSqliteSession: Boolean = false,
    val differentOwnerActive: Boolean = false,
)
internal data class CutoverDecision(
    val route: CutoverRoute,
    val blockers: Set<CutoverBlocker> = emptySet(),
)

/**
 * No release build uses this decision in M2-M/N/O preparation.
 * SHADOW mode observes only; it never creates a second recorder.
 * Unresolved old session ownership takes priority over selecting a mode.
 */
internal object HeimdallCutoverGate {
    fun decide(
        requested: CutoverRequestedMode,
        evidence: CutoverGateEvidence,
    ): CutoverDecision {
        val ownerConflicts = buildSet {
            if (evidence.legacyTaskActive)
                add(CutoverBlocker.EXISTING_LEGACY_RECORDING)
            if (evidence.unresolvedLease)
                add(CutoverBlocker.UNRESOLVED_LEASE)
            if (evidence.legacyOverlayAttached)
                add(CutoverBlocker.EXISTING_OVERLAY)
            if (evidence.unfinishedSqliteSession)
                add(CutoverBlocker.UNFINISHED_SQLITE_SESSION)
            if (evidence.differentOwnerActive)
                add(CutoverBlocker.OTHER_RECORDER_OWNER_ACTIVE)
        }
        if (ownerConflicts.isNotEmpty())
            return CutoverDecision(CutoverRoute.REFUSE_AND_REPAIR, ownerConflicts)
        return when (requested) {
            CutoverRequestedMode.LEGACY -> CutoverDecision(CutoverRoute.LEGACY)
            CutoverRequestedMode.HEIMDALL_SHADOW ->
                CutoverDecision(CutoverRoute.HEIMDALL_SHADOW)
            CutoverRequestedMode.HEIMDALL_DEFAULT -> {
                val validation = buildSet {
                    if (!evidence.realDeviceValidated)
                        add(CutoverBlocker.DEVICE_VALIDATION_MISSING)
                    if (!evidence.processDeathAndStopValidated)
                        add(CutoverBlocker.RECOVERY_VALIDATION_MISSING)
                    if (!evidence.sessionAndExportRegressionPassed)
                        add(CutoverBlocker.EXPORT_REGRESSIONS_MISSING)
                    if (!evidence.fallbackRollbackVerified)
                        add(CutoverBlocker.ROLLBACK_NOT_VERIFIED)
                }
                if (validation.isEmpty())
                    CutoverDecision(CutoverRoute.HEIMDALL_NATIVE)
                else CutoverDecision(CutoverRoute.LEGACY, validation)
            }
        }
    }
}
