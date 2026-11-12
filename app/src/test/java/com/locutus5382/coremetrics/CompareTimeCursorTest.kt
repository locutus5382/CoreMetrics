package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.ui.CompareTimeCursor
import org.junit.Assert.*
import org.junit.Test

class CompareTimeCursorTest {
    private fun point(ms:Long) = SessionStore.Point(ms,50.0,45.0,25.0,30.0,
        3.0,4.5,null,"org.eden.emulator")

    @Test fun matchesElapsedTimeNotAbsoluteEpoch() {
        val points=listOf(point(100_000),point(105_000),point(110_000))
        assertEquals(105_000L,CompareTimeCursor.nearest(points,5_000)?.timeMs)
        assertEquals(105_000L,CompareTimeCursor.nearest(points,7_000)?.timeMs)
    }

    @Test fun longGapAndRunEndAreUnavailable() {
        val points=listOf(point(100_000),point(105_000),point(200_000))
        assertNull(CompareTimeCursor.nearest(points,50_000))
        assertNull(CompareTimeCursor.nearest(points,105_000))
        assertNull(CompareTimeCursor.nearest(points,-1))
    }
}
