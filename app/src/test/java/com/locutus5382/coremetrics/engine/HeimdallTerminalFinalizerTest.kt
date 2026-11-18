package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.domain.StopReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * Injected failures are BEFORE/AFTER logical persistence boundaries. We do
 * not simulate Android process death by pretending SharedPreferences is a DB.
 * These tests prove contract behavior; emulator tests validate the adapters.
 */
class HeimdallTerminalFinalizerTest {
    private class FakePort : TerminalCommitPort {
        var row: String? = "RECORDING"
        var leaseActive = true
        var ownerMatches = true
        var stopPersisted = true
        var dbCommitFails = false
        var dbCommitThrows = false
        var clearFails = false
        var clearThrows = false
        var rewriteCount = 0
        var clearedCount = 0
        var lastId: Long? = null
        var finishedAtMs: Long? = null
        private var timestamp = 100L

        override suspend fun lease(sessionId: Long): TerminalLeaseProof {
            return TerminalLeaseProof(leaseActive, ownerMatches && sessionId == 73L,
                stopPersisted)
        }
        override suspend fun status(sessionId: Long): String? =
            if (sessionId == 73L) row else null

        override suspend fun commitIfRecording(
            sessionId: Long, terminalStatus: String
        ): Boolean {
            if (dbCommitThrows) throw IOException("injected sqlite write failure")
            if (dbCommitFails || sessionId != 73L || row != "RECORDING") return false
            row = terminalStatus
            rewriteCount++
            finishedAtMs = timestamp++
            lastId = sessionId
            return true
        }

        override suspend fun clearMatchingLease(sessionId: Long): Boolean {
            if (clearThrows) throw IOException("injected lease commit failure")
            if (clearFails || sessionId != 73L || !ownerMatches ||
                !stopPersisted || !leaseActive) return false
            leaseActive = false
            clearedCount++
            return true
        }
    }
    private suspend fun finish(p: FakePort, id: Long = 73L,
        why: StopReason = StopReason.USER
    ) = HeimdallTerminalFinalizer.finalizeOriginal(id, why, p)

    @Test fun successfulStopIsDurableThenFinalizedThenCleared() = runBlocking {
        val p = FakePort()
        assertTrue(finish(p))
        assertEquals("COMPLETED", p.row)
        assertEquals(1, p.rewriteCount)
        assertEquals(1, p.clearedCount)
        assertFalse(p.leaseActive)
        assertEquals(73L, p.lastId)
    }

    @Test fun failedStopWriteCannotFinalizeOrClear() = runBlocking {
        val p = FakePort().apply { stopPersisted = false }
        assertFalse(finish(p))
        assertEquals("RECORDING", p.row)
        assertEquals(0, p.rewriteCount)
        assertEquals(0, p.clearedCount)
    }

    @Test fun foreignEngineLeaseCannotBeFinalizedOrCleared() = runBlocking {
        val p = FakePort().apply { ownerMatches = false }
        assertFalse(finish(p))
        assertEquals("RECORDING", p.row)
        assertTrue(p.leaseActive)
    }

    @Test fun finalizingWrongOriginalIdDoesNotChangeAnotherRow() = runBlocking {
        val p = FakePort()
        assertFalse(finish(p, id = 74L))
        assertEquals("RECORDING", p.row)
        assertEquals(0, p.rewriteCount)
    }

    @Test fun databaseWriteFailureLeavesDurableStopForRetry() = runBlocking {
        val p = FakePort().apply { dbCommitFails = true }
        assertFalse(finish(p))
        assertTrue(p.leaseActive)
        assertEquals("RECORDING", p.row)
        p.dbCommitFails = false
        assertTrue(finish(p))
        assertEquals(1, p.rewriteCount)
        assertEquals(1, p.clearedCount)
    }

    @Test fun thrownDatabaseExceptionCannotReleaseLease() = runBlocking {
        val p = FakePort().apply { dbCommitThrows = true }
        try { finish(p); fail("injected exception expected") }
        catch (_: IOException) { }
        assertEquals(0, p.clearedCount)
        assertTrue(p.leaseActive)
        assertEquals("RECORDING", p.row)
    }

