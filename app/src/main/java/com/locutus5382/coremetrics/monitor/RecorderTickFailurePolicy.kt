package com.locutus5382.coremetrics.monitor

/** A transient read/write fault must not discard a 60-minute session.
 * The recorder never claims a dropped sample was collected: integrity
 * analysis will show the resulting timestamp gap. Permanent storage failures
 * fail immediately rather than silently accumulating false confidence.
 */
internal object RecorderTickFailurePolicy {
    const val MAX_CONSECUTIVE_FAILURES = 5
    const val LOCK_RETRY_DELAY_MS = 150L

    fun shouldTerminate(type:String,consecutiveFailures:Int):Boolean =
        consecutiveFailures>=MAX_CONSECUTIVE_FAILURES ||
            type=="SQLiteFullException" ||
            type=="SQLiteDatabaseCorruptException" ||
            type=="SQLiteCantOpenDatabaseException"
}
