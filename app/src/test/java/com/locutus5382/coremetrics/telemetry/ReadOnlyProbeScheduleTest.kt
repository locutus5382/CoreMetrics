package com.locutus5382.coremetrics.telemetry

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyProbeScheduleTest {
    @Test fun backoffAfterRepeatedUnsupportedResultsAndRecoverLater() {
        val p=ReadOnlyProbeSchedule(10_000L,120_000L,3)
        assertTrue(p.due(0L))
        p.mark(0L,false)
        assertFalse(p.due(9_999L))
        assertTrue(p.due(10_000L))
        p.mark(10_000L,false)
        assertTrue(p.due(20_000L))
        p.mark(20_000L,false)
        assertFalse(p.due(30_000L))
        assertFalse(p.due(139_999L))
        assertTrue(p.due(140_000L))
        p.mark(140_000L,true)
        assertFalse(p.due(149_999L))
        assertTrue(p.due(150_000L))
    }

    @Test fun validZeroIsARealReadingAndResetsBackoff() {
        val p=ReadOnlyProbeSchedule(5_000L,60_000L,2)
        p.mark(0L,false)
        p.mark(5_000L,false)
        assertFalse(p.due(10_000L))
        assertTrue(p.due(65_000L))
        p.mark(65_000L,true) // a parsed valid zero counts as success
        assertTrue(p.due(70_000L))
    }

    @Test fun monotonicResetDoesNotPermanentlyLockOutProbe() {
        val p=ReadOnlyProbeSchedule(10_000L,120_000L)
        p.mark(400_000L,false)
        assertTrue(p.due(5_000L))
        assertFalse(p.due(-1L))
    }
}
