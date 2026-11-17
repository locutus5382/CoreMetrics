package com.locutus5382.coremetrics.telemetry

import com.locutus5382.coremetrics.analysis.SessionRegimeAnalyzer
import com.locutus5382.coremetrics.monitor.BatteryDesignCapacity
import org.junit.Assert.*
import org.junit.Test

class DeviceTelemetryPortabilityTest {
    @Test fun cpuPolicyDiscoveryAndRelatedCoresAreBoundedAndHardwareNeutral() {
        assertEquals(listOf("policy0","policy4","policy11"),
            CpuPolicyTelemetry.names(listOf("policy11","policy4","policy0",
                "policy4","cpufreq0","policy4000")))
        assertEquals(listOf(0,1,2,3),
            CpuPolicyTelemetry.relatedCpus("0 1 2 3"))
        assertEquals(emptyList<Int>(),
            CpuPolicyTelemetry.relatedCpus("0 1 malicious"))
        assertEquals(16,CpuPolicyTelemetry.names(
            (0..50).map{"policy"+it}).size)
    }

    @Test fun newCapFormatIncludesAllDiscoveredPoliciesButNoPrimeCoreGuess() {
        val old=SessionRegimeAnalyzer.key(
            "policy3 max 2.05 GHz · policy7 max 2.09 GHz")
        assertEquals("P3:2.05|P7:2.09",old)
        val newer=SessionRegimeAnalyzer.key(
            "CPU_POLICIES: policy0 max 1.40 GHz · policy4 max 2.10 GHz")
        assertEquals("POLICIES:0=1.40|4=2.10",newer)
        assertTrue(SessionRegimeAnalyzer.label(newer)
            .contains("p4:2.10"))
        assertEquals(SessionRegimeAnalyzer.ReferenceCaps.UNKNOWN,
            SessionRegimeAnalyzer.referenceCaps(newer))
        assertEquals("POLICIES:?",SessionRegimeAnalyzer.key(
            "CPU_POLICIES: INCOMPLETE"))
        assertEquals("POLICIES:?",SessionRegimeAnalyzer.key(
            "CPU_POLICIES: unreadable"))
    }

    @Test fun maliAndKgslParseStrictUtilizationUnitsOnly() {
        assertEquals(71,GpuTelemetryProbe.load("71%","MALI_PERCENT"))
        assertEquals(0,GpuTelemetryProbe.load("0","KGSL_PERCENT"))
        assertEquals(80,GpuTelemetryProbe.load("80 100","KGSL_BUSY_RATIO"))
        assertNull(GpuTelemetryProbe.load("82 busy 100","MALI_PERCENT"))
        assertNull(GpuTelemetryProbe.load("120","MALI_PERCENT"))
        assertNull(GpuTelemetryProbe.load("0.50","MALI_PERCENT"))
        assertNull(GpuTelemetryProbe.load("95","SOME_UNKNOWN_VENDOR"))
        assertEquals(401.0,GpuTelemetryProbe.clockMHz("401000000")!!,0.01)
        assertEquals(401.0,GpuTelemetryProbe.clockMHz("401000")!!,0.01)
        assertNull(GpuTelemetryProbe.clockMHz("0"))
        assertNull(GpuTelemetryProbe.clockMHz("900000000000"))
    }

    @Test fun unreadableGpuSourcesAreUnavailableNotFakeZero() {
        val probe=GpuTelemetryProbe(
            read={_->null},devfreqPaths={emptyList()})
        val r=probe.sample(30_000L)
        assertNull(r.loadPct)
        assertNull(r.frequencyMHz)
        assertEquals("UNAVAILABLE",r.source)
    }

    @Test fun gpuAdapterUsesReadableMaliValueWithoutInventingAdreno() {
        val probe=GpuTelemetryProbe(
            read={path->
                if(path.endsWith("/mali0/device/utilization"))"42"
                else if(path.endsWith("/gpu/cur_freq"))"630000000"
                else null
            },
            devfreqPaths={listOf("/sys/class/devfreq/gpu/cur_freq")}
        )
        val r=probe.sample(100_000L)
        assertEquals(42,r.loadPct)
        assertEquals(630.0,r.frequencyMHz!!,0.01)
        assertTrue(r.source.contains("MALI_PERCENT"))
        assertTrue(r.source.contains("READABLE_NOT_VALIDATED"))
    }

    @Test fun designEnergyIsPlausibleButNotValidatedUsableWh() {
        val e=BatteryDesignCapacity.fromEnergyMicroWh("30800000")
        assertEquals(30.8,e!!.designWh,0.001)
        assertFalse(e.verified)
        val c=BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "5000000","3850000")
        assertEquals(19.25,c!!.designWh,0.01)
        assertFalse(c.verified)
        assertNull(BatteryDesignCapacity.fromEnergyMicroWh("-999"))
        assertNull(BatteryDesignCapacity.fromEnergyMicroWh("0"))
        assertNull(BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "5000000",null))
    }
}
