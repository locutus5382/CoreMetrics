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
