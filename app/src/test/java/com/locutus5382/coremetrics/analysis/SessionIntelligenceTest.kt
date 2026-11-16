package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class SessionIntelligenceTest {
    private fun point(t:Long,core:String?="cpu7",gpu:Double?=30.0,
        busy:Double?=40.0,cap:String?=
        "policy3 max 2.80 GHz · policy7 max 3.19 GHz",
        thermal:Int?=0,psi:Double?=0.0) =
        SessionStore.Point(t,null,null,40.0,gpu,null,null,null,null,
            observedCpuLimits=cap,busiestCorePct=busy,busiestCore=core,
            thermalStatus=thermal,memoryPsiSomeAvg10=psi)

    @Test fun persistentFrequencyCapChangeIsObservedButNotCausallyAttributed() {
        val points=(0..32).map{ i->
            point(i*5_000L,cap=if(i<16)
                "policy3 max 2.80 GHz · policy7 max 3.19 GHz"
                else "policy3 max 1.79 GHz · policy7 max 1.84 GHz")
        }
        val events=SessionIntelligence.analyze(points).events
        val cap=events.firstOrNull{it.kind==SessionIntelligence.Kind.CAP_CHANGED}
        assertNotNull(cap)
        assertEquals(80_000L,cap!!.offsetMs)
        assertFalse(cap.detail.contains("ClusterTune"))
    }

    @Test fun oneSampleCapGlitchDoesNotDeclareAnUnderclock() {
        val points=(0..20).map{i->
            point(i*5_000L,cap=if(i==9)
                "policy3 max 1.79 GHz · policy7 max 1.84 GHz"
                else "policy3 max 2.80 GHz · policy7 max 3.19 GHz")
        }
        assertFalse(SessionIntelligence.analyze(points).events.any{
            it.kind==SessionIntelligence.Kind.CAP_CHANGED})
    }

    @Test fun busiestCoreChangeIsPatternNotGameThreadMigration() {
        val points=(0..40).map{i->point(i*5_000L,
            core=if(i<20)"cpu7" else "cpu3")}
        val event=SessionIntelligence.analyze(points).events.firstOrNull{
            it.kind==SessionIntelligence.Kind.CORE_ACTIVITY_SHIFT}
        assertNotNull(event)
        assertEquals(SessionIntelligence.Evidence.PATTERN,event!!.evidence)
        assertTrue(event.detail.contains("unverified"))
    }

    @Test fun sustainedGpuSaturationNeedsTwoMinutesAndValidCoverage() {
        val continuous=(0..30).map{i->point(i*5_000L,gpu=96.0)}
        val event=SessionIntelligence.analyze(continuous).events.firstOrNull{
            it.kind==SessionIntelligence.Kind.SATURATION &&
                it.title.startsWith("GPU")}
        assertNotNull(event)
        val short=(0..18).map{i->point(i*5_000L,gpu=100.0)}
        assertFalse(SessionIntelligence.analyze(short).events.any{
            it.kind==SessionIntelligence.Kind.SATURATION})
        val broken=(0..30).map{i->
            point(i*5_000L+(if(i>16)35_000L else 0L),gpu=100.0)}
        assertFalse(SessionIntelligence.analyze(broken).events.any{
            it.kind==SessionIntelligence.Kind.SATURATION &&
                it.title.startsWith("GPU")})
    }

    @Test fun statusAndMemoryPressureDoNotImplyCpuThrottling() {
        val points=(0..20).map{i->point(i*5_000L,
            thermal=if(i in 4..6)2 else 0,
            psi=if(i in 9..12)12.0 else 0.0)}
        val events=SessionIntelligence.analyze(points).events
        assertTrue(events.any{it.kind==SessionIntelligence.Kind.OS_THERMAL_PRESSURE})
        assertTrue(events.any{it.kind==SessionIntelligence.Kind.MEMORY_PRESSURE})
        assertFalse(events.any{it.title.contains("CPU throttled")})
    }

    @Test fun missingSignalsNeverBecomeZeroOrFabricatedEvents() {
        val points=(0..25).map{i->point(i*5_000L,core=null,gpu=null,
            busy=null,cap=null,thermal=null,psi=null)}
        val r=SessionIntelligence.analyze(points)
        assertTrue(r.events.isEmpty())
        assertTrue(r.periods.isEmpty())
    }
}
