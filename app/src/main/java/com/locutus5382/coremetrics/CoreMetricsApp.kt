package com.locutus5382.coremetrics

import android.app.Application
import android.os.StrictMode

/**
 * Debug-only diagnostics for accidental main-thread disk access and leaked
 * SQLite or Closeable resources, in BOTH Android app processes.
 *
 * These checks only LOG: they do not terminate recordings or modify telemetry.
 * R8 optimizedTest and release builds never turn them on.
 */
class CoreMetricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
