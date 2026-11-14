package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.V08Analytics
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class V08AnalyticsTest {
    private fun s(id:Long)=SessionStore.Session(id,100_000,200_000,
        "Session $id","Stock","COMPLETED",3)
    private fun p(t:Long,core:String?=null,limits:String?=null)=
        SessionStore.Point(t,45.0,40.0,40.0,35.0,5.0,4.0,null,null,
            observedCpuLimits=limits,busiestCore=core)

    @Test fun compareSampleMeansOmitExternalPowerAndInvalidValues() {
        val a=p(0).copy(cpu=45.0,gpu=40.0,batteryWatts=4.0,
            batteryPowerStatus="DISCHARGING; BATTERY_NET_ONLY")
        val b=p(2_000).copy(cpu=50.0,gpu=41.0,batteryWatts=-9.0,
            batteryPowerStatus="CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY")
        val c=p(4_000).copy(cpu=55.0,gpu=42.0,batteryWatts=Double.NaN,
            batteryPowerStatus="DISCHARGING; BATTERY_NET_ONLY")
        val m=V08Analytics.sampleAverages(listOf(a,b,c))
        assertEquals(50.0,m.cpuC!!,1e-5)
        assertEquals(55.0,m.peakCpuC!!,1e-5)
        assertEquals(41.0,m.gpuC!!,1e-5)
        assertEquals(4.0,m.dischargeW!!,1e-5)
        assertNull(V08Analytics.sampleAverages(listOf(b)).dischargeW)
    }

    @Test fun busiestCoreCountsRecordedWinnersOnly() {
        val d=V08Analytics.busiestCores(listOf(p(10,"cpu0"),p(20,"cpu0"),
            p(30,"cpu7"),p(40,null),p(50,"cpu99")))
        assertEquals(3,d.observations)
        assertEquals(2,d.ignored)
        assertEquals(2,d.winners[0].count)
        assertEquals(200.0/3.0,d.winners[0].sharePct,1e-6)
        assertEquals(1,d.winners[7].count)
    }
    @Test fun chronologicalLinkKeepsGapAndIDs() {
        val t=V08Analytics.linkedTimeline(s(8),
            listOf(p(100_000),p(130_000)),s(9),
            listOf(p(154_200),p(296_200)))!!
        assertEquals(24_200L,t.gapMs)
        assertEquals(listOf(8L,9L),t.orderedIds)
        assertFalse(t.overlap)
        assertEquals(100_000L,t.startMs)
        assertEquals(296_200L,t.endMs)
    }
    @Test fun underclockSelectionWithOnlyStockLikeLimitsNeedsReview() {
        val stock="policy3 max 2.80 GHz · policy7 max 3.19 GHz"
        val points=(0..5).map { p(1000L+it*2000,null,stock) }
        val selectedUnderclock=V08Analytics.profile("Underclocking",points)
        assertTrue(selectedUnderclock.conflict)
        assertTrue(selectedUnderclock.note.contains("GPU-only"))
        val custom=V08Analytics.profile("Custom",points)
        assertFalse(custom.conflict)
        val stockSelected=V08Analytics.profile("Stock",points)
        assertFalse(stockSelected.conflict)
        val unknown=V08Analytics.profile("Underclocking",
            listOf(p(0L,null,null)))
        assertFalse(unknown.conflict)
    }

    @Test fun liveProfileWarningsShareRecordedRules() {
        val stock="policy3 max 2.80 GHz · policy7 max 3.19 GHz"
        val medium="policy3 max 2.05 GHz · policy7 max 2.09 GHz"
        val large="policy3 max 1.79 GHz · policy7 max 1.84 GHz"
        assertNull(V08Analytics.liveProfileWarning("Stock",stock))
        assertNotNull(V08Analytics.liveProfileWarning("Stock",medium))
        assertNotNull(V08Analytics.liveProfileWarning("Stock",large))
        val warning=V08Analytics.liveProfileWarning("Underclocking",stock)
        assertTrue(warning!!.contains("GPU-only"))
        assertNull(V08Analytics.liveProfileWarning("Underclocking",medium))
        assertNull(V08Analytics.liveProfileWarning("Custom",large))
        assertNull(V08Analytics.liveProfileWarning("Stock",null))
    }

    @Test fun warmupTrimKeepsAbsoluteTimestampsAndUnfilteredInput() {
        val rows=listOf(p(30_000),p(0),p(59_999),p(29_999),p(60_000))
        val trimmed=V08Analytics.afterWarmUp(rows,30)
        assertEquals(listOf(30_000L,59_999L,60_000L),
            trimmed.map{it.timeMs})
        assertEquals(5,rows.size)
        assertTrue(V08Analytics.afterWarmUp(listOf(p(0),p(29_999)),30).isEmpty())
        assertEquals(rows,V08Analytics.afterWarmUp(rows,0))
    }

    @Test fun unknownAndMultipleCeilingsRetainPhases() {
        val stock="policy3 max 2.80 GHz · policy7 max 3.19 GHz"
        val large="policy3 max 1.79 GHz · policy7 max 1.84 GHz"
        val readings=(0..3).map{p(1000L+it*2000,null,stock)}+
            (0..3).map{p(9000L+it*2000,null,large)}
        val result=V08Analytics.profile("Stock",readings)
        assertEquals(2,result.phases.size)
        assertTrue(result.conflict)
        assertEquals("Stock",result.selected)
        assertEquals("No readable P3/P7 frequency limits",
            V08Analytics.profile("Custom",listOf(p(0))).note)
    }
}
