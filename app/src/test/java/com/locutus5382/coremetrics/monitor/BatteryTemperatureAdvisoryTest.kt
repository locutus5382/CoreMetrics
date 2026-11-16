package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class BatteryTemperatureAdvisoryTest {
    private fun policy()=BatteryTemperatureAdvisory()

    @Test fun normalHasNoAdvisoryBefore39() {
        val p=policy()
        assertEquals(BatteryTemperatureAdvisory.State.NORMAL,p.update(35.2,0L))
        assertEquals(BatteryTemperatureAdvisory.State.NORMAL,p.update(38.9,5_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(39.0,10_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.0,15_000L))
    }

    @Test fun warmTurnsHotAfter15RealSecondsAtTwoSecondSampling() {
        val p=policy()
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.5,0L))
        for(t in 2_000L..14_000L step 2_000L)
            assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.5,t))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(43.2,16_000L))
    }

    @Test fun warmTurnsHotAfter15RealSecondsAtFiveSecondSampling() {
        val p=policy()
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.1,0L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.1,5_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.1,10_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(42.1,15_000L))
    }

    @Test fun shortSpikeOrBouncingCannotTurnHot() {
        val p=policy()
        p.update(42.4,0L)
        p.update(42.7,5_000L)
        p.update(41.9,10_000L)
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.6,15_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.5,20_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(42.5,25_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(42.5,30_000L))
    }

    @Test fun yellowReleaseNeeds10SecondsBelow38() {
        val p=policy()
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(39.1,0L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(37.9,5_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(38.1,10_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(37.8,15_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(37.8,20_000L))
        assertEquals(BatteryTemperatureAdvisory.State.NORMAL,p.update(37.8,25_000L))
    }

    @Test fun orangeReleaseNeeds10SecondsBelow41() {
        val p=policy()
        listOf(0L,5_000L,10_000L,15_000L).forEach{p.update(43.0,it)}
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(40.9,20_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(41.1,25_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(40.5,30_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(40.5,35_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(40.5,40_000L))
    }

    @Test fun invalidReadingOrGapDoesNotPretendSafeOrQualifyHot() {
        val p=policy()
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(43.0,0L))
        assertEquals(BatteryTemperatureAdvisory.State.UNAVAILABLE,p.update(null,5_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(43.0,10_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(43.0,20_000L))
        assertEquals(BatteryTemperatureAdvisory.State.WARM,p.update(43.0,25_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(43.0,35_000L))
        assertEquals(BatteryTemperatureAdvisory.State.UNAVAILABLE,p.update(150.0,40_000L))
        assertEquals(BatteryTemperatureAdvisory.State.UNAVAILABLE,p.update(-20.0,45_000L))
        assertEquals(BatteryTemperatureAdvisory.State.UNAVAILABLE,p.update(Double.NaN,50_000L))
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(43.0,55_000L))
    }

    @Test fun resetReturnsToNormalOnNewSession() {
        val p=policy()
        listOf(0L,5_000L,10_000L,15_000L).forEach{p.update(43.0,it)}
        assertEquals(BatteryTemperatureAdvisory.State.HOT,p.update(43.0,20_000L))
        p.reset()
        assertEquals(BatteryTemperatureAdvisory.State.NORMAL,p.update(35.0,30_000L))
    }
}