    @Test fun leaseClearFailureRetriesOnlyCleanupWithoutChangingEndTime() = runBlocking {
        val p = FakePort().apply { clearFails = true }
        assertFalse(finish(p))
        assertEquals("COMPLETED", p.row)
        val originalEnd = p.finishedAtMs
        assertEquals(1, p.rewriteCount)
        assertTrue(p.leaseActive)
        p.clearFails = false
        assertTrue(finish(p))
        assertEquals(originalEnd, p.finishedAtMs)
        assertEquals(1, p.rewriteCount)
        assertEquals(1, p.clearedCount)
    }

    @Test fun crashAfterSqliteCommitLeavesRecoverableSameId() = runBlocking {
        val p = FakePort().apply { clearThrows = true }
        try { finish(p); fail("injected exception expected") }
        catch (_: IOException) { }
        assertEquals("COMPLETED", p.row)
        assertEquals(73L, p.lastId)
        assertEquals(0, p.clearedCount)
        assertTrue(p.leaseActive)
        p.clearThrows = false
        assertTrue(finish(p))
        assertEquals(1, p.rewriteCount)
        assertEquals(1, p.clearedCount)
    }

    @Test fun acknowledgedTerminalSessionDoesNotGetRewrittenOnRedelivery() = runBlocking {
        val p = FakePort()
        assertTrue(finish(p))
        assertTrue(finish(p))
        assertEquals(1, p.rewriteCount)
        assertEquals(1, p.clearedCount)
    }

    @Test fun existingDifferentTerminalResultCannotBeOverwritten() = runBlocking {
        val p = FakePort().apply { row = "FAILED" }
        assertFalse(finish(p, why = StopReason.USER))
        assertEquals("FAILED", p.row)
        assertTrue(p.leaseActive)
        assertEquals(0, p.rewriteCount)
    }

    @Test fun alreadyFailedRowCanClearMatchingLeaseWithoutRewrite() = runBlocking {
        val p = FakePort().apply { row = "FAILED" }
        assertTrue(finish(p, why = StopReason.STORAGE_ERROR))
        assertEquals("FAILED", p.row)
        assertEquals(0, p.rewriteCount)
        assertEquals(1, p.clearedCount)
    }

    @Test fun missingRowCannotReleaseLease() = runBlocking {
        val p = FakePort().apply { row = null }
        assertFalse(finish(p))
        assertTrue(p.leaseActive)
    }

    @Test fun absentLeaseOnlyAcknowledgesAlreadyTerminalRow() = runBlocking {
        val p = FakePort().apply { leaseActive = false }
        assertFalse(finish(p))
        p.row = "COMPLETED"
        assertTrue(finish(p))
        assertEquals(0, p.rewriteCount)
        assertEquals(0, p.clearedCount)
    }

    @Test fun differentStopReasonCannotRewriteAlreadyCompletedResult() = runBlocking {
        val p = FakePort().apply { row = "COMPLETED" }
        assertFalse(finish(p, why = StopReason.INTERRUPTED))
        assertEquals("COMPLETED", p.row)
    }

    @Test fun invalidSessionIdRejectedBeforeAnyWrite() = runBlocking {
        val p = FakePort()
        try { finish(p, 0); fail("expected invalid session rejection") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, p.rewriteCount)
        assertEquals(0, p.clearedCount)
    }

    @Test fun terminalStatusMappingIsStable() {
        assertEquals("COMPLETED", HeimdallTerminalFinalizer.statusFor(StopReason.USER))
        assertEquals("FAILED", HeimdallTerminalFinalizer.statusFor(StopReason.SAMPLE_FAILURE))
        assertEquals("FAILED", HeimdallTerminalFinalizer.statusFor(StopReason.STORAGE_ERROR))
        assertEquals("INTERRUPTED", HeimdallTerminalFinalizer.statusFor(StopReason.INTERRUPTED))
    }
}
