package com.locutus5382.coremetrics.data

import android.content.Context

/** Legacy UI alias. Both UI and monitor reuse one distinct store per process.
 * Never close it from Activity.onDestroy while exports may hold live cursors.
 */
internal object UiSessionStoreOwner {
    fun get(context:Context):SessionStore=ProcessSessionStoreOwner.get(context)
}
