package com.locutus5382.coremetrics.engine

import com.locutus5382.coremetrics.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeimdallTrialBuildIsolationTest {
    @Test fun trialFlagCanNeverSelectNativeAsProductionDefault() {
        assertFalse("Odin trials must not flip normal START dispatch",
            BuildConfig.HEIMDALL_NATIVE_DEFAULT)
    }

    @Test fun isolatedTrialPackageMustHaveDistinctAppIdentity() {
        if (BuildConfig.HEIMDALL_TRIAL) {
            assertTrue("Trial must be debuggable", BuildConfig.DEBUG)
            assertTrue("Trial must use distinct package ID",
                BuildConfig.APPLICATION_ID == "com.locutus5382.coremetrics.heimdalltrial")
        } else {
            assertFalse("Normal build must not use trial's app ID",
                BuildConfig.APPLICATION_ID ==
                    "com.locutus5382.coremetrics.heimdalltrial")
        }
    }
}
