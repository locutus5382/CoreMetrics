package com.locutus5382.coremetrics.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Static UI materials, identical observational recorder and overlay for all.
 * The Base test APK locks Frosted, Themes APK exposes all four.
 */
internal enum class SurfaceFinish(
    val key:String,val displayName:String,val description:String
) {
    FROSTED("frosted","Frosted Glass","Original Cobalt & Solar ambient glass"),
    CRYSTAL_AERO("crystal_aero","Crystal Aero",
        "Clear optical panes with specular rims over static Cobalt & Solar ribbons"),
    SOLAR_PULSE("solar_pulse","Solar Pulse",
        "Modern vivid Cobalt, Orange and Gold control surfaces"),
    COBALT_MATRIX("cobalt_matrix","Cobalt Matrix",
        "Precision telemetry, segmented ticks and instrument-like dark panels");

    companion object {
        fun parse(key:String?):SurfaceFinish=when(key) {
            "liquid" -> CRYSTAL_AERO // previous saved choice, intentionally migrated
            "tactical" -> COBALT_MATRIX
            else -> values().firstOrNull{it.key==key}?:FROSTED
        }
    }
}

internal val LocalSurfaceFinish=staticCompositionLocalOf {
    SurfaceFinish.FROSTED
}
