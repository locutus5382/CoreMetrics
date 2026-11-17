package com.locutus5382.coremetrics.monitor

/**
 * Read-only *design* capacity candidates. Linux power_supply units:
 * energy_full_design = microWh, charge_full_design = microAh,
 * voltage_min_design = microV. Never equate these to usable current Wh,
 * and never use unvalidated values for remaining runtime estimates.
 */
object BatteryDesignCapacity {
    data class Candidate(val designWh:Double,val provenance:String,
        val verified:Boolean=false)

    fun fromEnergyMicroWh(raw:String?):Candidate? {
        val microWh=raw?.trim()?.toLongOrNull() ?:return null
        val wh=microWh/1_000_000.0
        return wh.takeIf{it.isFinite() && it in 3.0..200.0}
            ?.let{Candidate(it,"SYSFS_ENERGY_FULL_DESIGN_UNVERIFIED")}
    }
    fun fromChargeMicroAhAndNominalMicroV(
        chargeRaw:String?,nominalVoltageRaw:String?
    ):Candidate? {
        val charge=chargeRaw?.trim()?.toLongOrNull()?:return null
        val voltage=nominalVoltageRaw?.trim()?.toLongOrNull()?:return null
        if(charge !in 200_000L..40_000_000L ||
            voltage !in 2_500_000L..25_000_000L)return null
        val wh=charge.toDouble()*voltage/1_000_000_000_000.0
        return wh.takeIf{it.isFinite() && it in 3.0..200.0}
            ?.let{Candidate(it,"SYSFS_CHARGE_FULL_DESIGN_AND_VOLTAGE_NOMINAL_UNVERIFIED")}
    }
}
