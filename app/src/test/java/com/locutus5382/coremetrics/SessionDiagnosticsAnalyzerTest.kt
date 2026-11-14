package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer.Metric
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class SessionDiagnosticsAnalyzerTest {
    private fun session() = SessionStore.Session(
        123L,1_000_000L,1_120_000L,"Metroid","Stock","COMPLETED",25
    )
    private fun point(
        i:Int, cpu:Double?=45.0, gpuLoad:Double?=30.0,
        busiest:Double?=54.0, batteryW:Double?=4.0,
        status:String="DISCHARGING; BATTERY_NET_ONLY",batteryPct:Int?=69,
        clocks:String?="policy0 2.02 GHz · policy3 2.80 GHz · policy7 3.19 GHz",
        cap:String?="policy3 max 2.80 GHz · policy7 max 3.19 GHz"
    ):SessionStore.Point = SessionStore.Point(
        timeMs=1_000_000L+i*5_000L,
        cpu=cpu,gpu=40.0,cpuLoad=30.0,gpuLoad=gpuLoad,ram=5.2,
        batteryWatts=batteryW,sfFps=null,appPackage="dev.eden",
        observedCpuLimits=cap,busiestCorePct=busiest,
        batteryPowerStatus=status,busiestCore="cpu3",
        batteryTemp=28.0,batteryPct=batteryPct,cpuClocks=clocks,gpuMHz=550.0
    )
    private fun diagnostics(points:List<SessionStore.Point>) =
        SessionDiagnosticsAnalyzer.analyze(
            points,BenchmarkAnalyzer.analyze(session(),points)
        )

    @Test fun interpolatedP95PeakAndTimestampAwareRollingMedian() {
        val points=(0..20).map { point(it,cpu=35.0+it) }
        val result=diagnostics(points)
        val cpu=result.distribution(Metric.CPU_TEMP)
        assertEquals(21,cpu.observations)
        assertEquals(45.0,cpu.median!!,.001)
        assertEquals(54.0,cpu.p95!!,.001)
        assertEquals(55.0,cpu.peak!!,.001)
        assertEquals(1_100_000L,cpu.peakTimeMs)
        assertEquals(49.0,cpu.final60Median!!,.001)
    }

    @Test fun gapResetsMedianAndChartSegment() {
        val first=(0..12).map { point(it) }
        val later=(50..64).map { point(it,cpu=60.0) }
        val result=diagnostics(first+later)
        val cpu=result.distribution(Metric.CPU_TEMP)
        assertEquals(1,result.gapCount)
        assertNotEquals(cpu.readings[12].segment,cpu.readings[13].segment)
        assertNull(cpu.readings[13].rolling60)
        assertEquals(60.0,cpu.final60Median!!,.001)
    }

    @Test fun validBatteryRuntimeUsesIntegratedDischargeOnly() {
        val points=(0..24).map{point(it)}
        val r=diagnostics(points)
        assertEquals(4.0,r.typicalDischargeW!!,.0001)
        assertEquals(7.7,r.fullChargeHours!!,.0001)
        assertEquals(30.8*.69/4.0,r.remainingHours!!,.0001)
        assertEquals(69,r.measuredBatteryPct)
        assertTrue(r.runtimeNote.contains("Approximate"))
    }

    @Test fun chargingAndMissingPowerNeverBecomeGamingRuntime() {
        val mixed=(0..24).map{
            if(it==12)point(it,batteryW=-3.0,status="CHARGING_OR_EXTERNAL_POWER")
            else point(it)
        }
        val result=diagnostics(mixed)
        assertNull(result.remainingHours)
        assertNull(result.fullChargeHours)
        val power=result.distribution(Metric.DISCHARGE_W)
        assertEquals(24,power.observations)
        assertEquals(4.0,power.median!!,.001)
        assertTrue(result.runtimeNote.contains("not exclusively discharging"))
    }

    @Test fun sustainedPressureUsesContiguousValidTimeNotPeakCount() {
        val points=(0..24).map{point(it,busiest=92.0,gpuLoad=94.0)}
        val r=diagnostics(points)
        assertEquals(120.0,r.gpuPressure.observedSeconds,.01)
        assertEquals(120.0,r.gpuPressure.highSeconds,.01)
        assertEquals(100.0,r.gpuPressure.highSharePct,.01)
        assertEquals(120.0,r.busiestCorePressure.longestHighSeconds,.01)
        val gaps=listOf(point(0,busiest=90.0),point(1,busiest=90.0),
            point(50,busiest=90.0),point(51,busiest=90.0))
        assertEquals(10.0,diagnostics(gaps).busiestCorePressure.highSeconds,.01)
    }

    @Test fun clockParserDifferentiatesInstantClockFromCpuCaps() {
        val r=diagnostics((0..20).map {
            if(it<10)point(it)
            else point(it,
                clocks="policy0 1.02 GHz · policy3 1.79 GHz · policy7 1.84 GHz",
                cap="policy3 max 1.79 GHz · policy7 max 1.84 GHz")
        })
        assertEquals(1,r.observedCapChanges)
        assertEquals(2.80,r.distribution(Metric.P3_CLOCK).peak!!,.001)
        assertEquals(3.19,r.distribution(Metric.P7_CLOCK).peak!!,.001)
        assertEquals(2.05,SessionDiagnosticsAnalyzer.clockGHz(
            "policy0 1.02 GHz · policy3 2.05 GHz · policy7 1.84 GHz",3)!!,.001)
        assertNull(SessionDiagnosticsAnalyzer.clockGHz("unavailable",7))
        assertNull(SessionDiagnosticsAnalyzer.clockGHz("policy3 max 2.05 GHz",7))
    }

    @Test fun insufficientOrAbsentDataIsUnavailableNeverZero() {
        val points=(0..3).map { point(it,cpu=null,gpuLoad=null,
            busiest=null,batteryW=null,batteryPct=null,clocks=null,cap=null) }
        val r=diagnostics(points)
        assertNull(r.distribution(Metric.CPU_TEMP).median)
        assertNull(r.distribution(Metric.CPU_TEMP).final60Median)
        assertNull(r.initialCpuSlopeCPerMin)
        assertNull(r.finalCpuSlopeCPerMin)
        assertNull(r.remainingHours)
        assertEquals(0.0,r.gpuPressure.observedSeconds,.001)
        assertEquals(0,r.observedCapReadings)
    }

    @Test fun thermalSlopeIsReportedOnlyForContiguousSufficientWindows() {
        val continuous=(0..44).map {point(it,cpu=40.0+it*.1)}
        val good=diagnostics(continuous)
        assertNotNull(good.initialCpuSlopeCPerMin)
        assertNotNull(good.finalCpuSlopeCPerMin)
        assertTrue(good.initialCpuSlopeCPerMin!!>0)
        val split=(0..9).map{point(it,cpu=40.0)} +
            (25..44).map{point(it,cpu=55.0)}
        val gaps=diagnostics(split)
        assertNull(gaps.initialCpuSlopeCPerMin)
    }
    @Test fun exportedTextUsesReadableDecimalsAndNAInsteadOfNull() {
        val r=diagnostics((0..30).map{point(it,cpu=41.123456789+it*.03)})
        val txt=SessionDiagnosticsAnalyzer.text(r)
        assertTrue(txt.contains("°C/min"))
        assertFalse(txt.contains("null"))
        assertFalse(txt.contains("41.123456789"))
        assertTrue(txt.contains("valid seconds"))
        val empty=diagnostics((0..3).map {
            point(it,cpu=null,gpuLoad=null,batteryW=null,batteryPct=null)
        })
        assertTrue(SessionDiagnosticsAnalyzer.text(empty).contains("n/a"))
    }
    @Test fun thermalRegressionDoesNotBridgeMissingSensorSamples() {
        val points=(0..30).map {
            point(it,cpu=if(it==12)null else 41.0+it*.1)
        }
        val r=diagnostics(points)
        assertNull(r.initialCpuSlopeCPerMin)
        assertNotNull(r.distribution(Metric.CPU_TEMP).median)
        assertNotEquals(
            r.distribution(Metric.CPU_TEMP).readings[11].segment,
            r.distribution(Metric.CPU_TEMP).readings[12].segment)
    }
}
