package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class RecorderTimingStatsTest {
    @Test fun boundedTimingsIncludeHudAndDatabaseWithoutPerTickPersistence() {
        val timing=RecorderTimingStats(capacity=16)
        repeat(8) { timing.record(15.0,1.5,2.0,18.5,0.0) }
        val report=timing.report()
        assertTrue(report.contains("ticks=8"))
        assertTrue(report.contains("collector=15.0"))
        assertTrue(report.contains("db=1.5"))
        assertTrue(report.contains("hud=2.0"))
        assertTrue(report.contains("total=18.5"))
        assertTrue(report.contains("start_late=0.0"))
    }

    @Test fun hiddenHudIsNotMistakenForZeroCost() {
        val timing=RecorderTimingStats(capacity=4)
        timing.record(16.0,1.0,null,17.0,0.0)
        assertTrue(timing.report().contains("hud=na"))
    }

    @Test fun invalidOrNegativeMeasurementsDoNotEnterStats() {
        val timing=RecorderTimingStats(capacity=4)
        timing.record(Double.NaN,1.0,1.0,1.0,0.0)
        timing.record(1.0,-2.0,1.0,1.0,0.0)
        timing.record(1.0,1.0,Double.NaN,2.0,0.0)
        assertEquals(1L,timing.count)
        assertTrue(timing.report().contains("hud=na"))
    }

    @Test fun rollingWindowStaysBounded() {
        val timing=RecorderTimingStats(capacity=4)
        repeat(40) { i ->
            timing.record(i.toDouble(),i.toDouble(),0.1,i.toDouble(),0.0)
        }
        assertEquals(40L,timing.count)
        assertTrue(timing.report().contains("window=4"))
        assertTrue(timing.report().contains("collector=39.0"))
    }
}
