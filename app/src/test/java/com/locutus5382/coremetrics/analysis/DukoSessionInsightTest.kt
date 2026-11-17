package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class DukoSessionInsightTest {
    private fun session(count:Int)=SessionStore.Session(
        19L,1_000_000L,1_000_000L+(count-1)*5_000L,
        "NFS","Stock","COMPLETED",count,note="Repeated NFS race",
        graphicsDriver="Custom T30",deviceManufacturer="AYN",
        deviceModel="Odin 2 Portal",androidApi=33
    )
    private fun p(t:Long,cpu:Double?=58.0,gpu:Double?=48.0,
        watts:Double?=4.0,
        status:String="DISCHARGING; BATTERY_NET_ONLY",
        battery:Double?=35.0):SessionStore.Point =
        SessionStore.Point(timeMs=t,cpu=cpu,gpu=gpu,cpuLoad=45.0,
            gpuLoad=52.0,ram=4.5,batteryWatts=watts,sfFps=null,
            appPackage="app.eden",batteryPowerStatus=status,
            batteryTemp=battery,batteryPct=71,
            observedCpuLimits="CPU_POLICIES: policy0 max 1.8 GHz · policy4 max 2.5 GHz",
            gpuTelemetrySource="LOAD:KGSL_PERCENT; CLOCK:KGSL_CLOCK; READABLE_NOT_VALIDATED",
            batteryPowerSource="CURRENT=BATTERY_PROPERTY_CURRENT_NOW; VOLTAGE=ACTION_BATTERY_CHANGED; SNAPSHOT_AGE_UNVERIFIED"
        )
    private fun report(points:List<SessionStore.Point>,cadence:Long=5000):
        DukoSessionInsight.Report {
        val bounds=SessionStore.ReportBounds(points.size.toLong(),
            points.first().timeMs,points.last().timeMs)
        val acc=DukoSessionInsight.Accumulator(session(points.size),bounds,cadence)
        points.forEach(acc::accept)
        return acc.finish()
    }

    @Test fun fullSessionExactQuantilesAndIntegratedWhHaveCorrectUnits() {
        val rows=(0..50).map{i->p(1_000_000L+i*5000L,cpu=50.0+i*.2)}
        val r=report(rows)
        assertEquals(51,r.sampleCount)
        assertEquals(100.0,r.dischargeCoveragePct,.001)
        assertEquals(4.0,r.weightedDischargeW!!,.001)
        assertEquals(4.0*250.0/3600.0,r.measuredEnergyWh!!,.00001)
        assertEquals(55.0,r.stat(DukoSessionInsight.Metric.CPU_TEMP).p50!!,.001)
        assertEquals(59.5,r.stat(DukoSessionInsight.Metric.CPU_TEMP).p95!!,.001)
        assertEquals(0,r.gapCount)
        assertTrue(r.findings.isNotEmpty())
    }

    @Test fun gapsAndChargingIntervalsAreNeverIntegratedAsDischarge() {
        val rows=(0..6).map{p(1_000_000L+it*5000L)}+
            (0..6).map{
                p(1_140_000L+it*5000L,watts=if(it==0)4.0 else -5.0,
                    status=if(it==0)"DISCHARGING" else "CHARGING_OR_EXTERNAL_POWER")
            }
        val r=report(rows)
        assertEquals(1,r.gapCount)
        assertEquals(30.0,r.validDischargeSeconds,.001)
        assertEquals(4.0*30.0/3600.0,r.measuredEnergyWh!!,.00001)
        assertTrue(r.dischargeCoveragePct<30.0)
        assertTrue(r.events.any{it.title=="Recording gap"})
    }

    @Test fun missingPowerAndMissingGpuRemainUnavailableNotZero() {
        val rows=(0..7).map{i->
            p(1_000_000L+i*5000L,watts=null,gpu=null)
        }
        val r=report(rows)
        assertNull(r.weightedDischargeW)
        assertNull(r.measuredEnergyWh)
        assertNull(r.stat(DukoSessionInsight.Metric.GPU_TEMP).p50)
        assertEquals(0,r.stat(DukoSessionInsight.Metric.GPU_TEMP).count)
        assertTrue(r.charts.first{it.metric==DukoSessionInsight.Metric.GPU_TEMP}
            .points.isEmpty())
    }

    @Test fun longRecordingBeyondOld12kPreviewPreservesLastPeakWithBoundedChart() {
        val n=12_025
        val start=1_000_000L
        val acc=DukoSessionInsight.Accumulator(session(n),
            SessionStore.ReportBounds(n.toLong(),start,start+(n-1)*2000L),2000L)
        for(i in 0 until n) {
            acc.accept(p(start+i*2000L,
                cpu=if(i==12_024)116.0 else 43.0))
        }
        val result=acc.finish()
        assertEquals(n,result.sampleCount)
        assertEquals(116.0,result.stat(DukoSessionInsight.Metric.CPU_TEMP).peak!!,.001)
        assertEquals(116.0,result.charts.first{
            it.metric==DukoSessionInsight.Metric.CPU_TEMP
        }.points.maxOf{it.value},.001)
        assertTrue(result.charts.all{it.points.size<=560})
        assertEquals(0,result.gapCount)
        assertEquals(result.sourceSampleCount,result.sampleCount.toLong())
    }

    @Test fun fifteenSecondsAbove42CreatesApplicationAdvisoryNotCriticalCpuWarning(){
        val r=report((0..8).map{i->p(1_000_000L+i*5000L,battery=43.0)})
        assertTrue(r.events.any{it.title.contains("BAT T orange HOT")})
        assertTrue(r.batteryAbove42Seconds>=35.0)
        assertTrue(r.cautions.any{it.contains("thermal zones")})
        assertFalse(r.findings.any{it.text.contains("proven throttling")})
    }

    @Test fun capSignatureAndMemoryStatusCannotBridgeUnobservedGap() {
        val early=(0..4).map { i ->
            p(1_000_000L+i*5000L).copy(
                observedCpuLimits="CPU_POLICIES: policy0 max 2.0 GHz",
                memoryPsiSomeAvg10=11.0)
        }
        val later=(0..4).map {i->
            p(1_150_000L+i*5000L).copy(
                observedCpuLimits="CPU_POLICIES: policy0 max 1.4 GHz",
                memoryPsiSomeAvg10=11.0)
        }
        val r=report(early+later)
        assertEquals(1,r.gapCount)
        assertFalse(r.events.any{it.title=="CPU frequency ceiling changed"})
        assertTrue(r.events.any{it.title=="Recording gap"})
    }

    @Test fun changingSourceCountOrOrderRejectsMisleadingReport() {
        val acc=DukoSessionInsight.Accumulator(session(8),
            SessionStore.ReportBounds(8,1000,36_000),5000)
        (0..5).forEach{acc.accept(p(1000L+it*5000L))}
        assertTrue(runCatching{acc.finish()}.isFailure)
        assertTrue(runCatching{acc.accept(p(20_000L))}.isFailure)
    }
}
