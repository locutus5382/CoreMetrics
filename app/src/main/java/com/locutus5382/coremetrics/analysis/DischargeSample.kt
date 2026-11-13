package com.locutus5382.coremetrics.analysis

/**
 * Battery-terminal discharge measurements suitable for comparisons.
 * External-power/charging and untrusted or implausible readings are not
 * discharge energy data. Keep plots and reports on the same eligibility rule.
 */
internal object DischargeSample {
    fun watts(value: Double?, batteryPowerStatus: String?): Double? =
        value?.takeIf {
            it.isFinite() && it > 0.0 && it < 100.0 &&
                batteryPowerStatus?.startsWith("DISCHARGING") == true
        }
}
