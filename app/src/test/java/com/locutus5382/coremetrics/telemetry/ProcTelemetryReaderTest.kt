package com.locutus5382.coremetrics.telemetry

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class ProcTelemetryReaderTest {
    @Test fun cpuRowsStopAtInterruptLineWithoutHardcodingCpu7() {
        val source=buildString {
            append("cpu  100 0 25 500 0 0 0 0 0 0\n")
            (0..15).forEach{append("cpu$it  10 0 2 50 0 0 0 0 0 0\n")}
            append("intr 123456 9999 8888\n")
            append("cpu16 99 99 99 99\n")
        }
        val rows=ProcTelemetryReader.cpuLines(StringReader(source))
        assertEquals(17,rows.size)
        assertTrue(rows.first().startsWith("cpu  "))
        assertTrue(rows.last().startsWith("cpu15 "))
    }

    @Test fun invalidCpuStreamFailsClosed() {
        assertTrue(ProcTelemetryReader.cpuLines(StringReader("intr 123\ncpu 1\n"))
            .isEmpty())
        assertEquals(2,ProcTelemetryReader.cpuLines(
            StringReader("cpu 1 2 3 4\ncpu0 2 3 4 5\nsoftirq 9\n")).size)
    }

    @Test fun memInfoAllowsReorderedFieldsAndReturnsGiB() {
        val mem="Buffers: 120 kB\nMemAvailable: 1048576 kB\n"+
            "Cached: 32768 kB\nMemTotal: 8388608 kB\n"+
            "Slab: 123 kB\n"
        assertEquals(7.0,ProcTelemetryReader.usedGiB(StringReader(mem))!!,0.00001)
    }

    @Test fun memoryReadersReportUnavailableInsteadOfZero() {
        assertNull(ProcTelemetryReader.usedGiB(StringReader(
            "MemTotal: 100 kB\nBuffers: 20 kB\n")))
        assertNull(ProcTelemetryReader.usedGiB(StringReader(
            "MemTotal: 100 kB\nMemAvailable: 200 kB\n")))
        assertNull(ProcTelemetryReader.usedGiB(StringReader(
            "MemTotal: invalid kB\nMemAvailable: 20 kB\n")))
        assertNull(ProcTelemetryReader.usedGiB(StringReader(
            "MemTotal: 0 kB\nMemAvailable: 0 kB\n")))
    }

    @Test fun cpuParseCapsMalformedUnboundedInput() {
        val input=(0..200).joinToString("\n"){"cpu$it 1 2 3 4"}+"\n"
        assertEquals(64,ProcTelemetryReader.cpuLines(StringReader(input)).size)
    }
}
