package com.locutus5382.coremetrics.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-only visual finishes. In-game WindowManager HUD, recording engine, DB,
 * polling and diagnostic math are intentionally outside this design system.
 *
 * Existing keys stay stable so saved v0.8.x appearance selections migrate
 * naturally; historic finish names are only displayed under new labels.
 */
internal enum class SurfaceFinish(
    val key:String,val displayName:String,val description:String
) {
    FROSTED("frosted","Frosted Glass",
        "Original Cobalt & Solar frosted glass. Preserves the familiar finish and interaction."),
    CRYSTAL_AERO("crystal_aero","True Glass",
        "Layered clear optical panes, subtle specular refraction and bright cobalt/cyan edges."),
    SOLAR_PULSE("solar_pulse","Modern Colorful",
        "Saturated cobalt, cyan and solar orange controls with confident, readable surfaces."),
    COBALT_MATRIX("cobalt_matrix","Technical Cockpit",
        "Matte navy, compact instrument frames, ruler ticks and monospace diagnostic identity.");

    companion object {
        fun parse(key:String?):SurfaceFinish =
            values().firstOrNull { it.key==key } ?: FROSTED
    }
}

internal data class SurfacePalette(
    val top:Color,
    val bottom:Color,
    val border:Color,
    val borderSoft:Color,
    val navSelected:Color,
    val navIdle:Color,
    val accent:Color,
    val secondary:Color,
    val foreground:Color=Color(0xFFF2F7FF),
    val muted:Color=Color(0xFFBFDAED),
    val sharpCorners:Boolean=false,
    val emphasisFill:Color
)

/** Pure and allocation-light theme table, not hardware-dependent. */
internal val SurfaceFinish.palette:SurfacePalette get()=when(this) {
    SurfaceFinish.FROSTED -> SurfacePalette(
        top=Color(0x2BFFFFFF),bottom=Color(0x33091534),
        border=Color(0xB293C5FD),borderSoft=Color(0x619EC6ED),
        navSelected=Color(0xE7245367),navIdle=Color(0x42192335),
        accent=Color(0xFF93C5FD),secondary=Color(0xFFFFC700),
        foreground=Color(0xFFECF6FC),muted=Color(0xFFC3D7ED),
        emphasisFill=Color(0xFF2563EB))
    SurfaceFinish.CRYSTAL_AERO -> SurfacePalette(
        top=Color(0x27FFFFFF),bottom=Color(0x140B2C60),
        border=Color(0xF1DDF6FF),borderSoft=Color(0x7191CCFA),
        navSelected=Color(0xA3167BBE),navIdle=Color(0x380A2D52),
        accent=Color(0xFF9DE9FF),secondary=Color(0xFFFFB967),
        emphasisFill=Color(0xFF2563EB))
    SurfaceFinish.SOLAR_PULSE -> SurfacePalette(
        top=Color(0xFF142C52),bottom=Color(0xFF071C35),
        border=Color(0xFF4B90D9),borderSoft=Color(0xFF35597A),
        navSelected=Color(0xFFDE5E09),navIdle=Color(0xFF122F59),
        accent=Color(0xFFFFC700),secondary=Color(0xFF7BE6F8),
        emphasisFill=Color(0xFFFF6B00))
    SurfaceFinish.COBALT_MATRIX -> SurfacePalette(
        top=Color(0xFF111B2C),bottom=Color(0xFF080F20),
        border=Color(0xFF5B94C4),borderSoft=Color(0xFF2D445E),
        navSelected=Color(0xFF173D55),navIdle=Color(0xFF101E30),
        accent=Color(0xFF70DBFA),secondary=Color(0xFFFFC700),
        foreground=Color(0xFFEDF8FF),muted=Color(0xFFB6CFE2),
        sharpCorners=true,emphasisFill=Color(0xFF2162C5))
}

internal val LocalSurfaceFinish=staticCompositionLocalOf { SurfaceFinish.FROSTED }
