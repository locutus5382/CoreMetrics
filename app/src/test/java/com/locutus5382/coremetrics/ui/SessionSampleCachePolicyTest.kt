package com.locutus5382.coremetrics.ui

import org.junit.Assert.*
import org.junit.Test

class SessionSampleCachePolicyTest {
    @Test fun metadataEditWithSameCountKeepsOriginalTelemetryCache() {
        assertFalse(SessionSampleCachePolicy.reload("Sessions",8L,8L,100,100))
        assertFalse(SessionSampleCachePolicy.reload("Sessions",8L,8L,null,100))
    }
    @Test fun completedOrGrowingRecordingInvalidatesOnlyOnSampleChange() {
        assertTrue(SessionSampleCachePolicy.reload("Sessions",8L,8L,101,100))
        assertTrue(SessionSampleCachePolicy.reload("Sessions",8L,8L,99,100))
    }
    @Test fun selectionSwitchAndPageExitAreCorrect() {
        assertTrue(SessionSampleCachePolicy.reload("Sessions",9L,8L,100,100))
        assertTrue(SessionSampleCachePolicy.reload("Sessions",8L,0L,0,0))
        assertFalse(SessionSampleCachePolicy.reload("Compare",8L,0L,100,0))
        assertFalse(SessionSampleCachePolicy.reload("Sessions",0L,0L,null,-1))
    }
}
