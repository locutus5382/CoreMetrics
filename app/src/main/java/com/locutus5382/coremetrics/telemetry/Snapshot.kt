package com.locutus5382.coremetrics.telemetry

/** One on-device snapshot. A missing reading remains null, never a fabricated zero. */
data class Snapshot(
    val timeMs: Long,
    val cpuTempC: Double?,
    val gpuTempC: Double?,
    val batteryTempC: Double?,
    val batteryPercent: Int?,
    val cpuPercent: Int?,
    val busiestPercent: Int?,
    val busiestCore: String?,
    val gpuPercent: Int?,
    val gpuMHz: Double?,
    val ramGiB: Double?,
    val cpuClockSummary: String?,
    val appPackage: String?
)
