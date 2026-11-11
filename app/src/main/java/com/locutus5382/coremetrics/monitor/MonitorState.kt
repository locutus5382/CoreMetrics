package com.locutus5382.coremetrics.monitor

import com.locutus5382.coremetrics.telemetry.Snapshot

/** In the :monitor process only. Cross-process UI state uses StatusProvider. */
internal object MonitorState {
    @Volatile var running = false
    @Volatile var starting = false
    @Volatile var sessionId = 0L
    @Volatile var startedAt = 0L
    @Volatile var sampleCount = 0L
    @Volatile var message = "Idle"
    @Volatile var latest: Snapshot? = null
    fun clear(messageText: String) {
        running = false; starting = false; sessionId = 0L
        startedAt = 0L; latest = null; message = messageText
    }
}
