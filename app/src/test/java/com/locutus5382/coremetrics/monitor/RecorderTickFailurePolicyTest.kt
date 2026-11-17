package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class RecorderTickFailurePolicyTest {
    @Test fun transientErrorsDoNotDiscardFullSession() {
        for(n in 1..4)
            assertFalse(RecorderTickFailurePolicy.shouldTerminate(
                "SQLiteDatabaseLockedException",n))
        assertTrue(RecorderTickFailurePolicy.shouldTerminate(
            "SQLiteDatabaseLockedException",5))
    }
    @Test fun permanentStorageFailuresTerminateImmediately() {
        for(type in listOf("SQLiteFullException",
            "SQLiteDatabaseCorruptException","SQLiteCantOpenDatabaseException"))
            assertTrue(RecorderTickFailurePolicy.shouldTerminate(type,1))
    }
    @Test fun samplingExceptionsHaveSameBoundedRun() {
        assertFalse(RecorderTickFailurePolicy.shouldTerminate("SecurityException",2))
        assertTrue(RecorderTickFailurePolicy.shouldTerminate("SecurityException",5))
        assertFalse(RecorderTickFailurePolicy.shouldTerminate("IOException",1))
    }
}
