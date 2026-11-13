package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.monitor.RecorderContinuation
import com.locutus5382.coremetrics.monitor.RecordingService
import org.junit.Assert.*
import org.junit.Test

class RecorderContinuationTest {
    @Test fun activeHudActionsRemainStickyAfterLockAndHide() {
        val actions=listOf(RecordingService.START,RecordingService.HUD_LOCK,
            RecordingService.HUD_UNLOCK,RecordingService.HUD_CONFIG,
            RecordingService.HUD_SHOW,RecordingService.HUD_HIDE,
            RecordingService.APP_FOREGROUND,RecordingService.APP_BACKGROUND,
            RecordingService.ARM)
        for(action in actions) assertTrue("Action $action must preserve recovery",
            RecorderContinuation.sticky(action,active=true,stopping=false))
    }
    @Test fun explicitStopAndDisarmCancelRestartAndIdleDoesNotSelfStart() {
        for(action in listOf(RecordingService.STOP,RecordingService.DISARM))
            assertFalse(RecorderContinuation.sticky(action,active=true,stopping=false))
        assertFalse(RecorderContinuation.sticky(RecordingService.HUD_LOCK,false,false))
        assertFalse(RecorderContinuation.sticky(RecordingService.APP_BACKGROUND,true,true))
        assertFalse(RecorderContinuation.shouldContinue(true,true,false))
    }
}
