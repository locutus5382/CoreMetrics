package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.monitor.MetricWarning
import org.junit.Assert.*
import org.junit.Test

class MetricWarningTest {
    @Test fun colorThresholdsStayDistinctFromSafetyClaims() {
        assertEquals(0,MetricWarning.temperature(null,65.0,80.0))
        assertEquals(0,MetricWarning.temperature(64.9,65.0,80.0))
        assertEquals(1,MetricWarning.temperature(65.0,65.0,80.0))
        assertEquals(2,MetricWarning.temperature(80.0,65.0,80.0))
    }
    @Test fun hysteresisAvoidsColorFlicker() {
        assertEquals(1,MetricWarning.hystereticTemperature(1,64.0,65.0,80.0))
        assertEquals(0,MetricWarning.hystereticTemperature(1,62.9,65.0,80.0))
        assertEquals(2,MetricWarning.hystereticTemperature(2,78.1,65.0,80.0))
        assertEquals(1,MetricWarning.hystereticTemperature(2,77.9,65.0,80.0))
        assertEquals(0,MetricWarning.hystereticTemperature(2,null,65.0,80.0))
    }
}
