package com.locutus5382.coremetrics.analysis

import org.junit.Assert.*
import org.junit.Test

class ExportVersionTest {
    @Test fun appBuildAndReportFormatAreDistinct() {
        assertEquals("0.9.6.6",ExportVersion.APP_VERSION)
        assertEquals("0.9.6",ExportVersion.VERSION)
        assertTrue(ExportVersion.sourceLabel().contains("app v0.9.6.6"))
        assertTrue(ExportVersion.sourceLabel().contains("report format v0.9.6"))
    }
}
