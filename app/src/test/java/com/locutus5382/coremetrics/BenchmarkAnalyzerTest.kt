package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class BenchmarkAnalyzerTest {
    private fun sess(status:String="COMPLETED", config:String="Stock")=
        SessionStore.Session(1L,1_000_000L,1_120_000L,
            "Mario Kart",config,status,61)
    private fun p(t:Long,w:Double?=4.0,status:String="DISCHARGING; BATTERY_NET_ONLY",
        caps:String="policy3 max 1.79 GHz · policy7 max 1.84 GHz") =
        SessionStore.Point(t,cpu=45.0,gpu=39.0,cpuLoad=42.0,gpuLoad=40.0,
            ram=5.5,batteryWatts=w,sfFps=null,appPackage="dev.eden.eden_emulator.nightly",
            observedCpuLimits=caps,batteryPowerStatus=status)
    @Test fun energyUsesTrapezoidOnlyOnValidDischargingIntervals() {
        val r=BenchmarkAnalyzer.analyze(sess(),listOf(p(100_000,4.0),p(102_000,6.0),
            p(104_000,4.0)))
        assertEquals(20.0/3600.0,r.energy.wh!!,0.000001)
        assertEquals(4.0,r.energy.measuredSeconds,0.001)
        assertTrue(r.quality.labelConflict)
        assertNull(r.frames.fpsPerBatteryW)
    }
    @Test fun skipGapsAndChargingThenReportNoExtrapolation() {
        val samples=listOf(p(100_000),p(102_000),
            p(150_000,4.0),p(152_000,4.0,"CHARGING_OR_EXTERNAL_POWER"))
        val r=BenchmarkAnalyzer.analyze(sess("INTERRUPTED_RECOVERED"),samples)
        assertEquals(1,r.quality.gapCount)
        assertEquals(2.0,r.energy.measuredSeconds,0.01)
        assertTrue(r.quality.score<60)
    }
    @Test fun nearFlatThermalWindowIsNotEquilibriumClaim() {
        val input=(0..45).map{p(1_000_000L+it*2_000L)}
        val r=BenchmarkAnalyzer.analyze(sess(),input)
        assertTrue(r.thermal.state.startsWith("low variation"))
        assertFalse(r.thermal.state.contains("steady-state"))
        assertNotNull(r.thermal.slopeCPerMin)
        assertTrue(BenchmarkAnalyzer.text(r).contains("configuration LABEL conflicts"))
    }
    @Test fun fullyMissingPowerAndFpsNeverZero() {
        val input=(0..8).map {p(1_000_000L+it*2_000L,null)}
        val r=BenchmarkAnalyzer.analyze(sess(),input)
        assertNull(r.energy.wh)
        assertNull(r.frames.presentedFps)
        assertTrue(BenchmarkAnalyzer.comparison(r,r).contains("unavailable"))
    }
}
