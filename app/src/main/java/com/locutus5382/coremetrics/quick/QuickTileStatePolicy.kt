package com.locutus5382.coremetrics.quick

/** Unknown status is never interpreted as permission to start another recorder. */
internal object QuickTileStatePolicy {
    enum class Mode { UNKNOWN, IDLE, RECORDING, STARTING, STANDBY, RECOVERABLE }
    enum class RecordAction { START, STOP, RECOVER, WAIT }
    enum class HudAction { ARM, SHOW, HIDE, CLOSE, RECOVER, WAIT }
    fun mode(reachable:Boolean,serviceAlive:Boolean,pendingRecovery:Boolean,
        running:Boolean,starting:Boolean,standby:Boolean):Mode=when {
        !reachable->Mode.UNKNOWN
        !serviceAlive && pendingRecovery->Mode.RECOVERABLE
        !serviceAlive->Mode.IDLE
        running->Mode.RECORDING
        starting->Mode.STARTING
        standby->Mode.STANDBY
        else->Mode.IDLE
    }
    fun recorder(mode:Mode)=when(mode){
        Mode.RECORDING->RecordAction.STOP
        Mode.RECOVERABLE->RecordAction.RECOVER
        Mode.IDLE,Mode.STANDBY->RecordAction.START
        Mode.STARTING,Mode.UNKNOWN->RecordAction.WAIT
    }
    fun hud(mode:Mode,visible:Boolean)=when(mode){
        Mode.UNKNOWN,Mode.STARTING->HudAction.WAIT
        Mode.RECOVERABLE->HudAction.RECOVER
        Mode.IDLE->HudAction.ARM
        Mode.RECORDING->if(visible)HudAction.HIDE else HudAction.SHOW
        Mode.STANDBY->if(visible)HudAction.CLOSE else HudAction.SHOW
    }
}
