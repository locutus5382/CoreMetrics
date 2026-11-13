package com.locutus5382.coremetrics.quick

import android.content.Context
import com.locutus5382.coremetrics.monitor.MonitorState
import com.locutus5382.coremetrics.monitor.RecorderLease

/** No self-Binder status call: both QS tile services and the recorder live in :monitor. */
internal data class QuickTileSnapshot(
    val mode:QuickTileStatePolicy.Mode,
    val hudVisible:Boolean
) {
    companion object {
        fun read(context:Context):QuickTileSnapshot {
            val alive=MonitorState.serviceCreated
            // When a tile spawns a fresh process after LMK, a user-created
            // recording lease must take precedence over the default idle state.
            val pending=if(!alive)RecorderLease.recover(context)!=null else false
            return QuickTileSnapshot(
                mode=QuickTileStatePolicy.mode(
                    reachable=true,serviceAlive=alive,pendingRecovery=pending,
                    running=MonitorState.running,
                    starting=MonitorState.starting,
                    standby=MonitorState.standby),
                hudVisible=MonitorState.hudVisible
            )
        }
        fun unknown()=QuickTileSnapshot(QuickTileStatePolicy.Mode.UNKNOWN,false)
    }
}
