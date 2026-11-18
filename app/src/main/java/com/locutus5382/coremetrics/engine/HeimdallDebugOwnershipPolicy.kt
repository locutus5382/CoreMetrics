package com.locutus5382.coremetrics.engine

/**
 * M2-G: pure admission policy for the ONE existing :monitor RecordingService.
 * This is a defense-in-depth check, NOT a transferable ownership credential.
 * Live service flags and the raw lease are rechecked immediately before handoff.
 */
internal data class DebugEngineAdmissionEvidence(
    val debugBuild: Boolean,
    val explicitDebugStart: Boolean,
    val notificationsAllowed: Boolean,
    val legacyTaskPresent: Boolean,
    val legacyStartingOrRunning: Boolean,
    val legacyStandbyArmed: Boolean,
    val legacySaving: Boolean,
    val legacyOverlayAttached: Boolean,
    val unresolvedRawLease: Boolean,
    val unfinishedSqliteSession: Boolean,
    val debugSlotOccupied: Boolean,
)
internal enum class DebugEngineDenial {
    NOT_DEBUG_BUILD, NO_EXPLICIT_REQUEST, NOTIFICATIONS_DENIED,
    LEGACY_TASK_PRESENT, LEGACY_ACTIVE, LEGACY_STANDBY,
    LEGACY_SAVING, LEGACY_OVERLAY_ATTACHED, UNRESOLVED_LEASE,
    UNFINISHED_SQLITE_SESSION,
    DEBUG_ALREADY_RESERVED,
}

internal object HeimdallDebugOwnershipPolicy {
    fun denial(e: DebugEngineAdmissionEvidence): DebugEngineDenial? = when {
        !e.debugBuild -> DebugEngineDenial.NOT_DEBUG_BUILD
        !e.explicitDebugStart -> DebugEngineDenial.NO_EXPLICIT_REQUEST
        !e.notificationsAllowed -> DebugEngineDenial.NOTIFICATIONS_DENIED
        e.debugSlotOccupied -> DebugEngineDenial.DEBUG_ALREADY_RESERVED
        e.legacyTaskPresent -> DebugEngineDenial.LEGACY_TASK_PRESENT
        e.legacyStartingOrRunning -> DebugEngineDenial.LEGACY_ACTIVE
        e.legacyStandbyArmed -> DebugEngineDenial.LEGACY_STANDBY
        e.legacySaving -> DebugEngineDenial.LEGACY_SAVING
        e.legacyOverlayAttached -> DebugEngineDenial.LEGACY_OVERLAY_ATTACHED
        e.unresolvedRawLease -> DebugEngineDenial.UNRESOLVED_LEASE
        e.unfinishedSqliteSession -> DebugEngineDenial.UNFINISHED_SQLITE_SESSION
        else -> null
    }

    /**
     * Once debug ownership is reserved, NO legacy command may fall through.
     * STOP/DISARM remain emergency stop routes; other actions are rejected.
     */
    fun routeWhileReserved(action: String?): ReservedCommandRoute = when (action) {
        "com.locutus5382.coremetrics.DEBUG_HEIMDALL_STOP",
        "com.locutus5382.coremetrics.STOP",
        "com.locutus5382.coremetrics.DISARM_HUD" -> ReservedCommandRoute.HEIMDALL_STOP
        else -> ReservedCommandRoute.BLOCK_LEGACY
    }
}

internal enum class ReservedCommandRoute { HEIMDALL_STOP, BLOCK_LEGACY }
