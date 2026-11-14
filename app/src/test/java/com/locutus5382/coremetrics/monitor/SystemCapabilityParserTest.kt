package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class SystemCapabilityParserTest {
    @Test fun swapFieldsAreExplicitlySeparated() {
        val parsed=SystemCapabilityParser.swapFieldsKiB(
            "MemAvailable: 2000 kB\nSwapTotal: 1024 kB\nSwapFree: 256 kB\n")
        assertNotNull(parsed)
        assertEquals(1024L,parsed?.totalKiB)
        assertEquals(256L,parsed?.freeKiB)
        assertEquals(768L,parsed?.usedKiB)
        assertEquals(0L,SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: 1024 kB\nSwapFree: 1024 kB"))
    }
    @Test fun missingOrInconsistentSwapIsUnavailable() {
        assertNull(SystemCapabilityParser.swapUsedKiB("MemTotal: 2048 kB"))
        assertNull(SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: 512 kB\nSwapFree: 1024 kB"))
        assertNull(SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: invalid kB\nSwapFree: 0 kB"))
    }
    @Test fun frequencyResidencyValidatesCountsAndNeverAssumesUtilization() {
        val r=SystemCapabilityParser.residency(
            "300000 120\n672000 250\n2841600 30\n")
        assertNotNull(r)
        assertEquals(3,r?.states)
        assertEquals(300000L,r?.minKHz)
        assertEquals(2841600L,r?.maxKHz)
        assertEquals(400L,r?.cumulativeTicks)
        assertNull(SystemCapabilityParser.residency("permission denied"))
        assertNull(SystemCapabilityParser.residency("400000 -1"))
    }
    @Test fun psiExtractsOnlySomeAndFullLines() {
        val ps=SystemCapabilityParser.psiLines(
            "some avg10=0.10 avg60=0.20 avg300=0.30 total=100\n"+
            "full avg10=0.00 avg60=0.00 avg300=0.00 total=0\n"+
            "irrelevant data\n")
        assertEquals(2,ps.size)
        assertTrue(ps[0].startsWith("some "))
        assertTrue(ps[1].startsWith("full "))
        assertTrue(SystemCapabilityParser.psiLines("not supported").isEmpty())
    }
}
