package com.locutus5382.coremetrics.ui

/** Never discard a session selection's source arrays while a document picker
 * or a user-requested export may still need them. SQLite remains persistent.
 */
internal object UiCacheReleasePolicy {
    fun permitted(uiVisible:Boolean,dukoPending:Boolean,legacyPending:Boolean):Boolean =
        !uiVisible && !dukoPending && !legacyPending
}
