package com.locutus5382.coremetrics.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * UI-only finishes. Cobalt/Solar Frosted is the product default.
 * These flags never change the WindowManager HUD or recording service.
 */
internal enum class SurfaceFinish(val key:String,val displayName:String,
    val description:String) {
    FROSTED("frosted","Frosted Glass","Original CoreMetrics glass · unchanged"),
    LIQUID("liquid","Liquid Glass","Brighter hairline reflections, static specular gradients"),
    TACTICAL("tactical","Tactical Flat","Opaque slate surfaces and strong contrast");

    companion object {
        fun parse(key:String?):SurfaceFinish =
            values().firstOrNull{it.key==key}?:FROSTED
    }
}

internal val LocalSurfaceFinish=staticCompositionLocalOf {
    SurfaceFinish.FROSTED
}
