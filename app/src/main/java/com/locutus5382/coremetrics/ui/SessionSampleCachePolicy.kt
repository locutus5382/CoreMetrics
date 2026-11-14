package com.locutus5382.coremetrics.ui

/**
 * Only switch recordings or an actual increase/change in persisted sample
 * count may invalidate the expensive SQLite telemetry cache.
 * Driver, scale, note, profile and warm-up edits are analysis-only changes.
 */
internal object SessionSampleCachePolicy {
    fun reload(page:String,selectedId:Long,loadedId:Long,
        currentCount:Int?,cachedCount:Int):Boolean =
        page=="Sessions" && selectedId>0L &&
            (loadedId!=selectedId ||
                currentCount!=null && currentCount!=cachedCount)
}
