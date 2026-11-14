package com.locutus5382.coremetrics.monitor

import android.content.Context

/**
 * One WindowManager HUD owner per :monitor process, not per short-lived Service.
 * Android can destroy and recreate a Service in the SAME process. Keeping the
 * owner permits a new Service to rebind callbacks or retry a failed detach
 * without losing an existing overlay handle and drawing a second HUD.
 * It does NOT coordinate separate installed application IDs / processes.
 */
internal object MonitorHudOwner {
    @Volatile private var instance:RecorderHud?=null
    @Synchronized fun get(context:Context):RecorderHud {
        return instance ?: RecorderHud(context.applicationContext).also {
            instance=it
        }
    }
}
