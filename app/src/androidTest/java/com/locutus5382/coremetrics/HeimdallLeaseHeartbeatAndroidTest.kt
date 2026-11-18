package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.monitor.RecorderLease
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No real RecordingService, SQLite, or user preferences: all tests intercept
 * the lease file name using a unique, isolated sandbox preference store.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallLeaseHeartbeatAndroidTest {
    private fun sandbox(name: String): Pair<Context, SharedPreferences> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefs = app.getSharedPreferences("heimdall_m2k_heartbeat_" + name,
            Context.MODE_PRIVATE)
        check(prefs.edit().clear().commit())
        return object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String?, mode: Int) = prefs
        } to prefs
    }
    private fun start(ctx: Context, id: Long = 73L) = RecorderLease.start(
        ctx, id, "Heartbeat test", "Stock", 2_000L, false,
        owner = RecorderLease.Owner.HEIMDALL_DEBUG)

    @Test fun healthySessionCanSkipDiskWriteBetweenScheduledHeartbeats() {
        val (ctx, prefs) = sandbox("healthy")
        try {
            assertTrue(start(ctx))
            val old = prefs.getLong("heartbeat_uptime", -1L)
            assertEquals(RecorderLease.HeimdallHeartbeat.HEALTHY,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = false))
            assertEquals(old, prefs.getLong("heartbeat_uptime", -1L))
            assertEquals(RecorderLease.HeimdallHeartbeat.RENEWED,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = true))
            assertEquals(73L, RecorderLease.rawSession(ctx).sessionId)
        } finally {
            RecorderLease.clear(ctx)
            prefs.edit().clear().commit()
        }
    }

    @Test fun differentSessionAndLegacyOwnerCannotRenewTheLease() {
        val (ctx, prefs) = sandbox("wrong")
        try {
            assertTrue(start(ctx))
            assertEquals(RecorderLease.HeimdallHeartbeat.OWNER_CHANGED,
                RecorderLease.heartbeatHeimdall(ctx, 99L, renew = true))
            assertTrue(RecorderLease.clear(ctx))
            assertTrue(RecorderLease.start(ctx, 73L, "Legacy", "Stock",
                5_000L, false))
            assertEquals(RecorderLease.HeimdallHeartbeat.OWNER_CHANGED,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = true))
        } finally {
            RecorderLease.clear(ctx)
            prefs.edit().clear().commit()
        }
    }

    @Test fun alreadyPersistedStopMustNotBeExtendedByLateSample() {
        val (ctx, prefs) = sandbox("stop")
        try {
            assertTrue(start(ctx))
            assertTrue(RecorderLease.requestStop(ctx))
            val old = prefs.getLong("heartbeat_uptime", -1L)
            assertEquals(RecorderLease.HeimdallHeartbeat.STOP_ALREADY_PERSISTED,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = true))
            assertEquals(old, prefs.getLong("heartbeat_uptime", -1L))
            assertTrue(RecorderLease.rawSession(ctx).stopPersisted)
        } finally {
            RecorderLease.clear(ctx)
            prefs.edit().clear().commit()
        }
    }

    @Test fun missingOrClearedLeaseIsNotHeartbeatSuccess() {
        val (ctx, prefs) = sandbox("missing")
        try {
            assertEquals(RecorderLease.HeimdallHeartbeat.OWNER_CHANGED,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = false))
            assertTrue(start(ctx))
            assertTrue(RecorderLease.clear(ctx))
            assertEquals(RecorderLease.HeimdallHeartbeat.OWNER_CHANGED,
                RecorderLease.heartbeatHeimdall(ctx, 73L, renew = true))
        } finally {
            RecorderLease.clear(ctx)
            prefs.edit().clear().commit()
        }
    }

    @Test fun synchronousCommitFalseMustNeverReportSuccessfulHeartbeat() {
        val (ctx, prefs) = sandbox("commit")
        try {
            assertTrue(start(ctx))
            val previous = prefs.getLong("heartbeat_uptime", -1L)
            val failedPrefs = object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor =
                    object : SharedPreferences.Editor by prefs.edit() {
                        // Simulate fsync/persistence failure without writing.
                        override fun commit(): Boolean = false
                    }
            }
            val failedCtx = object : ContextWrapper(ctx) {
                override fun getSharedPreferences(name: String?, mode: Int) =
                    failedPrefs
            }
            assertEquals(RecorderLease.HeimdallHeartbeat.COMMIT_FAILED,
                RecorderLease.heartbeatHeimdall(failedCtx, 73L, renew = true))
            assertEquals(previous, prefs.getLong("heartbeat_uptime", -1L))
            assertEquals(RecorderLease.Owner.HEIMDALL_DEBUG,
                RecorderLease.rawOwner(ctx))
        } finally {
            RecorderLease.clear(ctx)
            prefs.edit().clear().commit()
        }
    }
}
