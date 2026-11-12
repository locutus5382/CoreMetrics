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
    @Test fun noGuessIfUnitsNotValid() {
        assertNull(BatteryPowerMath.signedDischargeWatts(null,4200))
        assertNull(BatteryPowerMath.signedDischargeWatts(-100_000,4))
        assertNull(BatteryPowerMath.signedDischargeWatts(-30_000_000,4200))
    }
}
