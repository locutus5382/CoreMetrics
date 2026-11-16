package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.telemetry.BatteryPowerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPowerMathTest {
    @Test fun dischargingIsPositivePowerFromBattery() {
        assertEquals(4.2, BatteryPowerMath.signedDischargeWatts(-1_000_000,4200)!!, 0.000001)
    }
    @Test fun chargingIsNegativeNetBatteryPower() {
        assertEquals(-2.1, BatteryPowerMath.signedDischargeWatts(500_000,4200)!!, 0.000001)
    }
    @Test fun mixedSourcesMustNotPretendTheirTimingIsSynchronized() {
        assertEquals(
            "CURRENT=BATTERY_PROPERTY_CURRENT_NOW; VOLTAGE=ACTION_BATTERY_CHANGED; SNAPSHOT_AGE_UNVERIFIED",
            BatteryPowerMath.source(true,true,true,true))
        assertEquals(
            "CURRENT=SYSFS_CURRENT_NOW; VOLTAGE=SYSFS_VOLTAGE_NOW; SNAPSHOT_AGE_UNVERIFIED",
            BatteryPowerMath.source(false,false,true,true))
        assertEquals("UNAVAILABLE",BatteryPowerMath.source(true,true,false,true))
    }
    @Test fun contradictoryCurrentPolarityNeverBecomesDischargeEvidence() {
        org.junit.Assert.assertTrue(BatteryPowerMath.signConflict(950_000L,false))
        org.junit.Assert.assertTrue(BatteryPowerMath.signConflict(-950_000L,true))
        org.junit.Assert.assertFalse(BatteryPowerMath.signConflict(-950_000L,false))
        org.junit.Assert.assertFalse(BatteryPowerMath.signConflict(20_000L,false))
        org.junit.Assert.assertFalse(BatteryPowerMath.signConflict(null,false))
        org.junit.Assert.assertFalse(BatteryPowerMath.signConflict(950_000L,null))
    }

    @Test fun noGuessIfUnitsNotValid() {
        assertNull(BatteryPowerMath.signedDischargeWatts(null,4200))
        assertNull(BatteryPowerMath.signedDischargeWatts(-100_000,4))
        assertNull(BatteryPowerMath.signedDischargeWatts(-30_000_000,4200))
    }
}
