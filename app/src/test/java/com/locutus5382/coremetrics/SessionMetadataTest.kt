package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.SessionMetadata
import org.junit.Assert.*
import org.junit.Test

class SessionMetadataTest {
    @Test fun acceptedPresetsAndCustomNames() {
        assertEquals("Turnip",SessionMetadata.driver("Turnip"))
        assertEquals("System",SessionMetadata.driver(" System "))
        assertEquals("Custom: Turnip R28",SessionMetadata.driver("Custom: Turnip R28"))
        assertEquals("Handheld 1x",SessionMetadata.resolution("Handheld 1x"))
        assertEquals("Docked",SessionMetadata.resolution("Docked"))
        assertEquals("Custom: 1080p 0.75x",SessionMetadata.resolution("Custom: 1080p 0.75x"))
    }
    @Test fun noAutoAttributionAndDangerousLabelRejected() {
        assertNull(SessionMetadata.driver(null))
        assertNull(SessionMetadata.driver("Qualcomm guessed by telemetry"))
        assertNull(SessionMetadata.resolution("=HYPERLINK()"))
        assertNull(SessionMetadata.driver("Custom: "))
    }
}
