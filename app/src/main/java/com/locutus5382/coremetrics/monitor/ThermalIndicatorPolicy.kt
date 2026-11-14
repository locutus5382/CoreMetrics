package com.locutus5382.coremetrics.monitor

/**
 * A device-wide platform thermal warning, NOT a CPU/GPU-specific throttle test.
 * Headroom 1.0 corresponds to Android's severe-throttling threshold. The early
 * amber 0.85 threshold is a UI advisory, not a device-defined status boundary.
 * No sensors are read here; consumers provide the recorded values.
 */
internal object ThermalIndicatorPolicy {
    enum class Level { UNKNOWN, NORMAL, WARNING, SEVERE }

    fun level(status: Int?, headroom: Double?): Level {
        val validStatus = status?.takeIf { it in 0..6 }
        val validHeadroom = headroom?.takeIf { it.isFinite() && it >= 0.0 }
        if (validStatus == null && validHeadroom == null) return Level.UNKNOWN
        if ((validStatus ?: -1) >= 3 || (validHeadroom ?: -1.0) >= 1.0)
            return Level.SEVERE
        if ((validStatus ?: -1) >= 1 || (validHeadroom ?: -1.0) >= 0.85)
            return Level.WARNING
        return Level.NORMAL
    }

    fun description(level: Level): String = when(level) {
        Level.UNKNOWN -> "Android device thermal signal unavailable"
        Level.NORMAL -> "No reported Android thermal alert (not proof of no throttling)"
        Level.WARNING -> "Android thermal pressure or headroom approaching the severe limit"
        Level.SEVERE -> "Android severe thermal status or severe headroom threshold reached"
    }
}
