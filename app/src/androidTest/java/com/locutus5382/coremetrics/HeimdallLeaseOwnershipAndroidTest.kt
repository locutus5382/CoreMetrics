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
 * Uses isolated test-only preferences. This suite NEVER starts RecordingService,
 * touches production session data or launches a sampler, even on a real device.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallLeaseOwnershipAndroidTest {
    private fun isolated(name: String): Pair<Context, SharedPreferences> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "heimdall_m2h_isolated_" + name
        val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        check(prefs.edit().clear().commit())
        val wrapper = object : ContextWrapper(app) {
            override fun getSharedPreferences(
                ignoredName: String?, mode: Int
            ): SharedPreferences = prefs
        }
        return wrapper to prefs
    }

    @Test fun leaseWritesNativeOwnerAndSessionIdAtomically() {
        val (sandbox, prefs) = isolated("native")
        try {
            assertTrue(RecorderLease.start(
                sandbox, 451, "Test", "Stock", 2000, false,
                owner = RecorderLease.Owner.HEIMDALL_DEBUG))
            val saved = RecorderLease.recover(sandbox)
            assertNotNull(saved)
            assertEquals(451L, saved!!.id)
            assertEquals(RecorderLease.Owner.HEIMDALL_DEBUG, saved.owner)
            assertEquals("HEIMDALL_DEBUG", prefs.getString("engine_owner", null))
            assertTrue(RecorderLease.hasRawActiveLease(sandbox))
        } finally {
            assertTrue(RecorderLease.clear(sandbox))
            prefs.edit().clear().commit()
        }
    }

    @Test fun existingPreHeimdallLeaseDefaultsToLegacy() {
        val (sandbox, prefs) = isolated("legacy")
        try {
            assertTrue(RecorderLease.start(
                sandbox, 24, "Test", "Stock", 5000, false))
            assertTrue(prefs.edit().remove("engine_owner").commit())
            assertEquals(RecorderLease.Owner.LEGACY, RecorderLease.rawOwner(sandbox))
            assertEquals(RecorderLease.Owner.LEGACY, RecorderLease.recover(sandbox)?.owner)
        } finally {
            RecorderLease.clear(sandbox)
            prefs.edit().clear().commit()
        }
    }

    @Test fun unknownLeaseOwnerIsNeverReinterpretedAsLegacy() {
        val (sandbox, prefs) = isolated("unknown")
        try {
            assertTrue(RecorderLease.start(
                sandbox, 24, "Test", "Stock", 5000, false))
            assertTrue(prefs.edit().putString("engine_owner", "HEIMDALL_FUTURE").commit())
            assertEquals(RecorderLease.Owner.UNKNOWN, RecorderLease.rawOwner(sandbox))
            assertEquals(RecorderLease.Owner.UNKNOWN, RecorderLease.recover(sandbox)?.owner)
        } finally {
            RecorderLease.clear(sandbox)
            prefs.edit().clear().commit()
        }
    }

    @Test fun nativeOwnerSurvivesDurableStopAndIsClearedOnFinalization() {
        val (sandbox, prefs) = isolated("stop")
        try {
            assertTrue(RecorderLease.start(
                sandbox, 17, "Test", "Custom", 5000, false,
                owner = RecorderLease.Owner.HEIMDALL_DEBUG))
            assertTrue(RecorderLease.requestStop(sandbox))
            val saved = RecorderLease.recover(sandbox)
            assertTrue(saved?.stopRequested == true)
            assertEquals(RecorderLease.Owner.HEIMDALL_DEBUG, saved?.owner)
            assertEquals(17L, saved?.id)
            assertTrue(RecorderLease.clear(sandbox))
            assertNull(RecorderLease.rawOwner(sandbox))
            assertNull(prefs.getString("engine_owner", null))
        } finally {
            RecorderLease.clear(sandbox)
            prefs.edit().clear().commit()
        }
    }
}
