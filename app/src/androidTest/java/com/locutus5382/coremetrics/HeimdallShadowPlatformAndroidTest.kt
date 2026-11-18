package com.locutus5382.coremetrics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.monitor.HeimdallLegacyStatusProbe
import com.locutus5382.coremetrics.monitor.HeimdallLegacyRecoveryProbe
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the existing private IPC contract is readable on API 33+. */
@RunWith(AndroidJUnit4::class)
class HeimdallShadowPlatformAndroidTest {
    @Test fun providerStatusCanBeObservedWithoutRequestingRecorderCommands() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = HeimdallLegacyStatusProbe(context).read()
        // Other suites may already have used the provider or service. Do not
        // assert Idle or fake a particular lifecycle/lease state here.
        assertTrue("Private status contract unavailable: $result",
            result is HeimdallLegacyStatusProbe.Result.Observed)
    }

    @Test fun recoveryEvidenceReadNeverRequiresANewSession() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val candidate = HeimdallLegacyRecoveryProbe(context).inspect()
        // Nothing is opened, closed, resumed or modified by inspect(). If the
        // device has an eligible lease, its historical ID must be preserved.
        if (candidate.requiresServiceReconciliation) {
            assertTrue("A recovery candidate must reference the existing row",
                (candidate.sessionId ?: 0L) > 0L)
        }
    }
}
