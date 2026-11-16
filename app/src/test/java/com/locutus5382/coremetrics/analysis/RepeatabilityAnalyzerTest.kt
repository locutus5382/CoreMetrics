package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class RepeatabilityAnalyzerTest {
    private fun session(id:Long,count:Int=42,driver:String?="Custom: T30")=
        SessionStore.Session(id,id*1_000_000L,id*1_000_000L+205_000L,
            "NFS","Stock","COMPLETED",count,
            note="NFS same race",graphicsDriver=driver,
            resolutionScale="Handheld 1x",performanceMode="Performance",
            fanMode="Smart",clusterTuneMode="Stock",profileName="T30 repeat")
    private fun run(id:Long,watts:Double,driver:String?="Custom: T30",
        samples:Int=42,totalCount:Int=samples):RepeatabilityAnalyzer.Run {
        val s=session(id,totalCount,driver)
        val data=(0 until samples).map{i->
            val t=s.startedAt+i*5_000L
            SessionStore.Point(timeMs=t,cpu=55.0+id,gpu=45.0+id,
                cpuLoad=35.0,gpuLoad=40.0,ram=5.0,
                batteryWatts=watts,sfFps=null,appPackage="com.test.game",
                batteryPowerStatus="DISCHARGING; BATTERY_NET_ONLY")
        }
        return RepeatabilityAnalyzer.Run(s,data)
    }
    @Test fun runLevelSpreadUsesIndependentRunsNotIndividualTicks() {
        val r=RepeatabilityAnalyzer.analyze(listOf(
            run(1,4.8),run(2,5.1),run(3,4.9)))
        val watts=r.measurements.first{it.unit=="W"}
        assertEquals(3,watts.runCount)
        assertEquals(4.9,watts.medianOfRuns!!,0.001)
        assertEquals(4.8,watts.minimumRun!!,0.001)
        assertEquals(5.1,watts.maximumRun!!,0.001)
        assertEquals(0.3,watts.spread!!,0.001)
        assertTrue(r.cautions.any{it.contains("do not prove identical")})
        assertEquals(3,r.eligiblePowerRuns)
    }
    @Test fun missingPowerOrConfoundingTagsAreCautionsNotZeros() {
        val a=run(1,4.8)
        val b=run(2,5.1,driver="Custom: T32")
            .let{it.copy(points=it.points.map{p->
                p.copy(batteryPowerStatus="CHARGING_OR_EXTERNAL_POWER")
            })}
        val r=RepeatabilityAnalyzer.analyze(listOf(a,b))
        assertEquals(1,r.eligiblePowerRuns)
        assertEquals(1,r.measurements.first{it.unit=="W"}.runCount)
        assertTrue(r.cautions.any{it.contains("Graphics driver differs")})
        assertTrue(r.cautions.any{it.contains("unavailable watts")})
    }
    @Test fun incompleteSessionMustBeRejectedInsteadOfSilentlyTruncated() {
        assertTrue(runCatching {
            RepeatabilityAnalyzer.analyze(listOf(
                run(1,4.0),run(2,4.3,samples=20,totalCount=42)))
        }.isFailure)
    }
    @Test fun mismatchedQuickProfileCannotBeClaimedRepeatedExperiment() {
        val s=run(2,4.2)
        val renamed=s.copy(session=s.session.copy(profileName="T32"))
        assertTrue(runCatching{
            RepeatabilityAnalyzer.analyze(listOf(run(1,4.0),renamed))
        }.isFailure)
    }
    @Test fun separateRacesRequireTwoToFiveRuns() {
        assertTrue(runCatching{RepeatabilityAnalyzer.analyze(listOf(run(1,4.0)))}.isFailure)
        assertTrue(runCatching{
            RepeatabilityAnalyzer.analyze((1L..6L).map{run(it,4.0)})
        }.isFailure)
    }
}
