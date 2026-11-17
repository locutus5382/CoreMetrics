package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class HudReadoutFormatterTest {
    private val values=HudReadoutFormatter.Values(
        cpuTemp="43.0 C",gpuTemp="39.8 C",cpuUse="30 %",
        gpuUse="38 %",ram="6.2 GiB",power="3.8 W",
        batteryTemp="25.0 C")

    @Test fun fiveThemesRetainAllSevenSelectedReadings() {
        for(theme in HudLayoutPolicy.themes) {
            val model=HudReadoutFormatter.render(theme,values,true)
            val body=model.primary+model.secondary+model.tertiary
            listOf("43.0","39.8","30 %","38 %","6.2","3.8","25.0")
                .forEach {assertTrue(theme+" missing "+it,body.contains(it))}
        }
    }
    @Test fun disabledMetricsNeverAppear() {
        val only=HudReadoutFormatter.Values(cpuTemp="43.0 C",
            power="3.8 W")
        for(theme in HudLayoutPolicy.themes) {
            val m=HudReadoutFormatter.render(theme,only,true)
            val text=m.primary+m.secondary+m.tertiary
            assertFalse(text.contains("39.8"))
            assertFalse(text.contains("6.2"))
        }
    }
    @Test fun dukoLensHasOnlyMonospacedClinicalKeyValues() {
        val m=HudReadoutFormatter.render(HudLayoutPolicy.DUKO,values,true)
        assertTrue(m.primary.startsWith("T-CPU"))
        assertTrue(m.primary.contains("\nT-GPU"))
        assertTrue(m.primary.contains("\nPWR"))
        assertTrue(m.primary.contains("43.0 C"))
        assertFalse(m.primary.contains("🔥"))
        assertFalse(m.primary.contains("●"))
        assertTrue(m.secondary.isEmpty())
    }
    @Test fun strikerAndTacticalHaveActualIndependentDataRegions() {
        val striker=HudReadoutFormatter.render(HudLayoutPolicy.STRIKER,values,true)
        assertTrue(striker.primary.contains("CM / LIVE"))
        assertTrue(striker.secondary.contains("PWR"))
        val card=HudReadoutFormatter.render(HudLayoutPolicy.TACTICAL,values,true)
        assertTrue(card.primary.startsWith("THERMALS"))
        assertTrue(card.secondary.startsWith("UTILIZATION"))
        assertTrue(card.tertiary.startsWith("RESOURCES"))
    }
    @Test fun standbyNeverPretendsToSampleOrRecord() {
        for(theme in HudLayoutPolicy.themes) {
            val m=HudReadoutFormatter.render(theme,values,false)
            assertFalse(m.primary.contains("43.0"))
            assertTrue(m.primary.contains("READY")||m.primary.contains("STANDBY")||
                m.primary.contains("START"))
        }
    }
}
