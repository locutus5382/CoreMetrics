package com.locutus5382.coremetrics.monitor

import com.locutus5382.coremetrics.telemetry.Snapshot

/** In the :monitor process only. Cross-process UI state uses StatusProvider. */
internal object MonitorState {
    /** False for a provider-only :monitor process started by a tile query. */
    @Volatile var serviceCreated=false
    @Volatile var running = false
    @Volatile var starting = false
    @Volatile var standby = false
    @Volatile var sessionId = 0L
    @Volatile var startedAt = 0L
    @Volatile var sampleCount = 0L
    @Volatile var message = "Idle"
    @Volatile var hudState = "HUD_OFF"
    @Volatile var hudVisible = false
    // Requested visibility, actual overlay attachment and temporary in-app
    // pause are separate concepts, especially for Quick Settings.
    @Volatile var hudRequested = false
    @Volatile var hudAttached = false
    @Volatile var appPaused = false
    @Volatile var saving = false
    @Volatile var hudLocked = false
    // Drawer edits occur in :monitor, not the Compose UI process.
    @Volatile var hudOptionRevision = 0L
    @Volatile var hudOptions: HudOptions? = null
    @Volatile var latest: Snapshot? = null
    /** Debug engine projection only; never authorizes a recording command. */
    @Volatile var heimdallRecorderPhase: String? = null
    @Volatile var heimdallNeedsRetry = false
    @Volatile var heimdallNeedsRepair = false
    @Volatile var heimdallOriginalSessionId = 0L
    @Volatile var heimdallIssueCode: String? = null
    fun clear(messageText: String) {
        heimdallRecorderPhase = null
        heimdallNeedsRetry = false
        heimdallNeedsRepair = false
        heimdallOriginalSessionId = 0L
        heimdallIssueCode = null
        running = false; starting = false; sessionId = 0L
        startedAt = 0L; latest = null; message = messageText
    }
}
