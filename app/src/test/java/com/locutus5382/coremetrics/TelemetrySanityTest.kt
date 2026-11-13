package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.TelemetrySanity
import org.junit.Assert.*
import org.junit.Test

class TelemetrySanityTest {
    @Test fun rejectsDuplicatesAndOutOfOrderTimes() {
        TelemetrySanity.requireIncreasing(null,1_000_000L)
        TelemetrySanity.requireIncreasing(1_000_000L,1_005_000L)
        assertThrows(IllegalArgumentException::class.java){
            TelemetrySanity.requireIncreasing(1_000_000L,1_000_000L)
        }
        assertThrows(IllegalArgumentException::class.java){
            TelemetrySanity.requireIncreasing(1_000_000L,999_000L)
        }
    }
    @Test fun rejectsImplausibleNumbersWithoutInventingZeros() {
        assertTrue(TelemetrySanity.valid("cpu_temp_c",55.5))
        assertFalse(TelemetrySanity.valid("cpu_temp_c",300.0))
        assertFalse(TelemetrySanity.valid("gpu_load_pct",120.0))
        assertFalse(TelemetrySanity.valid("busiest_core_pct",-1.0))
        assertTrue(TelemetrySanity.valid("battery_net_w",-5.0))
        assertFalse(TelemetrySanity.valid("battery_net_w",Double.NaN))
        assertFalse(TelemetrySanity.valid("sf_layer_fps",Double.POSITIVE_INFINITY))
    }
}
