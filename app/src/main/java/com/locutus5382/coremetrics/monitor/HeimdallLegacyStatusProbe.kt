package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.locutus5382.coremetrics.domain.LegacyStatusFields
import com.locutus5382.coremetrics.domain.LegacyStatusObservation
import com.locutus5382.coremetrics.domain.LegacyStatusPolicy

/**
 * M2-C status adapter. Not called by the current Activity or RecordingService.
 * Queries the already-declared, private StatusProvider with its existing method.
 * Provider initialization MAY start the :monitor *process* but does not start
 * RecordingService, a foreground service, sampling, or a session.
 */
internal class HeimdallLegacyStatusProbe(private val context: Context) {
    sealed interface Result {
        data class Observed(val value: LegacyStatusObservation) : Result
        data class Unavailable(val reason: String) : Result
    }

    fun read(nowWallMs: Long = System.currentTimeMillis()): Result = try {
        val uri = Uri.Builder().scheme("content")
            .authority("${context.packageName}.monitor.status").build()
        val status = context.contentResolver.call(uri, "status", null, null)
            ?: return Result.Unavailable("NO_STATUS_RESPONSE")
        if (!status.containsKey("serviceAlive") || !status.containsKey("running") ||
            !status.containsKey("pendingRecovery") || !status.containsKey("id"))
            return Result.Unavailable("UNRECOGNIZED_STATUS_CONTRACT")
        Result.Observed(LegacyStatusPolicy.interpret(status.toFields(), nowWallMs))
    } catch (_: SecurityException) {
        Result.Unavailable("STATUS_ACCESS_DENIED")
    } catch (_: RuntimeException) {
        // Provider process death/IPC failure is not a claim that the service is idle.
        Result.Unavailable("STATUS_IPC_UNAVAILABLE")
    }

    private fun Bundle.toFields() = LegacyStatusFields(
        serviceAlive = getBoolean("serviceAlive"),
        pendingRecovery = getBoolean("pendingRecovery"),
        recoveringRecording = getBoolean("recoveringRecording"),
        recoverySessionId = getLong("recoverySessionId"),
        running = getBoolean("running"),
        starting = getBoolean("starting"),
        standby = getBoolean("standby"),
        sessionId = getLong("id"),
        sampleCount = getLong("count"),
        lastSampleWallMs = getLong("lastSampleAt"),
        reportedHudVisible = getBoolean("hudVisible"),
    )
}
