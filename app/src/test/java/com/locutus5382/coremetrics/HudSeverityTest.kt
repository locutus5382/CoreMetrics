package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.monitor.HudSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class HudSeverityTest {
    private val N=HudSeverity.Level.NORMAL
    private val Y=HudSeverity.Level.ELEVATED
    private val R=HudSeverity.Level.HIGH
    @Test fun thermalThresholdsAndHysteresis(){
        assertEquals(N,HudSeverity.thermal(null))
        assertEquals(Y,HudSeverity.thermal(65.0))
        assertEquals(Y,HudSeverity.thermal(64.2,Y))
        assertEquals(N,HudSeverity.thermal(62.9,Y))
        assertEquals(R,HudSeverity.thermal(80.0))
        assertEquals(R,HudSeverity.thermal(78.2,R))
        assertEquals(Y,HudSeverity.thermal(77.9,R))
    }
    @Test fun loadIsNotAutomaticallyCritical(){
        assertEquals(Y,HudSeverity.usage(100))
        assertEquals(N,HudSeverity.usage(0))
        assertEquals(Y,HudSeverity.usage(87,Y))
        assertEquals(N,HudSeverity.usage(84,Y))
    }
    @Test fun ramFollowsAndroidMemoryThresholdRatherThanPercentUsed(){
        assertEquals(R,HudSeverity.ram(true,300,100))
        assertEquals(Y,HudSeverity.ram(false,140,100))
        assertEquals(N,HudSeverity.ram(false,300,100))
        assertEquals(N,HudSeverity.ram(false,3,0))
    }
}