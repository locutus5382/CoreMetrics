package com.locutus5382.coremetrics.quick

/** Unknown status is never interpreted as permission to start another recorder. */
internal object QuickTileStatePolicy {
    enum class Mode { UNKNOWN, IDLE, RECORDING, STARTING, SAVING, STOP_PENDING, STANDBY, RECOVERABLE }
    enum class RecordAction { START, STOP, RECOVER, WAIT }
    enum class HudAction { ARM, SHOW, HIDE, CLOSE, RECOVER, WAIT }
    fun mode(reachable:Boolean,serviceAlive:Boolean,pendingRecovery:Boolean,
        running:Boolean,starting:Boolean,standby:Boolean,
        stopping:Boolean=false,stopPending:Boolean=false):Mode=when {
        !reachable->Mode.UNKNOWN
        stopPending && !running->Mode.STOP_PENDING
        stopping->Mode.SAVING
        pendingRecovery && !running && !starting && !standby->Mode.RECOVERABLE
        !serviceAlive->Mode.IDLE
        running->Mode.RECORDING
        starting->Mode.STARTING
        standby->Mode.STANDBY
        else->Mode.IDLE
    }
    fun recorder(mode:Mode)=when(mode){
        Mode.RECORDING->RecordAction.STOP
        Mode.RECOVERABLE,Mode.STOP_PENDING->RecordAction.RECOVER
        Mode.IDLE,Mode.STANDBY->RecordAction.START
        // Query failure permits ONLY an explicit attempt to recover a
        // validated pre-existing lease, never an unrelated new START.
        Mode.UNKNOWN->RecordAction.RECOVER
        Mode.STARTING,Mode.SAVING->RecordAction.WAIT
    }
    fun hud(mode:Mode,visible:Boolean,requested:Boolean=visible,
        pausedInApp:Boolean=false)=when(mode){
        Mode.UNKNOWN,Mode.RECOVERABLE,Mode.STOP_PENDING->HudAction.RECOVER
        Mode.STARTING,Mode.SAVING->HudAction.WAIT
        Mode.IDLE->HudAction.ARM
        Mode.RECORDING->if(visible || pausedInApp && requested)HudAction.HIDE
            else HudAction.SHOW
        Mode.STANDBY->if(visible || pausedInApp && requested)HudAction.CLOSE
            else HudAction.SHOW
    }
}
