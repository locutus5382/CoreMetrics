package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class V081ProfileQualityTest {
    private fun point(t:Long, limits:String?) = SessionStore.Point(
        t,45.0,38.0,22.0,40.0,5.0,4.0,null,null,
        observedCpuLimits=limits,
        batteryPowerStatus="DISCHARGING; BATTERY_NET_ONLY"
    )
    @Test fun underclockCpuCeilingsWarnButDoNotRewriteManualLabel() {
        val stock="policy3 max 2.80 GHz · policy7 max 3.19 GHz"
        val rows=(0..40).map { point(1000L+it*2000L,stock) }
        val session=SessionStore.Session(8L,1000L,81000L,
            "Session 8","Underclocking","COMPLETED",rows.size)
        val report=BenchmarkAnalyzer.analyze(session,rows)
        assertTrue(report.quality.labelConflict)
        assertEquals("Underclocking",report.session.configuration)
        assertTrue(report.quality.reasons.any{it.contains("GPU-only")})
    }
    @Test fun unavailableCeilingsNeverCountAsMismatch() {
        val rows=(0..40).map { point(1000L+it*2000L,null) }
        val session=SessionStore.Session(9L,1000L,81000L,
            "Session 9","Underclocking","COMPLETED",rows.size)
        val report=BenchmarkAnalyzer.analyze(session,rows)
        assertFalse(report.quality.labelConflict)
    }
}
