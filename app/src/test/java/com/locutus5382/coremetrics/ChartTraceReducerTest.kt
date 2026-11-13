package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.ChartTraceReducer
import com.locutus5382.coremetrics.analysis.DiagnosticTimeCursor
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import org.junit.Assert.*
import org.junit.Test

class ChartTraceReducerTest {
    private fun p(i:Int,v:Double?=50.0,segment:Int=0) =
        ChartTraceReducer.Sample(i*2_000L,v,segment)

    @Test fun largeSignalIsBoundedButRetainsInstantPeaksAndValleys() {
        val data=(0..9_999).map {
            p(it,when(it){3132->97.0;4681->-10.0;else->40.0})
        }
        val result=ChartTraceReducer.reduce(data,0,19_998_000,700,6_000)
        assertTrue("Pixel reduction should bound chart work",result.size<=2_800)
        assertTrue(result.any{it.value==97.0 && it.timeMs==6_264_000L})
        assertTrue(result.any{it.value== -10.0 && it.timeMs==9_362_000L})
        assertEquals(data.first(),result.first())
        assertEquals(data.last(),result.last())
        assertEquals(result,result.sortedBy{it.timeMs})
    }

    @Test fun explicitMissingSensorAndLongGapsNeverBecomeConnected() {
        val samples=listOf(p(0,10.0),p(1,11.0),p(2,null),
            p(3,14.0),p(4,15.0),p(60,20.0),p(61,21.0))
        val reduced=ChartTraceReducer.reduce(samples,0,130_000,3,6_000)
        assertTrue(reduced.count{it.value==null}>=2)
        assertTrue(reduced.any{it.timeMs==6_000L && it.value==14.0})
        assertTrue(reduced.any{it.timeMs==120_000L && it.value==20.0})
        val between=reduced.indexOfFirst{it.timeMs==120_000L && it.value==20.0}
        assertTrue(reduced.take(between).last().value==null)
    }

    @Test fun segmentSwitchForcesBreakEvenAtSameTimestampBucket() {
        val samples=listOf(p(0,30.0),p(1,32.0),
            p(2,60.0,1),p(3,62.0,1))
        val reduced=ChartTraceReducer.reduce(samples,0,100_000,1,6_000)
        assertTrue(reduced.any{it.value==null})
        assertEquals(listOf(30.0,32.0,60.0,62.0),
            reduced.mapNotNull{it.value})
    }

    @Test fun invalidNumbersAndOutOfRangePointsStayOutOfChart() {
        val x=listOf(p(0,1.0),p(1,Double.NaN),p(2,3.0),
            p(3,Double.POSITIVE_INFINITY),p(4,4.0))
        val out=ChartTraceReducer.reduce(x,2_000,8_000,10,5_000)
        assertFalse(out.any{it.value?.isNaN()==true ||
            it.value?.isInfinite()==true})
        assertTrue(out.count{it.value==null}>0)
    }

    @Test fun diagnosticCursorUsesRealSourceAndRespectsSegmentGaps() {
        val readings=listOf(
            SessionDiagnosticsAnalyzer.Reading(1_000,45.0,null,0),
            SessionDiagnosticsAnalyzer.Reading(6_000,47.0,null,0),
            SessionDiagnosticsAnalyzer.Reading(40_000,65.0,null,1),
            SessionDiagnosticsAnalyzer.Reading(45_000,64.0,null,1))
        assertEquals(47.0,DiagnosticTimeCursor.nearest(readings,6_500,3_000)?.value)
        assertNull(DiagnosticTimeCursor.nearest(readings,20_000,20_000))
        assertNull(DiagnosticTimeCursor.nearest(readings,0,10_000))
        assertNull(DiagnosticTimeCursor.nearest(readings,46_000,10_000))
        assertEquals(65.0,DiagnosticTimeCursor.nearest(readings,40_000,0)?.value)
    }
}
