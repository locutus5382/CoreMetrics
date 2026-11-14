package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.SessionMetadata
import org.junit.Assert.*
import org.junit.Test

class SessionMetadataTest {
    @Test fun notesPreserveLanguageButRemoveControls() {
        assertEquals("Hades · T30",SessionMetadata.note("  Hades\n ·\r T30\u0085  "))
        assertEquals("Проба · 游戏",SessionMetadata.note("Проба · 游戏"))
        assertNull(SessionMetadata.note("\n\r\u0007"))
        assertEquals(64,SessionMetadata.note("a".repeat(80))?.length)
    }
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
