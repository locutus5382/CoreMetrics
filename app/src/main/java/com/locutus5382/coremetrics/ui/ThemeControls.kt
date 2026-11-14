package com.locutus5382.coremetrics.ui

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A single track/handle palette for post-session range selectors and HUD
 * settings. This is a UI-only function; it is not used by the floating HUD.
 * Original Frosted uses Material3's default Slider colors.
 */
@Composable
internal fun cmSliderColors():SliderColors = when(LocalSurfaceFinish.current) {
    SurfaceFinish.FROSTED -> SliderDefaults.colors()
    SurfaceFinish.CRYSTAL_AERO -> SliderDefaults.colors(
        thumbColor=Color(0xFFE6F9FF),
        activeTrackColor=Color(0xFF8DE8FF),
        inactiveTrackColor=Color(0xFF245584))
    SurfaceFinish.SOLAR_PULSE -> SliderDefaults.colors(
        thumbColor=Color(0xFFFFC700),
        activeTrackColor=Color(0xFFFF6B00),
        inactiveTrackColor=Color(0xFF2457C5))
    SurfaceFinish.COBALT_MATRIX -> SliderDefaults.colors(
        thumbColor=Color(0xFFFFC700),
        activeTrackColor=Color(0xFF6FD6FC),
        inactiveTrackColor=Color(0xFF29425B))
}
