package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class PracticalAnalyticsTest {
    private fun session()=SessionStore.Session(4,100_000L,140_000L,
        "Session 4","Stock","COMPLETED",20,
        graphicsDriver="Turnip",resolutionScale="Handheld 1x")
    private fun p(time:Long,watts:Double?,status:String)=SessionStore.Point(
        time,48.0,40.0,32.0,28.0,6.0,watts,null,"dev.eden",
        batteryPowerStatus=status)
    @Test fun mixedPowerNeverIntegratesAcrossChargerBoundary() {
        val r=BenchmarkAnalyzer.analyze(session(),listOf(
            p(100_000,4.0,"DISCHARGING; BATTERY_NET_ONLY"),
            p(102_000,4.0,"DISCHARGING; BATTERY_NET_ONLY"),
            p(104_000,-3.0,"CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY"),
            p(106_000,-4.0,"CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY")
        ))
        assertEquals(BenchmarkAnalyzer.PowerCondition.MIXED,r.energy.powerCondition)
        assertEquals(8.0/3600.0,r.energy.wh!!,0.0000001)
        assertEquals(2.0,r.energy.measuredSeconds,0.0001)
        assertTrue(BenchmarkAnalyzer.text(r).contains("Manual driver tag: Turnip"))
    }
    @Test fun pluggedOnlyDoesNotBecomeZeroDischarge() {
        val r=BenchmarkAnalyzer.analyze(session(),listOf(
            p(100_000,-3.0,"CHARGING_OR_EXTERNAL_POWER"),
            p(102_000,-2.0,"CHARGING_OR_EXTERNAL_POWER")))
        assertNull(r.energy.wh)
        assertEquals(BenchmarkAnalyzer.PowerCondition.PLUGGED_IN,r.energy.powerCondition)
    }
    @Test fun warmupFilterExcludesFirstThirtySecondsWithoutMutatingSource() {
        val samples=(0..35).map {
            p(100_000+it*2_000L,4.0,"DISCHARGING; BATTERY_NET_ONLY")
        }
        val untrimmed=BenchmarkAnalyzer.analyze(session(),samples)
        val warmed=BenchmarkAnalyzer.analyze(session(),samples,30)
        assertEquals(36,untrimmed.sampleCount)
        assertEquals(21,warmed.sampleCount)
        assertTrue(warmed.energy.wh!!<untrimmed.energy.wh!!)
        assertEquals(30,warmed.skippedWarmUpSeconds)
        assertEquals(36,samples.size)
    }
}
