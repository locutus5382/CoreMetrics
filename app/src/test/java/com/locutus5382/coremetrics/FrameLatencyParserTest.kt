package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.telemetry.FrameLatencyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameLatencyParserTest {
    @Test fun validPresentStampsYieldLayerRate() {
        val base = 1_000_000_000L
        val rows = (0..10).joinToString("\n") { i ->
            val value = base + i*16_666_667L
            "$value $value $value"
        }
        val result = FrameLatencyParser.parse("16666667\n" + rows, base + 170_000_000L)
        assertEquals(60.0,result.fps!!,0.01)
        assertEquals(16.667,result.frameMs!!,0.01)
    }
    @Test fun denialAndStaleWindowDoNotManufactureFps() {
        assertNull(FrameLatencyParser.parse("Permission Denial: can't dump",null).fps)
        val rows = (0..10).joinToString("\n") { i ->
            val value = 1_000_000_000L + i*16_000_000L
            "$value $value $value"
        }
        assertNull(FrameLatencyParser.parse("16666667\n" + rows,50_000_000_000L).fps)
    }
}
