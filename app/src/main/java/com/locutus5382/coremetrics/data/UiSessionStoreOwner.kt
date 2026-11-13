package com.locutus5382.coremetrics.data

import android.content.Context

/**
 * One SQLiteOpenHelper per UI process, owned by application context rather
 * than a short-lived Activity. Activity.onDestroy must not close the database
 * while a running IO export or analysis still holds a live SQLite cursor.
 * The recorder's :monitor process has its own independent SQLite connection.
 */
internal object UiSessionStoreOwner {
    @Volatile private var store:SessionStore?=null
    @Synchronized fun get(context:Context):SessionStore =
        store ?: SessionStore(context.applicationContext).also{store=it}
}
