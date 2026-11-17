package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.analysis.DukoSessionInsight.Metric
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class DukoComparisonInsightTest {
    private fun report(
        id:Long,device:String="Odin 2 Portal",driver:String="T30",
        cpu:Double=52.0,gpu:Double=70.0,power:Double=4.0,
        coverage:Double=98.0,dischargeCoverage:Double=98.0,
        gpuSources:List<String>=listOf("LOAD:KGSL_PERCENT; USER_VALIDATED"),
        batterySources:List<String>=listOf("CURRENT=BATTERY_PROPERTY_CURRENT_NOW; VOLTAGE=BATTERY_CHANGED"),
        name:String="Hades"
    ):DukoSessionInsight.Report {
        val s=SessionStore.Session(
            id=id,startedAt=1_000L,endedAt=501_000L,label=name,
            configuration="Stock",status="COMPLETED",count=101,
            note="Same Hades scene",graphicsDriver=driver,
            resolutionScale="1x",performanceMode="Performance",
            fanMode="Smart",clusterTuneMode="Stock",profileName="Run",
            deviceManufacturer="AYN",deviceModel=device,
            deviceCode="odin2portal",androidApi=33
        )
        val stats=Metric.entries.map {metric ->
            val v=when(metric) {
                Metric.CPU_TEMP->cpu
                Metric.GPU_TEMP->52.0
                Metric.BATTERY_TEMP->35.0
                Metric.CPU_LOAD->43.0
                Metric.GPU_LOAD->gpu
                Metric.BUSIEST->77.0
                Metric.POWER->power
                Metric.GPU_CLOCK->650.0
                Metric.MEMORY_PSI->2.0
            }
            DukoSessionInsight.Stats(metric,101,v,v+2.0,v+4.0,v-3.0,coverage)
        }
        return DukoSessionInsight.Report(
            session=s,formatVersion="DUKO-SESSION-1",appVersion="0.9.9.2",
            firstMs=1_000L,lastMs=501_000L,sampleCount=101,
            sourceSampleCount=101L,cadenceMs=5_000L,gapThresholdMs=15_000L,
            gapCount=0,stats=stats,
            measuredEnergyWh=power*500/3600,validDischargeSeconds=490.0,
            dischargeCoveragePct=dischargeCoverage,weightedDischargeW=power,
            batteryWarmSeconds=0.0,batteryAbove42Seconds=0.0,
            maximumOsThermalStatus=0,events=emptyList(),additionalEvents=0,
            charts=emptyList(),findings=emptyList(),cautions=emptyList(),
            gpuSources=gpuSources,batterySources=batterySources
        )
    }

    @Test fun selectedBaselineAndReorderedSessionsPreserveSignedObservedDeltas() {
        val a=report(11,driver="T30",cpu=50.0,gpu=60.0,power=4.0)
        val b=report(12,driver="StevenMXZ",cpu=54.0,gpu=80.0,power=3.5)
        val r=DukoComparisonInsight.analyze(
            listOf(b,a),11,CompareStudioAnalysis.TestedVariable.DRIVER)
        assertEquals(listOf(12L,11L),r.ids)
        assertEquals(11L,r.baseline.report.session.id)
        val cpu=r.rows.first{it.metric==Metric.CPU_TEMP}
        assertEquals(4.0,cpu.cells[0].deltaP50!!,.001)
        assertEquals(0.0,cpu.cells[1].deltaP50!!,.001)
        val gpu=r.rows.first{it.metric==Metric.GPU_LOAD}
        assertEquals(20.0,gpu.cells[0].deltaP50!!,.001)
        val power=r.rows.first{it.metric==Metric.POWER}
        assertEquals(-0.5,power.cells[0].deltaP50!!,.001)
        assertFalse(r.observations.any{it.contains("winner",ignoreCase=true)})
        assertEquals(DukoComparisonInsight.Confidence.LIMITED,r.overall)
    }

    @Test fun unknownOrDifferentHardwareSuppressesAllBetweenRunDeltas() {
        val r=DukoComparisonInsight.analyze(listOf(
            report(1),report(2,device="Retroid Pocket 5")),1)
        assertEquals(DukoComparisonInsight.Confidence.INSUFFICIENT,r.overall)
        assertTrue(r.rows.all{it.cells[1].deltaP50==null})
        assertTrue(r.warnings.any{it.contains("device",ignoreCase=true)})
        val missing=report(3).let{
            it.copy(session=it.session.copy(deviceModel=null))
        }
        val unknown=DukoComparisonInsight.analyze(listOf(report(1),missing),1)
        assertTrue(unknown.rows.all{it.cells[1].deltaP50==null})
    }

    @Test fun badGpuProvenanceAndMissingPowerDoNotTurnIntoZeroOrComparableDeltas() {
        val a=report(1)
        val b=report(2,gpuSources=listOf(
            "LOAD:KGSL_PERCENT; READABLE_NOT_VALIDATED"),
            batterySources=emptyList())
        val r=DukoComparisonInsight.analyze(listOf(a,b),1)
        val gpu=r.rows.first{it.metric==Metric.GPU_LOAD}
        assertNotNull(gpu.cells[1].p50)
        assertNull(gpu.cells[1].deltaP50)
        assertFalse(gpu.cells[1].comparable)
        val power=r.rows.first{it.metric==Metric.POWER}
        assertNull(power.cells[1].deltaP50)
        assertTrue(r.rows.first{it.metric==Metric.CPU_TEMP}.cells[1].comparable)
    }

    @Test fun lowCoverageBlocksOnlyAffectedRows() {
        val r=DukoComparisonInsight.analyze(
            listOf(report(1),report(2,coverage=45.0,dischargeCoverage=30.0)),1)
        assertTrue(r.rows.all{it.cells[1].deltaP50==null})
        assertTrue(r.rows.any{it.cells[1].reason?.contains("80%")==true})
    }

    @Test fun twoToFiveDistinctSessionsAndSelectedBaselineAreRequired() {
        val all=(1L..5L).map{report(it)}
        assertEquals(5,DukoComparisonInsight.analyze(all,3).runs.size)
        assertTrue(runCatching{
            DukoComparisonInsight.analyze(listOf(all.first()),1)
        }.isFailure)
        assertTrue(runCatching{
            DukoComparisonInsight.analyze(all+report(6),1)
        }.isFailure)
        assertTrue(runCatching{
            DukoComparisonInsight.analyze(listOf(all[0],all[0]),1)
        }.isFailure)
        assertTrue(runCatching{
            DukoComparisonInsight.analyze(all,100)
        }.isFailure)
    }

    @Test fun declaredVariableDoesNotEraseOtherConfounders() {
        val x=report(1)
        val y=report(2,driver="StevenMXZ").let {
            it.copy(session=it.session.copy(resolutionScale="0.75x"))
        }
        val r=DukoComparisonInsight.analyze(
            listOf(x,y),1,CompareStudioAnalysis.TestedVariable.DRIVER)
        assertTrue(r.warnings.any{it.contains("Render scale differs")})
        assertFalse(r.warnings.any{it.contains("Graphics driver differs outside")})
    }
}
