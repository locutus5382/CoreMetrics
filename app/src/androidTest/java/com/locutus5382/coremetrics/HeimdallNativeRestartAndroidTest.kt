package com.locutus5382.coremetrics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2-I: destructive test for a fresh disposable AOSP emulator ONLY.
 * Kills only the SAME-UID :monitor process, not the instrumentation process.
 * Never runs on a physical Odin / user installation.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallNativeRestartAndroidTest {
    private fun emulatorOnly() =
        Build.HARDWARE.contains("ranchu",true) ||
            Build.HARDWARE.contains("goldfish",true) ||
            Build.FINGERPRINT.contains("generic",true)

    private fun rowCount(db:SessionStore):Long =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM sessions",null)
            .use { cursor -> if(cursor.moveToFirst()) cursor.getLong(0) else 0L }

    private fun send(ctx:Context,action:String) {
        ctx.startForegroundService(Intent(ctx,RecordingService::class.java)
            .setAction(action))
    }
    private fun status(ctx:Context):android.os.Bundle? =
        ctx.contentResolver.call(
            Uri.parse("content://${ctx.packageName}.monitor.status"),
            "status",null,null)

    private fun <T> waitFor(maxMs:Long,probe:()->T?):T {
        val until=SystemClock.elapsedRealtime()+maxMs
        while(SystemClock.elapsedRealtime()<until) {
            probe()?.let { return it }
            Thread.sleep(125)
        }
        throw AssertionError("Timed out waiting for native Heimdall restart evidence")
    }

    @Test fun sameSessionContinuesAfterMonitorProcessIsKilled() {
        assumeTrue("Disposable emulator only",emulatorOnly())
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val db=SessionStore(ctx)
        var originalId=0L
        val initialRows=rowCount(db)
        try {
            assertFalse("A fresh CI emulator must not contain unfinished recordings",
                db.hasUnfinishedRecording())
            assertNull("A fresh CI emulator must not have an active lease",
                status(ctx)?.getString("heimdallRawRecoveryOwner"))
            ActivityScenario.launch(MainActivity::class.java).use {
                send(ctx,RecordingService.HEIMDALL_DEBUG_START)
                originalId=waitFor(40_000) {
                    status(ctx)?.let { observed ->
                        if(observed.getBoolean("running") &&
                            observed.getString("heimdallRawRecoveryOwner")=="HEIMDALL_DEBUG")
                            observed.getLong("id").takeIf { it>0L }
                        else null
                    }
                }
                val oldPid=waitFor(10_000) {
                    status(ctx)?.getInt("heimdallMonitorPid",0)
                        ?.takeIf { pid -> pid>0 && pid!=Process.myPid() }
                }
                assertTrue(db.canResume(originalId))
                assertEquals("A debug START must create exactly one original row",
                    initialRows+1,rowCount(db))
                waitFor(35_000) {
                    if(db.storedSampleCount(originalId)>0L) true else null
                }
                val before=db.storedSampleCount(originalId)

                // Emulator-only controlled :monitor crash: persistent lease
                // and original SQLite session must survive.
                Process.killProcess(oldPid)
                Thread.sleep(500)
                send(ctx,RecordingService.RECOVER)
                waitFor(45_000) {
                    status(ctx)?.let { observed ->
                        if(observed.getBoolean("serviceAlive") &&
                            observed.getBoolean("running") &&
                            observed.getLong("id")==originalId &&
                            observed.getInt("heimdallMonitorPid",0)!=oldPid &&
                            observed.getString("heimdallRawRecoveryOwner")=="HEIMDALL_DEBUG")
                            true else null
                    }
                }
                waitFor(35_000) {
                    if(db.storedSampleCount(originalId)>before) true else null
                }
                assertEquals("Recovery must preserve the original SQLite ID",
                    originalId,status(ctx)?.getLong("id"))
                assertEquals("Recovery must not create a replacement row",
                    initialRows+1,rowCount(db))
                send(ctx,RecordingService.STOP)
                waitFor(40_000) {
                    if(db.sessionById(originalId)?.status=="COMPLETED")
                        true else null
                }
                assertTrue("Persisted samples must survive process death",
                    db.storedSampleCount(originalId)>before)
                waitFor(15_000) {
                    if(status(ctx)?.getString("heimdallRawRecoveryOwner")==null)
                        true else null
                }
            }
        } finally {
            // Failed sessions are intentionally NOT cleared: CI retains the
            // original SQLite/lease evidence until emulator disposal.
            if(originalId>0L && db.canResume(originalId)) runCatching {
                send(ctx,RecordingService.STOP)
            }
            db.close()
        }
    }
}
