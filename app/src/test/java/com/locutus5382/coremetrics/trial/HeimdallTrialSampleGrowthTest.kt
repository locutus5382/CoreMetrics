package com.locutus5382.coremetrics.trial

import org.junit.Assert.*
import org.junit.Test

class HeimdallTrialSampleGrowthTest {
    private fun good(samples: Long = 5) = TrialSampleEvidence(
        statusVerified = true, phase = "RECORDING",
        owner = "HEIMDALL_DEBUG", sessionId = 81L,
        persistedSamples = samples, rowStatus = "RECORDING",
        unresolvedLease = false)

    @Test fun increasingPersistedRowsInOriginalSessionPasses() {
        val result = HeimdallTrialSampleGrowth.evaluate(good(),good(9))
        assertEquals(TrialSampleGrowthResult.VERIFIED_GROWTH, result.result)
        assertTrue(result.passed)
        assertEquals(81L,result.sessionId)
        assertEquals(4L,result.delta)
    }

    @Test fun zeroOrDecreasingSamplesNeverPass() {
        for (n in listOf(5L,4L,-1L)) {
            val result = HeimdallTrialSampleGrowth.evaluate(good(),good(n))
            assertEquals(TrialSampleGrowthResult.NO_NEW_SAMPLES,result.result)
            assertFalse(result.passed)
        }
    }

    @Test fun missingMonitorStatusCannotProveGrowth() {
        assertEquals(TrialSampleGrowthResult.STATUS_UNAVAILABLE,
            HeimdallTrialSampleGrowth.evaluate(
                good().copy(statusVerified = false),good(100)).result)
        assertEquals(TrialSampleGrowthResult.STATUS_UNAVAILABLE,
            HeimdallTrialSampleGrowth.evaluate(
                good(),good(100).copy(statusVerified = false)).result)
    }

    @Test fun unexpectedOwnerOnEitherSideBlocksVerification() {
        for (owner in listOf(null,"LEGACY","UNKNOWN","HEIMDALL_NEXT")) {
            assertEquals(TrialSampleGrowthResult.OWNER_MISMATCH,
                HeimdallTrialSampleGrowth.evaluate(
                    good().copy(owner = owner),good(100)).result)
            assertEquals(TrialSampleGrowthResult.OWNER_MISMATCH,
                HeimdallTrialSampleGrowth.evaluate(
                    good(),good(100).copy(owner = owner)).result)
        }
    }

    @Test fun unresolvedRawLeaseBlocksVerificationEvenIfSamplesIncreased() {
        assertEquals(TrialSampleGrowthResult.OWNER_MISMATCH,
            HeimdallTrialSampleGrowth.evaluate(
                good().copy(unresolvedLease = true),good(99)).result)
    }

    @Test fun aNewSessionMustNotBeMistakenForGrowthInTheOldOne() {
        val result=HeimdallTrialSampleGrowth.evaluate(good(),
            good(90).copy(sessionId = 82L))
        assertEquals(TrialSampleGrowthResult.SESSION_CHANGED,result.result)
    }

    @Test fun missingOriginalIdCannotPass() {
        assertEquals(TrialSampleGrowthResult.SESSION_CHANGED,
            HeimdallTrialSampleGrowth.evaluate(
                good().copy(sessionId = 0L),
                good(90).copy(sessionId = 0L)).result)
    }

    @Test fun finalizedOrMissingSqliteRowCannotPass() {
        for (row in listOf(null,"COMPLETED","FAILED","INTERRUPTED")) {
            assertEquals(TrialSampleGrowthResult.ROW_NOT_RECORDING,
                HeimdallTrialSampleGrowth.evaluate(
                    good(),good(90).copy(rowStatus = row)).result)
        }
    }

    @Test fun actualRunningPhaseRequiredAtBothObservations() {
        for (phase in listOf(null,"STARTING","RECOVERING","STOPPING",
            "RETRY_STOP","RETRY_SAVE","FINALIZING")) {
            assertEquals(TrialSampleGrowthResult.NOT_RECORDING,
                HeimdallTrialSampleGrowth.evaluate(
                    good().copy(phase=phase),good(90)).result)
        }
    }
}
