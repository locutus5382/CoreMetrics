package com.locutus5382.coremetrics.monitor

/**
 * v0.9.9.6: five *visual* HUDs, one sampler and one WindowManager owner.
 * External preferences are normalized to canonical theme IDs. No theme is
 * allowed to change recording/SQLite/session, refresh rate, or permission.
 */
internal object HudLayoutPolicy {
    const val STRIKER="striker"
    const val GHOST="ghost"
    const val STRIP="horizontal_strip"
    const val TACTICAL="compact_card"
    const val DUKO="duko_lens"
    val themes=listOf(STRIKER,GHOST,STRIP,TACTICAL,DUKO)

    // Old IDs normalize to their closest NEW design, not the rejected UI.
    fun normalize(value:String?):String=when(value) {
        STRIKER,"pill"->STRIKER
        GHOST->GHOST
        STRIP,"ribbon"->STRIP
        TACTICAL,"glass_card","pro"->TACTICAL
        DUKO->DUKO
        else->STRIKER
    }
    fun label(value:String):String=when(normalize(value)) {
        STRIKER->"The Striker"
        GHOST->"The Ghost HUD"
        STRIP->"The Horizontal Strip"
        TACTICAL->"The Compact Card HUD"
        else->"DUKO Lens"
    }
    fun description(value:String):String=when(normalize(value)) {
        STRIKER->"Angular, grouped telemetry and dedicated controls"
        GHOST->"Borderless, near-invisible inline metrics"
        STRIP->"Cinema-band telemetry anchored near a screen edge"
        TACTICAL->"Dense thermals, utilization and resources panels"
        else->"Monospaced laboratory lens with bracket framing"
    }
    fun card(value:String)=normalize(value)==TACTICAL
    fun ribbon(value:String)=normalize(value)==STRIP
    fun lens(value:String)=normalize(value)==DUKO
    fun borderless(value:String)=normalize(value)==GHOST
    fun ribbonTwoRows(metricCount:Int,availableWidthDp:Int):Boolean=
        metricCount>4 && availableWidthDp<700
    fun divider(layout:String,index:Int,total:Int,twoRows:Boolean=false):String {
        if(index==0)return ""
        return when(normalize(layout)){
            TACTICAL->if(index%2==0)"\n" else "  /  "
            STRIP->if(twoRows && index%3==0)"\n" else "  |  "
            GHOST->"  |  "
            DUKO->"\n"
            STRIKER->if(total>3 && index%2==0)"\n" else "   "
            else->"  "
        }
    }
    fun nominalTextSp(layout:String,size:String):Float {
        val scale=when(size){"small"->.82f;"large"->1.18f;else->1f}
        val base=when(normalize(layout)) {
            STRIKER->12.8f
            GHOST->12.1f
            STRIP->10.9f
            TACTICAL->12.3f
            DUKO->13.0f
            else->12.0f
        }
        return base*scale
    }
    /**
     * 42 C / 80 C are configurable-looking but currently fixed app UI
     * *advisories*, NOT validated device thermal limits or thermal throttling.
     * Never infer thermal causality from a temperature threshold.
     */
    fun advisoryBand(cpuTempC:Double?):Int=when {
        cpuTempC==null || !cpuTempC.isFinite()->0
        cpuTempC>80.0->2
        cpuTempC>=42.0->1
        else->0
    }
    /**
     * A deliberate gesture toward an outer edge triggers collapse. It must
     * both approach the edge and move far enough to avoid accidental docking
     * from normal short drags. The lock path never evaluates this policy.
     */
    fun shouldCollapseOnRelease(
        left:Int,top:Int,hudWidth:Int,screenWidth:Int,deltaX:Float,
        deltaY:Float,thresholdPx:Int,edgePx:Int
    ):Boolean {
        if(hudWidth<=0 || screenWidth<=0)return false
        val towardsLeft=deltaX<=-thresholdPx &&
            left<=edgePx
        val towardsRight=deltaX>=thresholdPx &&
            left+hudWidth>=screenWidth-edgePx
        val horizontal=kotlin.math.abs(deltaX)>
            kotlin.math.abs(deltaY)*1.15f
        return horizontal && (towardsLeft || towardsRight)
    }
}
