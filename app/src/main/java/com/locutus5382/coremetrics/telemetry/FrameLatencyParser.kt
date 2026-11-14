package com.locutus5382.coremetrics.telemetry

/**
 * Android SurfaceFlinger layer present timestamps, NOT emulator internal FPS.
 * Parses a single bounded --latency response. Invalid/stale values are missing.
 */
data class FrameEstimate(
    val fps: Double?,
    val frameMs: Double?,
    val frames: Int,
    val status: String
)

object FrameLatencyParser {
    private val whitespace=Regex("""\s+""")
    fun parse(text: String, nowNanos: Long?): FrameEstimate {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (text.contains("Permission Denial",true) || text.contains("permission denied",true))
            return FrameEstimate(null,null,0,"PERMISSION_DENIED")
        if (lines.size < 5) return FrameEstimate(null,null,0,"NO_FRAME_DATA")
        val stamps = lines.drop(1).mapNotNull { line ->
            val c = line.split(whitespace)
            if (c.size != 3) return@mapNotNull null
            c[2].toLongOrNull()?.takeIf { it > 0 && it != Long.MAX_VALUE }
        }.distinct().sorted().takeLast(120)
        if (stamps.size < 4) return FrameEstimate(null,null,stamps.size,"TOO_FEW_FRAMES")
        val latest = stamps.last()
        if (nowNanos != null && (latest > nowNanos + 1_000_000_000L ||
                nowNanos - latest > 10_000_000_000L))
            return FrameEstimate(null,null,stamps.size,"STALE_OR_CLOCK_MISMATCH")
        val deltas = stamps.zipWithNext().map { (a,b) -> b-a }
            .filter { it in 1_000_000L..1_000_000_000L }
        if (deltas.size < 3) return FrameEstimate(null,null,deltas.size,"INVALID_DELTAS")
        val fps = 1_000_000_000.0 / deltas.average()
        if (fps !in 1.0..240.0) return FrameEstimate(null,null,deltas.size,"FPS_OUT_OF_RANGE")
        return FrameEstimate(fps, deltas.sorted()[deltas.size/2]/1_000_000.0,
            deltas.size, "SF_LAYER_PRESENT_RATE_NOT_IN_GAME_FPS")
    }
}
