package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class NormalizedEnergyTest {
    private fun session(id:Long) = SessionStore.Session(id,100_000L,106_000L,
        "Session $id","Underclocking","COMPLETED",4)
    private fun p(t:Long,power:Double?,status:String="DISCHARGING; BATTERY_NET_ONLY")=
        SessionStore.Point(t,42.0,38.0,30.0,20.0,4.0,
            power,null,null,batteryPowerStatus=status)
    @Test fun integratedAndNormalizedUseExactlySameValidIntervals() {
        val r=BenchmarkAnalyzer.analyze(session(1),
            listOf(p(100_000,3.0),p(102_000,5.0),p(104_000,3.0)))
        assertEquals(16.0/3600.0,r.energy.wh!!,1e-8)
        assertEquals(4.0,r.energy.weightedMeanW!!,1e-8)
        assertEquals(4000.0/60.0,r.energy.milliWhPerMinute!!,1e-8)
    }
    @Test fun chargingOrMissingDoesNotCreateFalseZero() {
        val r=BenchmarkAnalyzer.analyze(session(2),
            listOf(p(100_000,null),p(102_000,5.0,"CHARGING"),p(104_000,null)))
        assertNull(r.energy.wh)
        assertNull(r.energy.weightedMeanW)
        assertNull(r.energy.milliWhPerMinute)
    }
}
