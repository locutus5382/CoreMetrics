package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.quick.QuickTileStatePolicy as P
import org.junit.Assert.*
import org.junit.Test

class QuickTileStatePolicyTest {
    @Test fun unknownCannotStartAnotherSessionOrHud(){
        val unknown=P.mode(false,false,false,false,false,false)
        assertEquals(P.Mode.UNKNOWN,unknown)
        assertEquals(P.RecordAction.RECOVER,P.recorder(unknown))
        assertEquals(P.HudAction.RECOVER,P.hud(unknown,false))
        assertNotEquals(P.RecordAction.START,P.recorder(unknown))
        assertNotEquals(P.HudAction.ARM,P.hud(unknown,false))
    }
    @Test fun freshLeaseRequiresExplicitSameSessionRecovery(){
        val saved=P.mode(true,false,true,false,false,false)
        assertEquals(P.Mode.RECOVERABLE,saved)
        assertEquals(P.RecordAction.RECOVER,P.recorder(saved))
        assertEquals(P.HudAction.RECOVER,P.hud(saved,false))
        assertEquals(P.Mode.IDLE,P.mode(true,false,false,false,false,false))
    }
    @Test fun hudTileNeverStopsActiveRecording(){
        assertEquals(P.HudAction.HIDE,P.hud(P.Mode.RECORDING,true))
        assertEquals(P.HudAction.SHOW,P.hud(P.Mode.RECORDING,false))
        assertEquals(P.RecordAction.STOP,P.recorder(P.Mode.RECORDING))
        assertEquals(P.RecordAction.WAIT,P.recorder(P.Mode.STARTING))
    }
    @Test fun standbyAllowsRecButNotDuplicateHud(){
        assertEquals(P.RecordAction.START,P.recorder(P.Mode.STANDBY))
        assertEquals(P.HudAction.CLOSE,P.hud(P.Mode.STANDBY,true))
        assertEquals(P.HudAction.SHOW,P.hud(P.Mode.STANDBY,false))
    }
}
