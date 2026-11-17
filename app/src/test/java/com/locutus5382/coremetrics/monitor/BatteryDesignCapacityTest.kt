package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class BatteryDesignCapacityTest {
    @Test fun designEnergyUsesDocumentedMicroWattHourUnits() {
        val value=BatteryDesignCapacity.fromEnergyMicroWh("30800000")!!
        assertEquals(30.8,value.designWh,0.0001)
        assertFalse(value.verified)
        assertTrue(value.provenance.contains("UNVERIFIED"))
        assertNull(BatteryDesignCapacity.fromEnergyMicroWh("unavailable"))
    }

    @Test fun chargeCapacityRequiresActualNominalPackVoltage() {
        val value=BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "8000000","3850000")!!
        assertEquals(30.8,value.designWh,0.0001)
        assertTrue(value.provenance.contains("VOLTAGE_NOMINAL"))
        assertFalse(value.verified)
        // Missing nominal voltage must not silently substitute cutoff
        // voltage or a fixed 3.85V assumption on another handheld.
        assertNull(BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "8000000",null))
        assertNull(BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "8000000","unavailable"))
    }

    @Test fun invalidDesignCapacityIsNotTreatedAsRuntimeEnergy() {
        assertNull(BatteryDesignCapacity.fromEnergyMicroWh("0"))
        assertNull(BatteryDesignCapacity.fromEnergyMicroWh("-1000000"))
        assertNull(BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            "8000000","1000"))
    }
}
