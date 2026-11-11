package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.telemetry.CpuMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuMathTest {
    @Test fun computesBusyDelta() {
        val before = CpuMath.parse("cpu 30 0 20 50 0 0 0")
        val after = CpuMath.parse("cpu 50 0 40 110 0 0 0")
        assertEquals(40, CpuMath.percent(before, after))
    }
    @Test fun rejectsUnavailableOrCounterReset() {
        assertNull(CpuMath.percent(null, CpuMath.parse("cpu 1 2 3 4 5")))
        assertNull(CpuMath.percent(CpuMath.parse("cpu 40 0 30 20 0"),
            CpuMath.parse("cpu 30 0 10 10 0")))
    }
}
