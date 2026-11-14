package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale

/** Factual session observations. Never claims a game FPS or causal throttling. */
internal object SessionObservationAnalyzer {
    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun temperature(points: List<SessionStore.Point>,
        name: String, value: (SessionStore.Point) -> Double?): String? {
        val valid = points.mapNotNull { value(it)?.takeIf { v -> v.isFinite() } }
        if (valid.size < 4) return null
        val first = valid.take(4).average()
        val last = valid.takeLast(4).average()
        return name + " sensor: " + fmt(first) + "°C early, " +
            fmt(last) + "°C late; peak " + fmt(valid.maxOrNull() ?: last) + "°C."
    }

    fun observations(points: List<SessionStore.Point>): List<String> {
        if (points.isEmpty()) return listOf("No telemetry samples available.")
        val findings = ArrayList<String>(3)
        val highest = points.mapNotNull { it.thermalStatus?.takeIf { v -> v in 0..6 } }
            .maxOrNull()
        if (highest != null && highest >= 1) {
            val label = when (highest) {
                1 -> "LIGHT"
                2 -> "MODERATE"
                3 -> "SEVERE"
                4 -> "CRITICAL"
                5 -> "EMERGENCY"
                else -> "SHUTDOWN"
            }
            findings += "Android reported " + label + " device-wide thermal pressure. " +
                "This does not identify the throttled component."
        } else if (highest == 0) {
            findings += "Android thermal status reported NONE at the sampled times. " +
                "This does not exclude throttling."
        }
        temperature(points, "CPU", { it.cpu })?.let { findings += it }
        if (findings.size < 3)
            temperature(points, "GPU", { it.gpu })?.let { findings += it }
        if (findings.size < 3) {
            val psi = points.mapNotNull { it.memoryPsiSomeAvg10 }
                .filter { it.isFinite() && it in 0.0..100.0 }.maxOrNull()
            if (psi != null && psi >= 10.0)
                findings += "System memory PSI some avg10 reached " + fmt(psi) +
                    "%. This does not establish game-frame stalls."
        }
        return findings.take(3).ifEmpty {
            listOf("Available sensors do not support a specific conclusion.")
        }
    }
}
