package com.locutus5382.coremetrics.monitor

/** Display-only color feedback. These are USER INTERFACE thresholds, never
 * SoC's guaranteed throttling/safety limits. No sampling or hardware control.
 */
internal object HudSeverity {
    enum class Level { NORMAL, ELEVATED, HIGH }
    fun thermal(value:Double?,previous:Level=Level.NORMAL,
        yellow:Double=65.0,red:Double=80.0):Level {
        if(value==null || !value.isFinite())return Level.NORMAL
        // A 2°C release margin prevents single-sample color flicker.
        return when {
            value>=red || (previous==Level.HIGH && value>=red-2.0)->Level.HIGH
            value>=yellow || (previous==Level.ELEVATED && value>=yellow-2.0) ->
                Level.ELEVATED
            else->Level.NORMAL
        }
    }
    fun usage(value:Int?,previous:Level=Level.NORMAL):Level = when {
        value==null || value !in 0..100 -> Level.NORMAL
        value>=90 || (previous==Level.ELEVATED && value>=85) -> Level.ELEVATED
        else->Level.NORMAL
    }
    fun ram(lowMemory:Boolean,available:Long,threshold:Long,
        previous:Level=Level.NORMAL):Level {
        if(lowMemory)return Level.HIGH
        if(threshold<=0L || available<0L)return Level.NORMAL
        val ratio=available.toDouble()/threshold
        return when {
            ratio<=1.1 ->Level.HIGH
            ratio<=1.5 || (previous==Level.ELEVATED && ratio<=1.7) ->
                Level.ELEVATED
            else ->Level.NORMAL
        }
    }
}
