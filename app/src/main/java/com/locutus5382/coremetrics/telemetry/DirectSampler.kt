package com.locutus5382.coremetrics.telemetry

import android.app.Application
import android.content.IntentFilter
import android.content.Intent
import android.os.BatteryManager
import java.io.File
import kotlin.math.roundToInt

/**
 * Read-only direct sampling. One collector in :monitor, only while a user session
 * is active. Every unavailable source remains null. No root / PServer / writes.
 */
class DirectSampler(private val app: Application) {
    private val batteryPower = BatteryPowerReader(app)
    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val policyName = Regex("""policy\d+""")
    private val coreName = Regex("""cpu\d+""")
    private val whitespace = Regex("""\s+""")
    // Cache policy *paths* only, never frequency values. Retry discovery if
    // no directories were readable at first sampling (e.g. early boot).
    private var policyDirectories: List<File>? = null
    private fun cpuPolicies(): List<File> {
        val cached = policyDirectories
        if (!cached.isNullOrEmpty()) return cached
        val found = File("/sys/devices/system/cpu/cpufreq").listFiles()
            ?.filter { it.name.matches(policyName) }
            ?.sortedBy { it.name.drop(6).toIntOrNull() ?: Int.MAX_VALUE }
            ?.take(12).orEmpty()
        if (found.isNotEmpty()) policyDirectories = found
        return found
    }
    private var priorCpu: CpuMath.Times? = null
    private val priorCore = mutableMapOf<String, CpuMath.Times>()
    private data class Sensor(val type: String, val path: String)
    private val thermals: List<Sensor> by lazy {
        (0..127).mapNotNull { i ->
            val root = "/sys/class/thermal/thermal_zone" + i
            val type = read(root + "/type")?.lowercase() ?: return@mapNotNull null
            Sensor(type, root + "/temp")
        }
    }
    private fun read(path: String): String? = try {
        File(path).takeIf { it.isFile && it.canRead() }
            ?.bufferedReader()?.use { it.readText().trim().take(16384) }
    } catch (_: Exception) { null }

    private fun thermal(cpu: Boolean): Double? {
        val items = thermals.filter { sensor ->
            if (cpu) sensor.type.startsWith("cpu-") || sensor.type.startsWith("cpuss-")
            else sensor.type.startsWith("gpu-") || sensor.type.startsWith("gpuss-")
        }
        return items.mapNotNull { sensor ->
            read(sensor.path)?.toDoubleOrNull()?.div(1000.0)?.takeIf { it in -20.0..160.0 }
        }.maxOrNull()
    }

    private fun battery(intent: Intent?): Triple<Double?,Int?,Boolean> {
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it in -200..1500 }?.div(10.0)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (scale > 0 && level in 0..scale)
            (level * 100.0 / scale).roundToInt() else null
        return Triple(temp, percent, intent != null)
    }

    private fun cpuClockSummary(): String? {
        val parts = cpuPolicies().asSequence()
            .mapNotNull { policy ->
                val khz = read(policy.path + "/scaling_cur_freq")?.toLongOrNull()
                    ?.takeIf { it in 1..10_000_000 } ?: return@mapNotNull null
                policy.name + " " + String.format(java.util.Locale.US, "%.2f GHz", khz / 1e6)
            }?.toList().orEmpty()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun gpuClock(): Double? {
        for (path in listOf("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk")) {
            val value = read(path)?.toLongOrNull()?.takeIf { it > 0 } ?: continue
            return if (value > 2_000_000) value / 1e6 else value / 1000.0
        }
        return null
    }

    private fun gpuLoad(): Int? {
        val root = "/sys/class/kgsl/kgsl-3d0/"
        val primary = read(root + "gpu_busy_percentage")
            ?.substringBefore(" ")?.removeSuffix("%")?.trim()?.toIntOrNull()
        if (primary != null && primary in 0..100) return primary
        val values = read(root + "gpubusy")?.split(whitespace)
            ?.mapNotNull { it.toLongOrNull() }.orEmpty()
        return if (values.size >= 2 && values[1] > 0 && values[0] in 0..values[1])
            (values[0] * 100.0 / values[1]).roundToInt() else null
    }

    private fun cpuLimits(): String? {
        // Read-only, instantaneous policy limits. Never infer which tuner set
        // them; never use observed scaling_cur_freq as a cap.
        val names = listOf("policy0", "policy3", "policy7")
        val parts = names.mapNotNull { p ->
            val path = "/sys/devices/system/cpu/cpufreq/" + p + "/"
            val ceiling = read(path + "scaling_max_freq")?.toLongOrNull()
                ?.takeIf { it in 100_000L..5_000_000L }
                ?: return@mapNotNull null
            val hardware = read(path + "cpuinfo_max_freq")?.toLongOrNull()
                ?.takeIf { it in 100_000L..5_000_000L }
            val limited = hardware != null && ceiling < hardware
            p + " max " + String.format(java.util.Locale.US, "%.2f", ceiling/1e6) +
                " GHz" + if (limited) " (below hw)" else ""
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun sample(appPackage: String?, frame: FrameEstimate? = null): Snapshot {
        val now = System.currentTimeMillis()
        val lines = read("/proc/stat")?.lineSequence()?.toList().orEmpty()
        val current = CpuMath.parse(lines.firstOrNull())
        val cpuPercent = CpuMath.percent(priorCpu, current)
        priorCpu = current
        val perCore = lines.asSequence().mapNotNull { line ->
            val label = line.substringBefore(" ")
            if (!label.matches(coreName)) return@mapNotNull null
            val ticks = CpuMath.parse(line) ?: return@mapNotNull null
            val load = CpuMath.percent(priorCore[label], ticks)
            priorCore[label] = ticks
            if (load == null) null else label to load
        }.toList()
        val peak = CpuMath.uniqueBusiest(perCore)
        val mem = read("/proc/meminfo")
        fun kb(key: String): Long? = mem?.lineSequence()?.firstOrNull { it.startsWith(key + ":") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')
            ?.toLongOrNull()
        val total = kb("MemTotal"); val available = kb("MemAvailable")
        val ram = if (total != null && available != null && available in 0..total)
            (total - available) / 1048576.0 else null
        // One sticky broadcast fetch per sample. Both temperature/percentage
        // and electrical-power status interpret precisely the SAME snapshot.
        val batteryIntent = try {
            app.registerReceiver(null,batteryFilter)
        } catch (_: Exception) { null }
        val battery = battery(batteryIntent)
        val power = batteryPower.read(batteryIntent)
        return Snapshot(
            timeMs = now, cpuTempC = thermal(true), gpuTempC = thermal(false),
            batteryTempC = battery.first, batteryPercent = battery.second,
            cpuPercent = cpuPercent, busiestPercent = peak?.second, busiestCore = peak?.first,
            gpuPercent = gpuLoad(), gpuMHz = gpuClock(), ramGiB = ram,
            cpuClockSummary = cpuClockSummary(), appPackage = appPackage,
            signedBatteryWatts = power.signedBatteryWatts,
            batteryCurrentMicroAmps = power.currentMicroAmps,
            batteryVoltageMilliVolts = power.voltageMilliVolts,
            batteryPowerSource = power.source,
            batteryPowerStatus = power.status,
            surfaceFps = frame?.fps,
            surfaceFrameMs = frame?.frameMs,
            surfaceFpsStatus = frame?.status ?: "OFF_BY_DEFAULT",
            observedCpuLimits = cpuLimits(),
            perCoreLoads = perCore.joinToString(";") { it.first + ":" + it.second }
        )
    }
}
