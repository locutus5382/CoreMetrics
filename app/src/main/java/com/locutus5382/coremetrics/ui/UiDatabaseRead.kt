package com.locutus5382.coremetrics.ui

import android.content.Context
import com.locutus5382.coremetrics.monitor.RecorderJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explicit, cancellable UI-only read boundary. Never interprets database
 * errors as an empty database, and never swallows coroutine cancellation.
 * The caller retains existing data and surfaces an actionable error message.
 */
internal suspend fun <T> guardedUiDatabaseRead(
    context:Context,operation:String,read:()->T
):Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(read())
    } catch(e:CancellationException) {
        throw e
    } catch(e:Exception) {
        RecorderJournal.event(context.applicationContext,"UI_DB_READ_FAILED",
            operation.take(40)+" "+e.javaClass.simpleName)
        Result.failure(e)
    }
}
