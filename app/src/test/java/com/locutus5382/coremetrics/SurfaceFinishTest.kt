package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.ui.SurfaceFinish
import org.junit.Assert.*
import org.junit.Test

class SurfaceFinishTest {
    @Test fun allFourStableKeysRoundTripAndAppearInExpectedOrder() {
        val expected=listOf("frosted","crystal_aero","solar_pulse","cobalt_matrix")
        assertEquals(expected,SurfaceFinish.values().map{it.key})
        for(f in SurfaceFinish.values()) {
            assertSame(f,SurfaceFinish.parse(f.key))
            assertTrue(f.displayName.isNotBlank())
            assertTrue(f.description.isNotBlank())
        }
    }
    @Test fun legacyKeysStayLoadableWithoutPreferenceMigration() {
        assertEquals("True Glass",SurfaceFinish.parse("crystal_aero").displayName)
        assertEquals("Modern Colorful",SurfaceFinish.parse("solar_pulse").displayName)
        assertEquals("Technical Cockpit",SurfaceFinish.parse("cobalt_matrix").displayName)
        assertEquals("Frosted Glass",SurfaceFinish.parse("frosted").displayName)
    }
    @Test fun unknownOrMissingValuesAlwaysUseOriginalFrosted() {
        assertSame(SurfaceFinish.FROSTED,SurfaceFinish.parse(null))
        assertSame(SurfaceFinish.FROSTED,SurfaceFinish.parse(""))
        assertSame(SurfaceFinish.FROSTED,SurfaceFinish.parse("tactical"))
        assertSame(SurfaceFinish.FROSTED,SurfaceFinish.parse("liquid"))
    }
}
