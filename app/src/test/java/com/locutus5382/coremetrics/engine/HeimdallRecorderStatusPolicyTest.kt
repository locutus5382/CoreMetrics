package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.EngineState
import com.locutus5382.coremetrics.domain.RecordingState
import com.locutus5382.coremetrics.domain.StopReason
import org.junit.Assert.*
import org.junit.Test

class HeimdallRecorderStatusPolicyTest {
    private fun show(recording: RecordingState) = HeimdallRecorderStatusPolicy.present(
        EngineState(epoch = "test-epoch", recording = recording))

    @Test fun onlyRecordingStateIsReportedAsLiveSampling() {
        val active = show(RecordingState.Recording(73, 1_000, null))
        assertEquals(HeimdallRecorderPhase.RECORDING, active.phase)
        assertTrue(active.recording)
        assertEquals(73L, active.sessionId)
    }

    @Test fun preparingDoesNotPretendThatSamplesHaveStarted() {
        val s = show(RecordingState.Starting("start", null))
        assertTrue(s.preparing)
        assertFalse(s.recording)
        assertNull(s.sessionId)
    }

    @Test fun recoveringOriginalSessionIsNotYetLiveRecording() {
        val s = show(RecordingState.Recovering(73, 1_000, null))
        assertEquals(HeimdallRecorderPhase.RECOVERING, s.phase)
        assertEquals(73L, s.sessionId)
        assertTrue(s.preparing)
        assertFalse(s.recording)
    }

    @Test fun stopInProgressIsNeverActiveRecordingOrRetryYet() {
        val s = show(RecordingState.StopRequested(73, "stop", StopReason.USER))
        assertEquals(HeimdallRecorderPhase.STOPPING, s.phase)
        assertFalse(s.recording)
        assertTrue(s.saving)
        assertFalse(s.retryAvailable)
    }

    @Test fun failedStopCommitOffersExplicitRetryWithoutClaimingDurability() {
        val s = show(RecordingState.StopRequested(
            73, "stop", StopReason.USER, persistError = "IO_ERROR",
            samplingPaused = true))
        assertEquals(HeimdallRecorderPhase.RETRY_STOP, s.phase)
        assertTrue(s.retryAvailable)
        assertFalse(s.recording)
        assertEquals("STOP_NOT_DURABLE", s.issueCode)
        assertFalse(s.message.contains("IO_ERROR"))
    }

    @Test fun failedSamplerJoinOffersStopRetryWithoutFabricatingQuiescence() {
        val s = show(RecordingState.StopRequested(
            73, "stop", StopReason.USER, durableStop = true,
            pauseError = "JOIN_FAILED"))
        assertEquals(HeimdallRecorderPhase.RETRY_STOP, s.phase)
        assertEquals("SAMPLER_NOT_QUIESCED", s.issueCode)
        assertFalse(s.recording)
    }

    @Test fun bothFailuresRetainOriginalSessionId() {
        val s = show(RecordingState.StopRequested(
            73, "stop", StopReason.USER,
            persistError = "WRITE", pauseError = "JOIN"))
        assertEquals("STOP_AND_SAMPLER_UNCONFIRMED", s.issueCode)
        assertEquals(73L, s.sessionId)
        assertTrue(s.retryAvailable)
    }

    @Test fun finalizationIsSavingNotRecording() {
        val s = show(RecordingState.Finalizing(73, "stop", StopReason.USER))
        assertEquals(HeimdallRecorderPhase.FINALIZING, s.phase)
        assertTrue(s.saving)
        assertFalse(s.recording)
        assertFalse(s.retryAvailable)
    }

    @Test fun failedFinalizationOffersSaveRetryAndDoesNotClaimRecording() {
        val s = show(RecordingState.FinalizeFailed(
            73, "stop", StopReason.USER, "SQLITE_BUSY"))
        assertEquals(HeimdallRecorderPhase.RETRY_SAVE, s.phase)
        assertEquals("FINALIZATION_UNCONFIRMED", s.issueCode)
        assertTrue(s.retryAvailable)
        assertTrue(s.saving)
        assertFalse(s.recording)
        assertFalse(s.message.contains("SQLITE_BUSY"))
    }

    @Test fun failedEngineCannotAppearIdleOrStartable() {
        val s = show(RecordingState.Failed("MISSING_LEASE", 73))
        assertEquals(HeimdallRecorderPhase.NEEDS_REPAIR, s.phase)
        assertTrue(s.repairRequired)
        assertFalse(s.recording)
        assertFalse(s.retryAvailable)
        assertEquals("OWNER_REVIEW_REQUIRED", s.issueCode)
    }

    @Test fun idleHasNoStaleErrorOrSession() {
        val s = show(RecordingState.Idle)
        assertEquals(HeimdallRecorderPhase.IDLE, s.phase)
        assertNull(s.sessionId)
        assertNull(s.issueCode)
        assertFalse(s.saving)
        assertFalse(s.recording)
    }
}
