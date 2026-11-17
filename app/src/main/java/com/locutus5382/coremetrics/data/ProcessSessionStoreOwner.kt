package com.locutus5382.coremetrics.data

import android.content.Context

/**
 * Process-local shared SQLiteOpenHelper; Android creates a distinct singleton
 * in the UI process and the :monitor process. Reuses WAL connection/cache
 * without retaining Activity or TileService instances.
 *
 * Crucially this is NOT a cached recording-settings snapshot: every settings
 * read still queries SQLite, preserving cross-process source-of-truth rules.
 */
internal object ProcessSessionStoreOwner {
    @Volatile private var store:SessionStore?=null
    @Synchronized fun get(context:Context):SessionStore =
        store ?: SessionStore(context.applicationContext).also {store=it}
}
