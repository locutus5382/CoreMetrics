package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class SystemCapabilityParserTest {
    @Test fun usedSwapIsNotPhysicalCompressedRam() {
        assertEquals(768L,SystemCapabilityParser.swapUsedKiB(
            "MemAvailable: 2000 kB\nSwapTotal: 1024 kB\nSwapFree: 256 kB\n"))
        assertEquals(0L,SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: 1024 kB\nSwapFree: 1024 kB"))
    }
    @Test fun missingOrInconsistentKernelDataRemainsUnavailable() {
        assertNull(SystemCapabilityParser.swapUsedKiB("MemTotal: 2048 kB"))
        assertNull(SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: 512 kB\nSwapFree: 1024 kB"))
        assertNull(SystemCapabilityParser.swapUsedKiB(
            "SwapTotal: invalid kB\nSwapFree: 0 kB"))
    }
}
