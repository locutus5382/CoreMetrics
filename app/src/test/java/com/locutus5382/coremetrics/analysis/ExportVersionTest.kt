package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class ExportVersionTest {
    @Test fun appBuildAndReportFormatAreDistinct() {
        assertEquals("0.9.7",ExportVersion.APP_VERSION)
        assertEquals("0.9.7",ExportVersion.VERSION)
        assertTrue(ExportVersion.sourceLabel().contains("app v0.9.7"))
        assertTrue(ExportVersion.sourceLabel().contains("report format v0.9.7"))
        // Catch accidental APK/report drift on every debug/optimized CI build.
        assertEquals(ExportVersion.APP_VERSION,
            BuildConfig.VERSION_NAME.substringBefore('-'))
    }
}
