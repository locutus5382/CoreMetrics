package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.monitor.MonitorState
import com.locutus5382.coremetrics.monitor.RecorderLease
import com.locutus5382.coremetrics.quick.QuickTileSnapshot
import com.locutus5382.coremetrics.quick.QuickTileStatePolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No real service, session, database or user preferences are touched. */
@RunWith(AndroidJUnit4::class)
class HeimdallQuickTileRecoveryAndroidTest {
    private fun isolated(name: String): Pair<Context, SharedPreferences> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefs = app.getSharedPreferences("heimdall_m2l_qs_" + name,
            Context.MODE_PRIVATE)
        check(prefs.edit().clear().commit())
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String?, mode: Int) = prefs
        }
        return context to prefs
    }

    private inline fun withMonitorState(
        serviceAlive: Boolean = false,
        phase: String? = null,
        block: () -> Unit,
    ) {
        val wasAlive = MonitorState.serviceCreated
        val wasRunning = MonitorState.running
        val wasStarting = MonitorState.starting
        val wasStandby = MonitorState.standby
        val wasSaving = MonitorState.saving
        val wasPhase = MonitorState.heimdallRecorderPhase
        try {
            MonitorState.serviceCreated = serviceAlive
            MonitorState.running = false
            MonitorState.starting = false
            MonitorState.standby = false
            MonitorState.saving = phase != null
            MonitorState.heimdallRecorderPhase = phase
            block()
        } finally {
            MonitorState.serviceCreated = wasAlive
            MonitorState.running = wasRunning
            MonitorState.starting = wasStarting
            MonitorState.standby = wasStandby
            MonitorState.saving = wasSaving
            MonitorState.heimdallRecorderPhase = wasPhase
        }
    }

    @Test fun expiredUnresolvedLeaseMustNotAppearIdleOrStartable() =
        withMonitorState {
            val (ctx, prefs) = isolated("expired")
            try {
                assertTrue(RecorderLease.start(ctx, 73L,
                    "test", "Stock", 2_000L, false,
                    owner = RecorderLease.Owner.HEIMDALL_DEBUG))
                assertTrue(prefs.edit().putLong("heartbeat_uptime", -1L).commit())
                assertNull(RecorderLease.recover(ctx))
                val mode = QuickTileSnapshot.read(ctx).mode
                assertEquals(QuickTileStatePolicy.Mode.REPAIR_REQUIRED, mode)
                assertEquals(QuickTileStatePolicy.RecordAction.WAIT,
                    QuickTileStatePolicy.recorder(mode))
            } finally {
                RecorderLease.clear(ctx)
                prefs.edit().clear().commit()
            }
        }

    @Test fun unknownEngineOwnerNeverEnablesNewSession() =
        withMonitorState {
            val (ctx, prefs) = isolated("owner")
            try {
                assertTrue(RecorderLease.start(ctx, 74L,
                    "test", "Stock", 5_000L, false))
                assertTrue(prefs.edit().putString("engine_owner", "FUTURE_ENGINE").commit())
                assertEquals(QuickTileStatePolicy.Mode.REPAIR_REQUIRED,
                    QuickTileSnapshot.read(ctx).mode)
            } finally {
                RecorderLease.clear(ctx)
                prefs.edit().clear().commit()
            }
        }

    @Test fun nativeRetryPhasesAllowStopButNeverArmHud() =
        withMonitorState(serviceAlive = true, phase = "RETRY_STOP") {
            val (ctx, prefs) = isolated("retry")
            try {
                val stopMode = QuickTileSnapshot.read(ctx).mode
                assertEquals(QuickTileStatePolicy.Mode.STOP_RETRY, stopMode)
                assertEquals(QuickTileStatePolicy.RecordAction.STOP,
                    QuickTileStatePolicy.recorder(stopMode))
                assertEquals(QuickTileStatePolicy.HudAction.WAIT,
                    QuickTileStatePolicy.hud(stopMode, false))
                MonitorState.heimdallRecorderPhase = "RETRY_SAVE"
                val saveMode = QuickTileSnapshot.read(ctx).mode
                assertEquals(QuickTileStatePolicy.Mode.SAVE_RETRY, saveMode)
                assertEquals(QuickTileStatePolicy.RecordAction.STOP,
                    QuickTileStatePolicy.recorder(saveMode))
            } finally {
                prefs.edit().clear().commit()
            }
        }

    @Test fun eligibleLegacyLeaseCanRecoverExistingSessionWithoutNewStart() =
        withMonitorState {
            val (ctx, prefs) = isolated("legacy")
            try {
                assertTrue(RecorderLease.start(ctx, 41L,
                    "test", "Stock", 5_000L, false))
                val mode = QuickTileSnapshot.read(ctx).mode
                assertEquals(QuickTileStatePolicy.Mode.RECOVERABLE, mode)
                assertEquals(QuickTileStatePolicy.RecordAction.RECOVER,
                    QuickTileStatePolicy.recorder(mode))
            } finally {
                RecorderLease.clear(ctx)
                prefs.edit().clear().commit()
            }
        }
}
