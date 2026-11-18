package com.locutus5382.coremetrics.monitor

import com.locutus5382.coremetrics.domain.LegacyAuditObservation
import com.locutus5382.coremetrics.domain.LegacyAuditSnapshot
import com.locutus5382.coremetrics.domain.LegacyServiceAudit
import java.util.UUID

/**
 * M2-E debug-only observer inside the SAME :monitor process as RecordingService.
 * In-memory diagnostic snapshot only; never issues commands, reads sensors,
 * writes SQLite or requests WindowManager operations. Process death resets it.
 */
internal object HeimdallServiceShadowAudit {
    @Volatile private var latest: LegacyAuditSnapshot? = null

    @Synchronized fun onServiceCreated() {
        latest = LegacyServiceAudit.begin(UUID.randomUUID().toString())
    }

    @Synchronized fun observe(value: LegacyAuditObservation) {
        latest?.let { latest = LegacyServiceAudit.observe(it, value) }
    }

    fun snapshot(): LegacyAuditSnapshot? = latest
}
