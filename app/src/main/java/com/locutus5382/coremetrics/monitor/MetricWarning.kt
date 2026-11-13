package com.locutus5382.coremetrics.monitor

internal object MetricWarning {
    fun hystereticTemperature(
        previous:Int,value:Double?,yellow:Double,red:Double
    ):Int {
        if(value==null || !value.isFinite())return 0
        return when(previous){
            2 -> if(value>=red-2.0)2 else if(value>=yellow-2.0)1 else 0
            1 -> if(value>=red)2 else if(value>=yellow-2.0)1 else 0
            else -> temperature(value,yellow,red)
        }
    }
    fun temperature(value:Double?,yellow:Double,red:Double):Int=when {
        value==null || !value.isFinite()->0
        value>=red->2
        value>=yellow->1
        else->0
    }
}
