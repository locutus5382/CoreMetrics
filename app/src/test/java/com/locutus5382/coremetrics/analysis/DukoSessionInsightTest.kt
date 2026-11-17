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

    @Test fun sustainedHighUtilizationIsAQualifiedPatternNotAFpsVerdict() {
        // Existing 2-minute rule: >=95% of eligible readings >=95% load.
        // It is system telemetry, not an attribution to game bottlenecks.
        val start=1_000_000L
        val rows=(0..30).map{i ->
            p(start+i*5_000L).copy(
                gpuLoad=if(i==0)50.0 else 97.0,
                busiestCorePct=96.0)
        }
        val result=report(rows)
        assertTrue(result.events.any {
            it.title=="GPU sustained high utilization" &&
                it.evidence==DukoSessionInsight.Evidence.PATTERN
        })
        assertTrue(result.events.any {
            it.title=="Busiest core sustained high utilization" &&
                it.evidence==DukoSessionInsight.Evidence.PATTERN
        })
        assertTrue(result.findings.any {it.text.contains("does not establish game FPS")})
        assertFalse(result.findings.any {it.text.contains("bottleneck proven")})
    }

    @Test fun missingGpuLoadCannotProduceSustainedGpuAlert() {
        val rows=(0..30).map{i ->
            p(1_000_000L+i*5_000L).copy(gpuLoad=null)
        }
        val result=report(rows)
        assertFalse(result.events.any {it.title.startsWith("GPU sustained")})
        assertNull(result.stat(DukoSessionInsight.Metric.GPU_LOAD).p50)
    }

    @Test fun highLoadRequiresAtLeastNinetyFivePercentWindowCoverage() {
        // Three unavailable observations out of 25 across a 120s window.
        // An alert must not be inferred even though all valid GPU readings
        // are 98%: coverage is below the agreed 95% threshold.
        val rows=(0..24).map{i ->
            p(1_000_000L+i*5_000L).copy(
                gpuLoad=if(i in setOf(3,11,19))null else 98.0,
                busiestCorePct=35.0)
        }
        val result=report(rows)
        assertFalse(result.events.any { it.title.startsWith("GPU sustained") })
    }

    @Test fun denseImportedUtilizationSamplesGrowPrimitiveRingWithoutDroppingEvents() {
        // 720 observations 200 ms apart keep 600+ samples within the last
        // 120 s, forcing the primitive ring beyond its initial 64 entries.
        val n=720
        val start=1_000_000L
        val bounds=SessionStore.ReportBounds(n.toLong(),start,
            start+(n-1)*200L)
        val acc=DukoSessionInsight.Accumulator(session(n),bounds,500L)
        repeat(n) { i ->
            acc.accept(p(start+i*200L).copy(
                gpuLoad=97.0,busiestCorePct=96.0))
        }
        val r=acc.finish()
        assertEquals(n,r.sampleCount)
        assertEquals(0,r.gapCount)
        assertEquals(97.0,r.stat(DukoSessionInsight.Metric.GPU_LOAD).p50!!,.001)
        assertTrue(r.events.any{it.title=="GPU sustained high utilization"})
        assertTrue(r.events.any{
            it.title=="Busiest core sustained high utilization"})
    }

    @Test fun extremaSpikesAndChronologicalChartOrderSurviveBucketAllocationOptimization() {
        val time=1_000_000L
        val rows=listOf(0L,1L,2L,3L,5_000L,10_000L,15_000L,20_000L)
            .mapIndexed { i,offset ->
                p(time+offset,cpu=when(i) {
                    1->20.0
                    2->120.0
                    else->55.0
                })
            }
        val r=report(rows)
        val points=r.charts.first{
            it.metric==DukoSessionInsight.Metric.CPU_TEMP
        }.points
        assertTrue(points.any{it.value==20.0})
        assertTrue(points.any{it.value==120.0})
        assertEquals(120.0,
            r.stat(DukoSessionInsight.Metric.CPU_TEMP).peak!!,.001)
        assertTrue(points.zipWithNext().all{it.first.offsetMs<=it.second.offsetMs})
    }

    @Test fun cachedCpuPolicyParsingStillReportsActualCapTransitions() {
        val stock="policy3 max 2.80 GHz policy7 max 3.19 GHz"
        val limited="policy3 max 1.79 GHz policy7 max 1.84 GHz"
        val rows=(0..17).map { i ->
            val raw=when {
                i<6 -> stock
                i<12 -> limited
                else -> stock
            }
            p(1_000_000L+i*5000L).copy(observedCpuLimits=raw)
        }
        val r=report(rows)
        val changes=r.events.filter{it.title=="CPU frequency ceiling changed"}
        assertEquals(2,changes.size)
        assertEquals(0,r.gapCount)
        assertTrue(changes.all{it.detail.contains("Observed cap")})
    }

    @Test fun changingSourceCountOrOrderRejectsMisleadingReport() {
        val acc=DukoSessionInsight.Accumulator(session(8),
            SessionStore.ReportBounds(8,1000,36_000),5000)
        (0..5).forEach{acc.accept(p(1000L+it*5000L))}
        assertTrue(runCatching{acc.finish()}.isFailure)
        assertTrue(runCatching{acc.accept(p(20_000L))}.isFailure)
    }
    @Test fun sequentialComparisonQuantileScratchNeverCarriesPriorRunValues() {
        val scratch=DukoSessionInsight.QuantileScratch()
        val bounds=SessionStore.ReportBounds(13L,1_000_000L,1_060_000L)
        fun build(cpu:Double,gpu:Double?):DukoSessionInsight.Report {
            val acc=DukoSessionInsight.Accumulator(
                session(13),bounds,5_000L,scratch)
            repeat(13) { i ->
                acc.accept(p(1_000_000L+i*5000L,cpu=cpu,gpu=gpu))
            }
            return acc.finish()
        }
        val first=build(55.0,75.0)
        val second=build(88.0,null)
        val third=build(33.0,42.0)
        assertEquals(55.0,first.stat(DukoSessionInsight.Metric.CPU_TEMP).p50!!,.001)
        assertEquals(75.0,first.stat(DukoSessionInsight.Metric.GPU_TEMP).p95!!,.001)
        assertEquals(88.0,second.stat(DukoSessionInsight.Metric.CPU_TEMP).p95!!,.001)
        assertNull(second.stat(DukoSessionInsight.Metric.GPU_TEMP).p50)
        assertEquals(0,second.stat(DukoSessionInsight.Metric.GPU_TEMP).count)
        assertEquals(33.0,third.stat(DukoSessionInsight.Metric.CPU_TEMP).p50!!,.001)
        assertEquals(42.0,third.stat(DukoSessionInsight.Metric.GPU_TEMP).p95!!,.001)
        // Immutable summary values must remain valid after buffer reuse.
        assertEquals(55.0,first.stat(DukoSessionInsight.Metric.CPU_TEMP).p50!!,.001)
    }

}
