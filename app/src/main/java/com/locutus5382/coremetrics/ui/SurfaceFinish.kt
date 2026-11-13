package com.locutus5382.coremetrics.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Main-app visual treatments only. Native in-game HUD keeps its stable
 * WindowManager implementation for reliability and Android touch safety. */
internal enum class SurfaceFinish(
    val key:String,val displayName:String,val description:String
) {
    FROSTED("frosted","Frosted Glass",
        "Original Cobalt & Solar soft translucent glass, with interactive light glints"),
    CRYSTAL_AERO("crystal_aero","Crystal Aero",
        "Clear optical-style surfaces with bright specular rims and static silk ribbons"),
    SOLAR_PULSE("solar_pulse","Solar Pulse",
        "Modern opaque solar-orange, gold and cobalt controls with bold readable cards"),
    COBALT_MATRIX("cobalt_matrix","Cobalt Matrix",
        "Dark navy instrument console, radial ticks and cyan/orange telemetry geometry");
    companion object {
        // Legacy Liquid/Tactical are intentionally replaced, not carried forward.
        fun parse(key:String?):SurfaceFinish =
            values().firstOrNull{it.key==key}?:FROSTED
    }
}
internal val LocalSurfaceFinish=staticCompositionLocalOf{SurfaceFinish.FROSTED}
