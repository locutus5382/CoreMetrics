package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.monitor.RecorderContinuation
import org.junit.Assert.*
import org.junit.Test

class RecorderContinuationTest {
    @Test fun activeHudCommandsCannotDisableBestEffortRestart() {
        for (i in 0..12) assertTrue(RecorderContinuation.shouldContinue(
            active=true,stopping=false,terminal=false))
    }
    @Test fun explicitStopOrDisarmNeverRestart() {
        assertFalse(RecorderContinuation.shouldContinue(true,false,true))
        assertFalse(RecorderContinuation.shouldContinue(true,true,false))
        assertFalse(RecorderContinuation.shouldContinue(false,false,false))
    }
}
