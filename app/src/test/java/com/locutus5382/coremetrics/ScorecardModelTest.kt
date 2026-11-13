package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.analysis.ScorecardModel
import com.locutus5382.coremetrics.analysis.SegmentAnalysis
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class ScorecardModelTest {
    private fun session(id:Long=1,status:String="COMPLETED") =
        SessionStore.Session(id,1_000_000L,1_360_000L,
            "Metroid","Stock",status,73,
            note="Repeatable course",graphicsDriver="Mesa",resolutionScale="1x")
    private fun p(i:Int,origin:Long=1_000_000L,cpu:Double?=46.0,
        gpu:Double?=39.0,gpuLoad:Double?=35.0,watts:Double?=3.0) =
        SessionStore.Point(origin+i*5_000L,cpu,gpu,28.0,gpuLoad,
            5.2,watts,null,"org.yuzu",
            batteryPowerStatus="DISCHARGING; BATTERY_NET_ONLY",
            busiestCorePct=54.0)
    private fun raw(origin:Long=1_000_000L)=(0..72).map{p(it,origin)}

    @Test fun fullSessionPngDataUsesOriginalStartAndSameStatistics() {
        val all=raw()
        val bundle=SegmentAnalysis.create(session(),all,
            SegmentAnalysis.fullRange(all)!!)
        val card=ScorecardModel.single(bundle)
        assertEquals(ScorecardModel.Type.SINGLE,card.type)
        assertEquals(0L,card.graphStartMs)
        assertEquals(360_000L,card.graphDurationMs)
        assertEquals(all.size,card.series[0].samples.size)
        assertEquals("00:00 - 06:00 of original recording",card.range)
        assertTrue(card.rows.any{it.label.contains("temperature") && it.left.contains("46.0")})
        assertTrue(card.footnotes.any{it.contains("not a measure of FPS")})
    }

    @Test fun selectedSegmentPngAndTxtShareTheExactSourceWindow() {
        val all=raw()
        val selection=SegmentAnalysis.Range(1_085_000L,1_205_000L)
        val bundle=SegmentAnalysis.create(session(),all,selection)
        val card=ScorecardModel.single(bundle)
        assertEquals("01:25 - 03:25 of original recording",card.range)
        assertEquals(85_000L,card.graphStartMs)
        assertEquals(205_000L,card.graphDurationMs)
        assertEquals(25,card.series[0].samples.size)
        assertEquals(85_000L,card.series[0].samples.first().elapsedMs)
        assertTrue(SegmentAnalysis.textReport(bundle).contains("85.00 to 205.00"))
    }

    @Test fun missingMetricsAndGapsStayMissingNotFabricatedZero() {
        val all=raw().mapIndexed {i,x ->
            if(i in 20..25)x.copy(cpu=null,gpu=null,batteryWatts=null) else x
        }
        val bundle=SegmentAnalysis.create(session(),all,SegmentAnalysis.fullRange(all)!!)
        val card=ScorecardModel.single(bundle)
        assertNull(card.series[0].samples[22].value)
        assertNull(card.series[1].samples[22].value)
        assertTrue(card.series[0].gapLimitMs>0L)
        assertFalse(card.rows.first().left.startsWith("0.0"))
    }

    @Test fun shortRecordingAndBadWindowCannotExport() {
        val all=(0..4).map{p(it)}
        val bundle=SegmentAnalysis.create(session(),all,SegmentAnalysis.fullRange(all)!!)
        assertThrows(IllegalArgumentException::class.java) {
            ScorecardModel.single(bundle)
        }
    }

    @Test fun abDeltasAlwaysBMinusAAndNoWinnerClaims() {
        val a=raw()
        val b=(0..72).map{p(it,origin=2_000_000L,cpu=50.0,gpu=41.0,watts=4.0)}
        val result=CompareStudioAnalysis.analyze(session(1),a,session(2),b,
            CompareStudioAnalysis.Window(60_000L,180_000L))
        val card=ScorecardModel.comparison(result)
        assertEquals(ScorecardModel.Type.AB,card.type)
        assertEquals(60_000L,card.graphStartMs)
        assertEquals(180_000L,card.graphDurationMs)
        val cpu=card.rows.first{it.label=="CPU temp P50"}
        assertEquals("46.0 °C",cpu.left)
        assertEquals("50.0 °C",cpu.right)
        assertEquals("+4.0 °C",cpu.delta)
        val power=card.rows.first{it.label=="Mean battery discharge"}
        assertEquals("+1.00 W",power.delta)
        assertTrue(card.footnotes.any{it.contains("not")||it.contains("NOT")})
        assertFalse(card.rows.any{it.label.contains("Winner",ignoreCase=true)})
    }

    @Test fun interruptedAndGpuMismatchedRunsPrintComparabilityCautions() {
        val b=(0..72).map {p(it,origin=2_000_000L,gpuLoad=9.0)}
        val result=CompareStudioAnalysis.analyze(session(1),raw(),
            session(2,status="INTERRUPTED"),b,
            CompareStudioAnalysis.Window(0L,120_000L))
        val card=ScorecardModel.comparison(result)
        assertEquals("LIMITED COMPARABILITY",card.badge)
        assertTrue(card.badgesAreCaution)
        assertTrue(card.footnotes.any{it.contains("Caution")})
        assertTrue(result.warnings.size>0)
    }

    @Test fun invalidAndAbsentNumericValuesAreLabeledUnavailable() {
        assertEquals("Unavailable",ScorecardModel.formatted(null," C"))
        assertEquals("Unavailable",ScorecardModel.formatted(Double.NaN," W"))
        assertEquals("Unavailable",ScorecardModel.formatted(Double.POSITIVE_INFINITY," W"))
        assertEquals("3.25 W",ScorecardModel.formatted(3.247," W",2))
    }
}
