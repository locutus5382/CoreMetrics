package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.SegmentAnalysis
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Metric
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class SegmentAnalysisTest {
    private fun session()=SessionStore.Session(7L,1_000_000L,1_800_000L,
        "Metroid","Stock","COMPLETED",100,note="Boss fight")
    private fun p(i:Int,cpu:Double?=45.0,gpu:Double?=40.0,
        watts:Double?=3.0,status:String="DISCHARGING; BATTERY_NET_ONLY") =
        SessionStore.Point(1_000_000L+i*5_000L,cpu,gpu,30.0,35.0,
            5.6,watts,null,null,batteryPowerStatus=status,busiestCorePct=54.0)

    @Test fun inclusiveAbsoluteRangesNeverResetElapsedOrigin() {
        val source=(0..60).map{p(it)}
        val full=SegmentAnalysis.fullRange(source)!!
        assertEquals(1_000_000L,full.startMs)
        assertEquals(1_300_000L,full.endMs)
        val range=SegmentAnalysis.Range(1_065_000L,1_135_000L)
        val result=SegmentAnalysis.create(session(),source,range)
        assertEquals(15,result.points.size)
        assertEquals(1_000_000L,result.sourceOriginMs)
        assertTrue(SegmentAnalysis.textReport(result).contains("65.00 to 135.00 seconds"))
        assertEquals(65_000L,result.points.first().timeMs-result.sourceOriginMs)
    }

    @Test fun stableTemperatureNearMedianIsHundredPercent() {
        val points=(0..20).map{p(it,cpu=45.0,gpu=41.0)}
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        val cpu=result.stability.first{it.title=="CPU temperature"}
        assertEquals(100.0,cpu.percentOfValidTime!!,.001)
        assertEquals(0.0,cpu.meanAbsoluteStep!!,.0001)
        assertEquals(100.0,cpu.measuredSeconds,.001)
    }

    @Test fun timeWeightedStabilitySkipsRecordingGap() {
        val times=listOf(0L,2_000L,4_000L,6_000L,8_000L,
            18_000L,20_000L,22_000L,24_000L,26_000L,28_000L,
            30_000L,32_000L,34_000L,36_000L,38_000L,40_000L)
        val points=times.mapIndexed{i,t->
            p(i,cpu=if(i<=4)60.0 else 45.0).copy(timeMs=1_000_000L+t)
        }
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        val s=result.stability.first{it.title=="CPU temperature"}
        assertTrue(s.percentOfValidTime!! > 70.0)
        assertTrue(s.percentOfValidTime!! < 100.0)
        assertEquals(30.0,s.measuredSeconds,.01)
    }

    @Test fun chargingAndMissingPowerCannotContributeToStability() {
        val points=(0..24).map{
            if(it in 8..12)p(it,watts=-2.0,status="CHARGING_OR_EXTERNAL_POWER")
            else p(it)
        }
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        val power=result.stability.first{it.title=="Discharge power"}
        assertEquals(0.0,power.meanAbsoluteStep!!,.001)
        assertEquals(90.0,power.measuredSeconds,.001)
        assertEquals(100.0,power.percentOfValidTime!!,.001)
        assertNull(result.diagnostics.remainingHours)
    }

    @Test fun gapDoesNotCreateVolatilityOrHeatTransition() {
        val points=(0..10).map{p(it,cpu=45.0)}+
            (100..110).map{p(it,cpu=55.0)}
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        val cpu=result.stability.first{it.title=="CPU temperature"}
        assertEquals(100.0,cpu.measuredSeconds,.001)
        assertEquals(0.0,cpu.meanAbsoluteStep!!,.001)
        assertEquals(1,result.diagnostics.gapCount)
        assertEquals(0.0,cpu.percentOfValidTime!!,.001)
    }

    @Test fun shortAndSparseSelectionProducesUnavailableNotZero() {
        val points=(0..4).map{p(it)}
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        assertFalse(result.validForReport)
        assertNull(result.stability.first().percentOfValidTime)
        assertThrows(IllegalArgumentException::class.java) {
            SegmentAnalysis.textReport(result)
        }
    }

    @Test fun missingPowerProducesUnavailable() {
        val points=(0..25).map{p(it,watts=null)}
        val result=SegmentAnalysis.create(session(),points,SegmentAnalysis.fullRange(points)!!)
        val power=result.stability.first{it.title=="Discharge power"}
        assertNull(power.percentOfValidTime)
        assertNull(power.meanAbsoluteStep)
        assertEquals(0,power.comparedSteps)
        assertEquals(0.0,power.measuredSeconds,.001)
        assertNull(result.diagnostics.distribution(Metric.DISCHARGE_W).median)
    }

    @Test fun originalPointsRemainUntouched() {
        val points=(0..30).map{p(it)}
        val range=SegmentAnalysis.Range(1_025_000L,1_095_000L)
        assertEquals(15,SegmentAnalysis.window(points,range).size)
        assertEquals(31,points.size)
        assertThrows(IllegalArgumentException::class.java) {
            SegmentAnalysis.Range(1_100_000L,1_100_000L)
        }
    }
}
