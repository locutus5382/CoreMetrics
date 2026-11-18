package com.locutus5382.coremetrics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
 * DESTRUCTIVE to disposable emulator app state ONLY: creates one genuine SQLite
 * session via the debug Heimdall engine. Never executed on physical devices.
 *
 * Must run in its own fresh API33 emulator group. Not a substitute for Odin
 * lifecycle testing or process-death recovery.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallDebugServiceAndroidTest {
    private fun emulatorOnly(): Boolean =
        Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.FINGERPRINT.contains("generic", true)

    private fun send(ctx: Context, action: String) {
        ctx.startForegroundService(
            Intent(ctx, RecordingService::class.java).setAction(action))
    }

    private fun status(ctx: Context): android.os.Bundle? =
        ctx.contentResolver.call(
            Uri.parse("content://${ctx.packageName}.monitor.status"),
            "status", null, null)

    private fun <T> waitFor(maxMs: Long, read: () -> T?): T {
        val until=SystemClock.elapsedRealtime()+maxMs
        while(SystemClock.elapsedRealtime()<until) {
            read()?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for Heimdall debug lifecycle")
    }

    @Test fun debugServiceUsesOnlyOneEngineAndPersistsOriginalSession() {
        assumeTrue("For disposable CI emulators only", emulatorOnly())
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db=SessionStore(ctx)
        var id=0L
        try {
            assertNull("CI emulator must begin without a pending owner lease",
                status(ctx)?.getString("heimdallRawRecoveryOwner"))
            assertFalse("CI emulator must begin without active SQLite sessions",
                db.hasUnfinishedRecording())
            ActivityScenario.launch(MainActivity::class.java).use {
                send(ctx, RecordingService.HEIMDALL_DEBUG_START)
                id=waitFor(30_000) {
                    val state=status(ctx)
                    // Display copy can change. Engine phase and sampled
                    // status, not a localized message prefix, are the
                    // authoritative recording assertions.
                    if(state?.getBoolean("running")==true &&
                        state.getString("heimdallRecorderPhase")=="RECORDING") {
                        state.getLong("id").takeIf { sid -> sid>0L }
                    } else null
                }
                assertEquals("HEIMDALL_DEBUG",
                    status(ctx)?.getString("heimdallRawRecoveryOwner"))
                // A stale app/QS START during native recording MUST NOT open a
                // competing legacy session or replace the original lease.
                send(ctx, RecordingService.START)
                Thread.sleep(300)
                assertEquals(id, status(ctx)?.getLong("id"))
                assertEquals(id,
                    status(ctx)?.getLong("heimdallRecoveryLeaseId"))

                waitFor(35_000) {
                    if(db.storedSampleCount(id)>0L) true else null
                }
                send(ctx, RecordingService.STOP)
                waitFor(30_000) {
                    db.sessionById(id)?.takeIf { row ->
                        row.status=="COMPLETED" }
                }
                assertTrue("At least one real sample must be retained",
                    db.storedSampleCount(id)>0L)
                assertFalse("Original session must no longer be RECORDING",
                    db.canResume(id))
                waitFor(15_000) {
                    if(status(ctx)?.getString("heimdallRawRecoveryOwner")==null)
                        true else null
                }
            }
        } finally {
            // Do not clear the lease or database on a failed test: the
            // persisted evidence is needed to diagnose engine recovery bugs.
            // STOP is best-effort and routes to the recorded service owner.
            if(id>0L && db.canResume(id)) runCatching {
                send(ctx, RecordingService.STOP)
            }
            db.close()
        }
    }
}
