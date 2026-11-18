package com.locutus5382.coremetrics.quick

import android.content.Context
import com.locutus5382.coremetrics.BuildConfig
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
            val noActiveSampler = !MonitorState.running &&
                !MonitorState.starting && !MonitorState.standby
            val pendingLease = if(noActiveSampler)
                RecorderLease.recover(context) else null
            val raw = if(noActiveSampler)
                RecorderLease.rawSession(context) else null
            val unresolved = raw?.active == true &&
                (pendingLease == null ||
                    pendingLease.owner == RecorderLease.Owner.UNKNOWN ||
                    (raw.owner == RecorderLease.Owner.HEIMDALL_DEBUG &&
                        !BuildConfig.DEBUG))
            // Native phase comes from the service reducer; an unavailable
            // lease must NEVER turn into permission to create a new session.
            val phaseMode = if(alive) when(MonitorState.heimdallRecorderPhase) {
                "RETRY_STOP" -> QuickTileStatePolicy.Mode.STOP_RETRY
                "RETRY_SAVE" -> QuickTileStatePolicy.Mode.SAVE_RETRY
                "NEEDS_REPAIR" -> QuickTileStatePolicy.Mode.REPAIR_REQUIRED
                else -> null
            } else null
            val mode = phaseMode ?: if(unresolved)
                QuickTileStatePolicy.Mode.REPAIR_REQUIRED
            else QuickTileStatePolicy.mode(
                reachable=true,serviceAlive=alive,
                pendingRecovery=pendingLease!=null,
                running=MonitorState.running,
                starting=MonitorState.starting,
                standby=MonitorState.standby,
                stopping=MonitorState.saving,
                stopPending=pendingLease?.stopRequested==true)
            return QuickTileSnapshot(
                mode=mode,
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
