package com.locutus5382.coremetrics.data

/** Import boundary: reject implausible numerics, never substitute zero.
 * This does not guess missing sensors or rewrite original recordings.
 */
internal object TelemetrySanity {
    fun valid(key:String,value:Double):Boolean = value.isFinite() && when(key) {
        "cpu_temp_c","gpu_temp_c","battery_temp_c" -> value in -20.0..160.0
        "cpu_load_pct","gpu_load_pct","busiest_core_pct","battery_pct" ->
            value in 0.0..100.0
        "gpu_mhz" -> value in 1.0..2000.0
        "system_ram_used_gib" -> value in 0.0..256.0
        "battery_net_w" -> value in -100.0..100.0
        "sf_layer_fps" -> value in 0.0..1000.0
        "sf_frame_time_ms" -> value in 0.0..100_000.0
        else -> true
    }
    fun requireIncreasing(previous:Long?,current:Long) {
        require(previous==null || current>previous) {
            "CSV sample timestamps must be strictly increasing (no duplicates)"
        }
    }
}
