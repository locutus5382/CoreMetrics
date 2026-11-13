package com.locutus5382.coremetrics.monitor

internal object MetricWarning {
    fun temperature(value:Double?,yellow:Double,red:Double):Int=when {
        value==null || !value.isFinite()->0
        value>=red->2
        value>=yellow->1
        else->0
    }
}
