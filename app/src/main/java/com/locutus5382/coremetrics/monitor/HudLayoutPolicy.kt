package com.locutus5382.coremetrics.monitor

/**
 * HUD presentation only. The three layouts all consume exactly the same
 * recording snapshot and the same ONE WindowManager telemetry root.
 * No frame loop, sensor polling, foreground service, permissions or DB work.
 */
internal object HudLayoutPolicy {
    const val PILL="pill"
    const val GLASS_CARD="glass_card"
    const val RIBBON="ribbon"

    /** Historical "pro" is the old rounded-card choice: migrate harmlessly. */
    fun normalize(value:String?):String = when(value) {
        PILL->PILL
        "pro",GLASS_CARD->GLASS_CARD
        RIBBON->RIBBON
        else->PILL
    }

    fun card(value:String)=normalize(value)==GLASS_CARD
    fun ribbon(value:String)=normalize(value)==RIBBON

    /**
     * Horizontal ribbon prefers one physical line on wide landscape screens.
     * On narrow screens a two-row fallback keeps ALL requested metrics visible;
     * numbers must not be silently ellipsized away.
     */
    fun ribbonTwoRows(metricCount:Int,availableWidthDp:Int):Boolean =
        metricCount>4 && availableWidthDp<700

    fun divider(layout:String,index:Int,total:Int,twoRows:Boolean=false):String {
        if(index==0)return ""
        return when(normalize(layout)) {
            GLASS_CARD -> if(index%2==0)"\n" else "    ·    "
            RIBBON -> if(twoRows && index%2==0)"\n" else "    │    "
            else -> if(total>2 && index%2==0)"\n" else " · "
        }
    }

    /** Character sizing affects the HUD only, never the recorder. */
    fun nominalTextSp(layout:String,size:String):Float {
        val scale=when(size) {"small"->0.82f;"large"->1.20f;else->1f}
        return (if(card(layout))12.5f else if(ribbon(layout))11.2f else 12f)*scale
    }
}
