package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis.Window
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Metric
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.ui.CompareTimeCursor
import org.junit.Assert.*
import org.junit.Test

/** All time windows are relative to the original first sample, never a filtered start. */
class CompareStudioAnalysisTest {
    private fun session(id:Long,status:String="COMPLETED",driver:String?="Mesa",
        scale:String?="1x")=SessionStore.Session(
            id,1_000_000L,1_400_000L,"Metroid","Stock",status,60,
            note="Same test scene",graphicsDriver=driver,resolutionScale=scale)
    private fun p(i:Int,origin:Long=1_000_000L,cpu:Double=45.0,
        gpuLoad:Double=30.0,power:Double?=3.0,
        status:String="DISCHARGING; BATTERY_NET_ONLY",
        cap:String?="policy3 max 2.80 GHz · policy7 max 3.19 GHz") =
        SessionStore.Point(origin+i*5_000L,cpu,40.0,32.0,
            gpuLoad,5.4,power,null,null,observedCpuLimits=cap,
            batteryPowerStatus=status,busiestCorePct=55.0)
    private fun a()=(0..40).map{p(it)}
    private fun b()=(0..40).map{p(it,origin=2_000_000L,cpu=49.0,power=4.0)}
    private fun analyze(
        pa:List<SessionStore.Point> = a(),pb:List<SessionStore.Point> = b(),
        sa:SessionStore.Session=session(1),sb:SessionStore.Session=session(2),
        window:Window=Window(0L,200_000L)
    )=CompareStudioAnalysis.analyze(sa,pa,sb,pb,window)

    @Test fun sameElapsedWindowPreservesOriginalOriginsAndDeltas() {
        val report=analyze(window=Window(10_000L,100_000L))
        assertEquals(19,report.a.points.size)
        assertEquals(19,report.b.points.size)
        assertEquals(1_010_000L,report.a.points.first().timeMs)
        assertEquals(2_010_000L,report.b.points.first().timeMs)
        assertEquals(4.0,CompareStudioAnalysis.delta(
            report.a.median(Metric.CPU_TEMP),report.b.median(Metric.CPU_TEMP))!!,.001)
        assertEquals(1.0,CompareStudioAnalysis.delta(
            report.a.benchmark.energy.weightedMeanW,
            report.b.benchmark.energy.weightedMeanW)!!,.001)
        assertTrue(report.observationsUsable)
        val text=CompareStudioAnalysis.report(report)
        assertTrue(text.contains("10.00 to 100.00 s"))
        assertTrue(text.contains("B - A"))
        assertTrue(text.contains("observational",ignoreCase=true) ||
            text.contains("not a synchronization"))
    }

    @Test fun cadenceMismatchIsExplicitlyFlaggedWithoutChangingBMinusA() {
        val fast=(0..100).map{
            p(it).copy(timeMs=1_000_000L+it*2_000L)
        }
        val slow=b()
        val result=analyze(pa=fast,pb=slow,window=Window(0,200_000L))
        assertTrue(result.warnings.any{it.contains("cadences differ")})
        assertEquals("LIMITED COMPARABILITY",result.evidenceLabel)
        assertEquals(4.0,CompareStudioAnalysis.delta(
            result.a.median(Metric.CPU_TEMP),result.b.median(Metric.CPU_TEMP))!!,.001)
    }
    @Test fun forceSelectionInsideSharedMeasuredDuration() {
        val longA=(0..60).map{p(it)}
        val shortB=(0..16).map{p(it,origin=2_000_000L)}
        assertEquals(80_000L,CompareStudioAnalysis.sharedDuration(longA,shortB))
        assertThrows(IllegalArgumentException::class.java) {
            analyze(longA,shortB,window=Window(20_000L,120_000L))
        }
        val result=analyze(longA,shortB,window=Window(15_000L,80_000L))
        assertEquals(14,result.a.points.size)
        assertEquals(14,result.b.points.size)
    }

    @Test fun doNotShiftScrubCursorAfterRemovingEarlySamples() {
        val cropped=(0..20).map{p(it)}.filter{it.timeMs>=1_040_000L}
        val correct=CompareTimeCursor.nearest(cropped,50_000L,
            originalOriginMs=1_000_000L)
        assertEquals(1_050_000L,correct!!.timeMs)
        val noData=CompareTimeCursor.nearest(cropped,10_000L,
            originalOriginMs=1_000_000L)
        assertNull(noData)
    }

    @Test fun interruptedAndMismatchedWorkloadsAreWarningNotWinners() {
        val pb=(0..40).map{p(it,origin=2_000_000L,cpu=38.0,gpuLoad=9.0)}
        val result=analyze(pb=pb,sb=session(2,status="INTERRUPTED"))
        assertEquals("LIMITED COMPARABILITY",result.evidenceLabel)
        assertTrue(result.warnings.any{it.contains("interrupted")})
        assertTrue(result.warnings.any{it.contains("GPU load")})
        val text=CompareStudioAnalysis.report(result)
        assertTrue(text.contains("LIMITED COMPARABILITY"))
        assertFalse(text.contains("WINNER"))
    }

    @Test fun mixedPowerCannotBeClaimedComparable() {
        val pb=(0..40).map{
            if(it in 8..20)p(it,origin=2_000_000L,
                power=-1.0,status="CHARGING_OR_EXTERNAL_POWER")
            else p(it,origin=2_000_000L,power=4.0)
        }
        val result=analyze(pb=pb)
        assertTrue(result.warnings.any{it.contains("discharge")})
        assertEquals("LIMITED COMPARABILITY",result.evidenceLabel)
        val t=CompareStudioAnalysis.report(result)
        assertTrue(t.contains("Power"))
    }

    @Test fun partialWindowDoesNotPretendItHasEvidence() {
        val result=analyze(window=Window(0,20_000L))
        assertFalse(result.observationsUsable)
        assertEquals("INSUFFICIENT DATA",result.evidenceLabel)
        assertThrows(IllegalArgumentException::class.java) {
            CompareStudioAnalysis.report(result)
        }
    }

    @Test fun missingMetadataAndCapTransitionsAreReported() {
        val pb=(0..40).map{
            p(it,origin=2_000_000L,
                cap=if(it<15)"policy3 max 2.80 GHz · policy7 max 3.19 GHz"
                    else "policy3 max 2.05 GHz · policy7 max 2.09 GHz")
        }
        val result=analyze(pb=pb,sb=session(2,driver=null,scale=null))
        assertTrue(result.warnings.any{it.contains("metadata")})
        assertTrue(result.warnings.any{it.contains("ceilings changed")})
        assertEquals(2,result.b.observedCaps.size)
        assertTrue(CompareStudioAnalysis.report(result).contains("Observed CPU limits B:"))
    }

    @Test fun gapsPreventFalseEnergyCoverage() {
        val pa=(0..8).map{p(it)}+(35..44).map{p(it)}
        val pb=(0..44).map{p(it,origin=2_000_000L)}
        val result=analyze(pa=pa,pb=pb,window=Window(0,220_000L))
        assertEquals(1,result.a.benchmark.quality.gapCount)
        assertTrue(result.a.benchmark.energy.coveragePct<80.0)
        assertTrue(result.warnings.any{it.contains("discharge coverage")})
    }
}
