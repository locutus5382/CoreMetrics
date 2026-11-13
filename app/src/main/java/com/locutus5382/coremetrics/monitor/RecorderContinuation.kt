package com.locutus5382.coremetrics.monitor

/**
 * Continuation policy for the explicit foreground recorder.
 *
 * A HUD edit/lock/collapse MUST NOT turn an otherwise active, user-started
 * recording into a non-sticky service. STOP/DISARM ALWAYS remove restart intent.
 * START_STICKY is still best effort and cannot bypass Android's low-memory kill
 * decisions or keep recording during the process downtime.
 */
internal object RecorderContinuation {
    fun sticky(action:String?,active:Boolean,stopping:Boolean):Boolean =
        active && !stopping &&
            action!=RecordingService.STOP &&
            action!=RecordingService.DISARM

    // Pure form for JVM tests: no Android Service must be initialized to use it.
    fun shouldContinue(active:Boolean,stopping:Boolean,terminal:Boolean):Boolean =
        active && !stopping && !terminal
}
