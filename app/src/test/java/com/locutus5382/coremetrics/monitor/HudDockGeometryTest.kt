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
    @Test fun expandedHudParksNearCornersWithoutCollapsing() {
        val topRight=HudDockGeometry.park(1600,900,520,112,1078,1,
            margin=4,snapDistance=16)
        assertEquals(HudDockGeometry.Parked(1076,4,true),topRight)
        val topLeft=HudDockGeometry.park(1600,900,520,112,0,0,
            margin=4,snapDistance=16)
        assertEquals(HudDockGeometry.Parked(4,4,false),topLeft)
        val middle=HudDockGeometry.park(1600,900,520,112,600,300,
            margin=4,snapDistance=16)
        assertEquals(HudDockGeometry.Parked(600,300,false),middle)
    }
    @Test fun parkingClampsSmallScreensWithoutNegativeCoordinates() {
        val narrow=HudDockGeometry.park(30,20,44,78,999,-100,
            margin=4,snapDistance=16)
        assertEquals(0,narrow.x)
        assertEquals(0,narrow.y)
    }
        @Test fun narrowScreenNeverProducesNegativePosition() {
        assertEquals(0 to 0,HudDockGeometry.place(
            DockEdge.RIGHT,30,20,44,78,100,100))
    }
}
