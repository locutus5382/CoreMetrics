package com.locutus5382.coremetrics.engine

import android.app.Application
import android.database.sqlite.SQLiteDatabaseLockedException
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.domain.EngineEffectPort
import com.locutus5382.coremetrics.domain.StopReason
import com.locutus5382.coremetrics.monitor.RecorderLease
import com.locutus5382.coremetrics.monitor.RecorderTickFailurePolicy
import com.locutus5382.coremetrics.telemetry.DirectSampler
import com.locutus5382.coremetrics.telemetry.ForegroundAttribution
import com.locutus5382.coremetrics.telemetry.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Real Android collector / SQLite v9 / recovery-lease adapter for the NEW
 * Heimdall sampling loop. NOT instantiated by the legacy RecordingService
 * in M2-F; dual ownership is explicitly forbidden.
 */
internal class HeimdallAndroidSessionIo(
    private val application: Application,
    private val store: SessionStore,
    private val intervalMs: Long,
) : HeimdallSessionIo<Snapshot> {
    private val sampler = DirectSampler(application)
    private val foreground = ForegroundAttribution(application.applicationContext)
    private var lastHeartbeatElapsed = 0L

    init { require(intervalMs == 2_000L || intervalMs == 5_000L) }

    override suspend fun begin(profileId: Long?): EngineEffectPort.Opened =
        withContext(Dispatchers.IO) {
            // User metadata is not proof of a hardware configuration.
            if (profileId != null && store.activeProfileId() != profileId)
                error("PROFILE_SELECTION_CHANGED")
            val defaults = store.recordingDefaults()
            val id = store.begin(defaults.label, defaults.configuration,
                System.currentTimeMillis())
            EngineEffectPort.Opened(id, android.os.SystemClock.elapsedRealtime())
        }

    override suspend fun startLease(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val defaults = store.recordingDefaults()
            RecorderLease.start(application, sessionId, defaults.label,
                defaults.configuration, intervalMs, false, false,
                owner = RecorderLease.Owner.HEIMDALL_DEBUG)
        }

    override suspend fun persistStop(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val lease = RecorderLease.recover(application)
            if (lease?.recording != true || lease.id != sessionId ||
                lease.owner != RecorderLease.Owner.HEIMDALL_DEBUG)
                return@withContext false
            if (lease.stopRequested) true else RecorderLease.requestStop(application)
        }

    override suspend fun collect(): Snapshot = withContext(Dispatchers.IO) {
        sampler.sample(foreground.current(System.currentTimeMillis()))
    }

    override suspend fun persist(sessionId: Long, sample: Snapshot) =
        withContext(Dispatchers.IO) {
            var attempts = 0
            while (true) {
                try {
                    store.add(sessionId, sample)
                    break
                } catch (lock: SQLiteDatabaseLockedException) {
                    attempts++
                    if (attempts >= 2) throw lock
                    delay(RecorderTickFailurePolicy.LOCK_RETRY_DELAY_MS)
                }
            }
        }

    /**
     * Lease owner/STOP evidence is mandatory, not an optional HUD callback.
     * Check EVERY committed sample. Only the synchronous timestamp write is
     * rate-limited; a failed write cannot advance lastHeartbeatElapsed.
     */
    override suspend fun afterPersist(sessionId: Long) =
        withContext(Dispatchers.IO) {
            val now = android.os.SystemClock.elapsedRealtime()
            val renew = lastHeartbeatElapsed == 0L ||
                now - lastHeartbeatElapsed >= 12_000L
            val result = try {
                RecorderLease.heartbeatHeimdall(application, sessionId, renew)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                throw HeimdallLeaseHealthException(
                    HeimdallLeaseHealthIssue.COMMIT_FAILED)
            }
            when (result) {
                RecorderLease.HeimdallHeartbeat.HEALTHY -> Unit
                RecorderLease.HeimdallHeartbeat.RENEWED -> lastHeartbeatElapsed = now
                RecorderLease.HeimdallHeartbeat.OWNER_CHANGED ->
                    throw HeimdallLeaseHealthException(
                        HeimdallLeaseHealthIssue.OWNER_CHANGED)
                RecorderLease.HeimdallHeartbeat.STOP_ALREADY_PERSISTED ->
                    throw HeimdallLeaseHealthException(
                        HeimdallLeaseHealthIssue.TERMINAL_STOP_SEEN)
                RecorderLease.HeimdallHeartbeat.COMMIT_FAILED ->
                    throw HeimdallLeaseHealthException(
                        HeimdallLeaseHealthIssue.COMMIT_FAILED)
            }
        }

    override suspend fun finish(sessionId: Long, reason: StopReason): Boolean =
        withContext(Dispatchers.IO) {
            HeimdallTerminalFinalizer.finalizeOriginal(
                sessionId, reason, object : TerminalCommitPort {
                    override suspend fun lease(sessionId: Long): TerminalLeaseProof {
                        val raw = RecorderLease.rawSession(application)
                        return TerminalLeaseProof(
                            active = raw.active,
                            matchesHeimdallSession = raw.recording &&
                                raw.sessionId == sessionId &&
                                raw.owner == RecorderLease.Owner.HEIMDALL_DEBUG,
                            persistedStop = raw.stopPersisted,
                        )
                    }
                    override suspend fun status(sessionId: Long): String? =
                        store.recordingStatus(sessionId)

                    override suspend fun commitIfRecording(
                        sessionId: Long, terminalStatus: String
                    ): Boolean = store.finishRecordingOnce(
                        sessionId, System.currentTimeMillis(), terminalStatus)

                    override suspend fun clearMatchingLease(sessionId: Long): Boolean =
                        RecorderLease.clearHeimdallStoppedSession(
                            application, sessionId)
                }
            )
        }

    override suspend fun validatedRecovery(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val lease = RecorderLease.recover(application)
            lease?.recording == true && lease.id == sessionId &&
                lease.owner == RecorderLease.Owner.HEIMDALL_DEBUG &&
                !lease.stopRequested && store.canResume(sessionId)
        }

    override suspend fun terminalRecovery(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val lease = RecorderLease.recover(application)
            lease?.recording == true && lease.id == sessionId &&
                lease.owner == RecorderLease.Owner.HEIMDALL_DEBUG &&
                lease.stopRequested && store.canResume(sessionId)
        }

    override suspend fun renewRecoveryLease(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val lease = RecorderLease.recover(application)
            if (lease?.recording != true || lease.id != sessionId ||
                lease.owner != RecorderLease.Owner.HEIMDALL_DEBUG ||
                lease.stopRequested || !store.canResume(sessionId))
                return@withContext false
            if (!RecorderLease.markRecovery(application)) return@withContext false
            RecorderLease.start(application, sessionId, lease.label,
                lease.configuration, lease.intervalMs, false, restored = true,
                owner = RecorderLease.Owner.HEIMDALL_DEBUG)
        }

    override suspend fun unfinishedRow(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            store.canResume(sessionId)
        }

    override suspend fun closeInterrupted(sessionId: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!store.finish(sessionId, System.currentTimeMillis(),
                    "INTERRUPTED_RECOVERED"))
                return@withContext false
            val lease = RecorderLease.recover(application)
            if (lease?.recording == true && lease.id == sessionId &&
                lease.owner == RecorderLease.Owner.HEIMDALL_DEBUG)
                RecorderLease.clear(application)
            else true
        }

    // The new Canvas HUD owner has not been installed yet. Do not attach
    // the old five-theme HUD through the new engine without explicit handoff.
    override suspend fun attachOverlay(operationId: Long): Boolean = false
    override suspend fun detachOverlay(operationId: Long): Boolean = false
}
