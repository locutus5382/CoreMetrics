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
import com.locutus5382.coremetrics.monitor.RecorderLease
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic process-death boundary: original session row + HEIMDALL_DEBUG
 * lease already contain a durable STOP, but no service/sampler survives.
 *
 * Uses a disposable CI emulator ONLY. It must be its own test group, because
 * SessionStore.begin() is a genuine persistent user-database operation.
 * No physical-device or normal-install execution permitted.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallTerminalRecoveryAndroidTest {
    private fun emulatorOnly(): Boolean =
        Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.FINGERPRINT.contains("generic", true)

    private fun waitFor(ms: Long, condition: () -> Boolean) {
        val deadline=SystemClock.elapsedRealtime()+ms
        while(SystemClock.elapsedRealtime()<deadline) {
            if(condition()) return
            Thread.sleep(125)
        }
        throw AssertionError("Native persisted-STOP reconciliation timed out")
    }

    private fun owner(ctx: Context): String? =
        ctx.contentResolver.call(
            Uri.parse("content://${ctx.packageName}.monitor.status"),
            "status", null, null)?.getString("heimdallRawRecoveryOwner")

    private fun rowCount(db: SessionStore): Long =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM sessions", null).use {
            if(it.moveToFirst()) it.getLong(0) else 0L
        }

    @Test fun persistedStopAfterSyntheticCrashFinalizesWithoutRestartingSampler() {
        assumeTrue("Disposable AOSP emulator only", emulatorOnly())
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db=SessionStore(ctx)
        var originalId=0L
        try {
            assertFalse("Fresh CI emulator required", db.hasUnfinishedRecording())
            // Do not start the monitor provider before the persisted lease is
            // committed; this avoids cross-process preferences caching.
            assertFalse("Fresh CI emulator required",
                RecorderLease.hasRawActiveLease(ctx))
            val baseline=rowCount(db)
            originalId=db.begin("Heimdall recovery test", "Stock",
                System.currentTimeMillis())
            assertTrue(RecorderLease.start(ctx, originalId,
                "Heimdall recovery test", "Stock", 2_000L, false,
                owner=RecorderLease.Owner.HEIMDALL_DEBUG))
            assertTrue("STOP is persisted before crash/restart",
                RecorderLease.requestStop(ctx))
            val initialEnd=db.sessionById(originalId)?.endedAt
            assertNull(initialEnd)
            assertEquals(0L, db.storedSampleCount(originalId))

            ActivityScenario.launch(MainActivity::class.java).use {
                ctx.startForegroundService(Intent(ctx, RecordingService::class.java)
                    .setAction(RecordingService.RECOVER))
                waitFor(45_000) {
                    db.sessionById(originalId)?.status == "COMPLETED"
                }
                assertEquals("Original session row only",
                    baseline+1L, rowCount(db))
                assertNotNull(db.sessionById(originalId)?.endedAt)
                assertEquals("Persisted STOP must never restart sampling",
                    0L, db.storedSampleCount(originalId))
                waitFor(15_000) { owner(ctx) == null }
                assertFalse(db.canResume(originalId))
            }
        } finally {
            // If this fails, don't erase its original row or recovery lease.
            // The CI emulator is discarded and its logs retained.
            db.close()
        }
    }
}
