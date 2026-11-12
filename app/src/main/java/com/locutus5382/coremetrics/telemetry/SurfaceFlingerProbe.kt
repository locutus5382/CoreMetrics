package com.locutus5382.coremetrics.telemetry

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * EXPERIMENTAL, explicitly opt-in. Read-only dumpsys calls from application UID.
 * Production Android generally denies SurfaceFlinger DUMP to ordinary apps.
 * Never claim a real FPS measurement without a valid layer timestamp response.
 * This does not use PServer, root, shell interpolation or high-rate polling.
 */
class SurfaceFlingerProbe(private val context: Context, private val enabled: Boolean) {
    private var layer: String? = null
    private var matchedPackage: String? = null
    private var disabledReason: String? = null
    private var nextProbeMs = 0L
    private var last: FrameEstimate = FrameEstimate(null,null,0,"PROBE_NOT_RUN")

    private fun invoke(vararg args: String): String? {
        var output: File? = null
        return try {
            output = File.createTempFile("cm-fps-", ".txt", context.cacheDir)
            val cmd = listOf("dumpsys", "SurfaceFlinger") + args.toList()
            val process = ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(output!!).start()
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                disabledReason = "SF_COMMAND_TIMEOUT"
                null
            } else {
                output!!.bufferedReader().use { it.readText().take(72_000) }
            }
        } catch (e: Exception) {
            disabledReason = "SF_COMMAND_FAILED_" + e.javaClass.simpleName
            null
        } finally {
            output?.delete()
        }
    }

    fun probe(packageName: String?): FrameEstimate {
        if (!enabled) return FrameEstimate(null,null,0,"OFF_BY_DEFAULT")
        if (packageName.isNullOrBlank() || packageName == context.packageName)
            return FrameEstimate(null,null,0,"FOREGROUND_GAME_NOT_IDENTIFIED")
        disabledReason?.let { return FrameEstimate(null,null,0,it) }
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed < nextProbeMs) return last
        nextProbeMs = elapsed + 15_000L

        if (packageName != matchedPackage || layer == null) {
            val listing = invoke("--list")
                ?: return FrameEstimate(null,null,0,disabledReason ?: "SF_LAYER_LIST_UNAVAILABLE")
            if (listing.contains("Permission Denial",true) ||
                listing.contains("permission denied",true)) {
                disabledReason = "SURFACEFLINGER_PERMISSION_DENIED"
                return FrameEstimate(null,null,0,disabledReason!!)
            }
            val matches = listing.lineSequence().map { it.trim() }
                .filter { it.contains(packageName,ignoreCase=true) }
                .filter { it.isNotBlank() && !it.startsWith("Display") }
                .take(50).toList()
            val candidates = matches.filter { it.contains("SurfaceView",true) }
            val target = when {
                candidates.size == 1 -> candidates.first()
                matches.size == 1 -> matches.first()
                else -> null
            }
            if (target == null) return FrameEstimate(null,null,0,
                if (matches.isEmpty()) "NO_MATCHING_GAME_LAYER" else "AMBIGUOUS_GAME_LAYERS")
            layer = target
            matchedPackage = packageName
        }

        val result = invoke("--latency", layer!!)
            ?: return FrameEstimate(null,null,0,disabledReason ?: "SF_LATENCY_UNAVAILABLE")
        last = FrameLatencyParser.parse(result, System.nanoTime())
        if (last.status == "PERMISSION_DENIED")
            disabledReason = "SURFACEFLINGER_PERMISSION_DENIED"
        return last
    }
}
