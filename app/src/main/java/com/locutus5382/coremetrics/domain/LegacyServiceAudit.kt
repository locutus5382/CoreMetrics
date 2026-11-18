package com.locutus5382.coremetrics.domain

/**
 * M2-E: read-only shadow of events already completed by the legacy service.
 * No commands, platform effects or authority over the recorder.
 * This diagnostic snapshot is never recovery-lease or recording truth.
 */
internal enum class LegacyAuditStop { NONE, PERSISTED, FAILED }
internal enum class LegacyAuditFinish { NONE, CONFIRMED, FAILED }
internal enum class LegacyAuditFinding {
    OVERLAPPING_SESSIONS, WRONG_SESSION_EVENT, STOP_PERSISTENCE_FAILED,
    FINALIZATION_FAILED, FINALIZED_BEFORE_SAMPLER_EXIT,
}
internal data class LegacyAuditSnapshot(
    val epoch: String,
    val sequence: Long = 0L,
    val sessionId: Long? = null,
    val stop: LegacyAuditStop = LegacyAuditStop.NONE,
    val samplerExited: Boolean = false,
    val finish: LegacyAuditFinish = LegacyAuditFinish.NONE,
    val serviceDestroyed: Boolean = false,
    val findings: Set<LegacyAuditFinding> = emptySet(),
)

internal sealed interface LegacyAuditObservation {
    data class SessionAcquired(val sessionId: Long) : LegacyAuditObservation
    /** The bool is the return value of RecorderLease.requestStop(), not an intent. */
    data class TerminalIntent(val sessionId: Long, val persisted: Boolean) : LegacyAuditObservation
    /** Posted only after the sampling loop exits, at the start of its finally block. */
    data class SamplerExited(val sessionId: Long) : LegacyAuditObservation
    /** The bool is the result of SessionStore.finish() on the original session ID. */
    data class Finalized(val sessionId: Long, val succeeded: Boolean) : LegacyAuditObservation
    data object ServiceDestroyed : LegacyAuditObservation
}

/** Deterministic and Android-free; never substitutes observation for authorization. */
internal object LegacyServiceAudit {
    fun begin(epoch: String): LegacyAuditSnapshot {
        require(epoch.isNotBlank())
        return LegacyAuditSnapshot(epoch = epoch)
    }

    fun observe(s: LegacyAuditSnapshot, observation: LegacyAuditObservation): LegacyAuditSnapshot {
        if (s.serviceDestroyed) return s
        val next = when (observation) {
            is LegacyAuditObservation.SessionAcquired -> {
                if (observation.sessionId <= 0L) return s
                if (s.sessionId == observation.sessionId && s.finish != LegacyAuditFinish.CONFIRMED) s
                else {
                    val overlap = s.sessionId != null && s.sessionId != observation.sessionId &&
                        s.finish != LegacyAuditFinish.CONFIRMED
                    s.copy(sessionId = observation.sessionId, stop = LegacyAuditStop.NONE,
                        samplerExited = false, finish = LegacyAuditFinish.NONE,
                        findings = if (overlap) s.findings + LegacyAuditFinding.OVERLAPPING_SESSIONS
                            else s.findings)
                }
            }
            is LegacyAuditObservation.TerminalIntent -> {
                if (observation.sessionId != s.sessionId) wrongSession(s)
                else s.copy(stop = if (observation.persisted) LegacyAuditStop.PERSISTED
                    else LegacyAuditStop.FAILED,
                    findings = if (observation.persisted) s.findings
                        else s.findings + LegacyAuditFinding.STOP_PERSISTENCE_FAILED)
            }
            is LegacyAuditObservation.SamplerExited -> {
                if (observation.sessionId != s.sessionId) wrongSession(s)
                else s.copy(samplerExited = true)
            }
            is LegacyAuditObservation.Finalized -> {
                if (observation.sessionId != s.sessionId) wrongSession(s)
                else {
                    val findings = buildSet {
                        addAll(s.findings)
                        if (!s.samplerExited) add(LegacyAuditFinding.FINALIZED_BEFORE_SAMPLER_EXIT)
                        if (!observation.succeeded) add(LegacyAuditFinding.FINALIZATION_FAILED)
                    }
                    s.copy(finish = if (observation.succeeded) LegacyAuditFinish.CONFIRMED
                        else LegacyAuditFinish.FAILED, findings = findings)
                }
            }
            LegacyAuditObservation.ServiceDestroyed -> s.copy(serviceDestroyed = true)
        }
        return if (next == s) s else next.copy(sequence = s.sequence + 1)
    }

    private fun wrongSession(s: LegacyAuditSnapshot): LegacyAuditSnapshot =
        s.copy(findings = s.findings + LegacyAuditFinding.WRONG_SESSION_EVENT)
}
