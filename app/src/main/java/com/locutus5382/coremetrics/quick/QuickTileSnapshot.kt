package com.locutus5382.coremetrics.quick

import android.content.Context
import com.locutus5382.coremetrics.monitor.MonitorState
import com.locutus5382.coremetrics.monitor.RecorderLease

/** No self-Binder status call: both QS tile services and the recorder live in :monitor. */
internal data class QuickTileSnapshot(
    val mode:QuickTileStatePolicy.Mode,
    val hudVisible:Boolean,
    val hudRequested:Boolean=false,
    val hudAttached:Boolean=false,
    val pausedInApp:Boolean=false
) {
    companion object {
        fun read(context:Context):QuickTileSnapshot {
            val alive=MonitorState.serviceCreated
            // A provider/tile may spawn :monitor without the user-started
            // recorder service. The persisted lease takes precedence over an
            // apparently idle fresh process.
            val pendingLease=if(!MonitorState.running &&
                !MonitorState.starting && !MonitorState.standby)
                RecorderLease.recover(context) else null
            return QuickTileSnapshot(
                mode=QuickTileStatePolicy.mode(
                    reachable=true,serviceAlive=alive,
                    pendingRecovery=pendingLease!=null,
                    running=MonitorState.running,
                    starting=MonitorState.starting,
                    standby=MonitorState.standby,
                    stopping=MonitorState.saving,
                    stopPending=pendingLease?.stopRequested==true),
                hudVisible=MonitorState.hudVisible,
                hudRequested=MonitorState.hudRequested ||
                    pendingLease?.hudEnabled==true,
                hudAttached=MonitorState.hudAttached,
                pausedInApp=MonitorState.appPaused
            )
        }
        fun unknown()=QuickTileSnapshot(QuickTileStatePolicy.Mode.UNKNOWN,false)
    }
}
