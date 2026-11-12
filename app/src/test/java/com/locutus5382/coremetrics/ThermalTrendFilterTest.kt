package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.telemetry.ThermalTrendFilter
import org.junit.Assert.*
import org.junit.Test

class ThermalTrendFilterTest {
    @Test fun emaKeepsPeakRawAndReportsRisingTrend() {
        val filter = ThermalTrendFilter(0.35)
        val a = filter.update(0L, 50.0)
        assertEquals(50.0, a.smoothedC!!, 0.0001)
        assertEquals(ThermalTrendFilter.Direction.UNKNOWN, a.direction)
        val b = filter.update(5_000L, 60.0)
        assertEquals(53.5, b.smoothedC!!, 0.0001)
        assertEquals(60.0, b.rawPeakC!!, 0.0001)
        assertEquals(ThermalTrendFilter.Direction.RISING, b.direction)
        val c = filter.update(10_000L, 50.0)
        assertEquals(52.275, c.smoothedC!!, 0.0001)
        assertEquals(60.0, c.rawPeakC!!, 0.0001)
    }

    @Test fun coolingMissingSensorAndReset() {
        val f = ThermalTrendFilter()
        f.update(0L, 70.0)
        f.update(5_000L, 55.0)
        val cooling = f.update(10_000L, 40.0)
        assertEquals(ThermalTrendFilter.Direction.FALLING, cooling.direction)
        val missing = f.update(15_000L, null)
        assertNull(missing.smoothedC)
        assertEquals(70.0, missing.rawPeakC!!, 0.00001)
        f.reset()
        val restarted = f.update(20_000L, 39.0)
        assertEquals(39.0, restarted.smoothedC!!, 0.00001)
        assertEquals(39.0, restarted.rawPeakC!!, 0.00001)
        assertEquals(ThermalTrendFilter.Direction.UNKNOWN, restarted.direction)
    }

    @Test fun stableAndOldTimestampsDoNotMislead() {
        val f = ThermalTrendFilter()
        f.update(0L, 44.0)
        assertEquals(ThermalTrendFilter.Direction.STABLE, f.update(15_000L, 44.0).direction)
        assertEquals(ThermalTrendFilter.Direction.UNKNOWN, f.update(8_000L, 42.0).direction)
    }
}
