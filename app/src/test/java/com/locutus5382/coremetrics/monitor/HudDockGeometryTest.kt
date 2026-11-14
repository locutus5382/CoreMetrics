package com.locutus5382.coremetrics.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class HudDockGeometryTest {
    @Test fun snapsRightAndLeftWithActualTabWidth() {
        assertEquals(956 to 250,HudDockGeometry.place(
            DockEdge.RIGHT,1000,600,44,78,200,250))
        assertEquals(0 to 250,HudDockGeometry.place(
            DockEdge.LEFT,1000,600,44,78,200,250))
    }
    @Test fun snapsTopAndClampsToScreen() {
        assertEquals(912 to 0,HudDockGeometry.place(
            DockEdge.TOP,1000,600,88,44,9999,100))
        assertEquals(0 to 0,HudDockGeometry.place(
            DockEdge.TOP,1000,600,88,44,-100,-40))
        assertEquals(0 to 522,HudDockGeometry.place(
            DockEdge.LEFT,1000,600,44,78,100,9999))
    }
    @Test fun narrowScreenNeverProducesNegativePosition() {
        assertEquals(0 to 0,HudDockGeometry.place(
            DockEdge.RIGHT,30,20,44,78,100,100))
    }
}
