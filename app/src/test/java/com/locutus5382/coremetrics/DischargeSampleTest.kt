package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.DischargeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DischargeSampleTest {
    @Test fun dischargingIsTheOnlyPermittedSourceForComparisonCharts() {
        assertEquals(7.5,DischargeSample.watts(7.5,
            "DISCHARGING; BATTERY_NET_ONLY")!!,0.0)
        assertNull(DischargeSample.watts(7.5,
            "CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY"))
        assertNull(DischargeSample.watts(7.5,
            "BATTERY_STATUS_UNKNOWN; BATTERY_NET_ONLY"))
        assertNull(DischargeSample.watts(7.5,null))
    }

    @Test fun impossibleAndMissingPowerReadingsAreExcluded() {
        assertNull(DischargeSample.watts(null,"DISCHARGING; BATTERY_NET_ONLY"))
        assertNull(DischargeSample.watts(Double.NaN,"DISCHARGING"))
        assertNull(DischargeSample.watts(Double.POSITIVE_INFINITY,"DISCHARGING"))
        assertNull(DischargeSample.watts(-2.0,"DISCHARGING"))
        assertNull(DischargeSample.watts(0.0,"DISCHARGING"))
        assertNull(DischargeSample.watts(100.0,"DISCHARGING"))
        assertEquals(99.9,DischargeSample.watts(99.9,"DISCHARGING")!!,0.0)
    }
}
