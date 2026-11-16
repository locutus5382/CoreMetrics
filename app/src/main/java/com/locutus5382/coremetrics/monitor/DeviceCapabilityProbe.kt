package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.locutus5382.coremetrics.telemetry.CpuPolicyTelemetry
import com.locutus5382.coremetrics.telemetry.GpuTelemetryProbe
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-shot, read-only capability inspection on user-requested diagnostic
 * report export. No recorder-loop calls, Android privileges or DB changes.
 *
 * Accessible procfs/sysfs paths depend on the actual Android ROM/SELinux
 * policy; these reads establish availability only, not telemetry accuracy.
 */
internal object DeviceCapabilityProbe {
    private data class ReadResult(val data:String?,val reason:String)

    /** A strict size cap also protects against unexpected kernel file content. */
    private fun readBounded(path:String,limit:Int=2048):ReadResult {
        return try {
            val result=File(path).bufferedReader().use { reader ->
                val buffer=CharArray(limit+1)
                var used=0
                while(used<buffer.size) {
                    val n=reader.read(buffer,used,buffer.size-used)
                    if(n<0)break
                    if(n==0)break
                    used+=n
                }
                if(used>limit)return ReadResult(null,"too large for probe")
                String(buffer,0,used)
            }
            if(result.isBlank())ReadResult(null,"empty")
            else ReadResult(result,"readable")
        }catch(e:Exception){
            ReadResult(null,"unavailable ("+e.javaClass.simpleName+")")
        }
    }

    fun report(context:Context):String=buildString {
        appendLine("Android read-only device capability probe")
        appendLine("Hardware model: "+Build.MANUFACTURER+" / "+Build.MODEL+
            " · Android API "+Build.VERSION.SDK_INT)
        appendLine("Device model is context, not confirmation of specific kernel limits")
        appendLine("Timestamp: "+
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(Date())+
            " (at report export; NOT at prior process exit or during gameplay)")
        val power=context.getSystemService(PowerManager::class.java)
        val status=runCatching {power?.currentThermalStatus}
        appendLine("OS thermal status: "+status.fold(
            onSuccess={it?.let{n->n.toString()+" ("+
                SystemCapabilityParser.thermalStatusName(n)+")"} ?: "unavailable"},
            onFailure={"unavailable ("+it.javaClass.simpleName+")"})+
            " (Android policy classification, not proof of clock throttling)")
        val rawHeadroom=runCatching {power?.getThermalHeadroom(10)}
        val headroom=rawHeadroom.getOrNull()
        appendLine("OS thermal headroom forecast(10s): "+when {
            rawHeadroom.isFailure->"unavailable ("+
                rawHeadroom.exceptionOrNull()?.javaClass?.simpleName+")"
            headroom==null || !headroom.isFinite() || headroom<0f->
                "unavailable (unsupported/NaN)"
            else->headroom.toString()+" (normalized proximity to thermal limit)"
        })
        // Battery counters can support future cumulative-energy sanity checks,
        // but access/readability alone does NOT validate counter update cadence.
        val battery=context.getSystemService(BatteryManager::class.java)
        val batteryIntent=runCatching {
            context.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val voltage=batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1)
            ?.takeIf{it in 2500..6000}
        appendLine("Battery broadcast voltage: "+
            (voltage?.let{it.toString()+" mV"}?:"unavailable")+
            " (broadcast update age not guaranteed)")
        for((title,prop,unit) in listOf(
            Triple("Current now",BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,"uA"),
            Triple("Charge counter",BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,"uAh"),
            Triple("Energy counter",BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER,"nWh")
        )) {
            val v=runCatching {battery?.getLongProperty(prop)}.getOrNull()
                ?.takeUnless{it==Long.MIN_VALUE || it==Int.MIN_VALUE.toLong()}
            appendLine("Battery "+title+": "+
                (v?.let{it.toString()+" "+unit} ?: "unavailable")+
                " (one-shot value, NOT battery-terminal W or game energy)")
        }
        for(path in listOf("/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/voltage_now")) {
            val source=readBounded(path,128)
            appendLine("Battery sysfs "+path.substringAfterLast('/')+": "+
                (source.data?.trim()?.take(64)?:source.reason)+
                " (raw kernel units; vendor validation required)")
        }
        val energyDesign=BatteryDesignCapacity.fromEnergyMicroWh(
            readBounded("/sys/class/power_supply/battery/energy_full_design",128).data)
        val chargeDesign=BatteryDesignCapacity.fromChargeMicroAhAndNominalMicroV(
            readBounded("/sys/class/power_supply/battery/charge_full_design",128).data,
            readBounded("/sys/class/power_supply/battery/voltage_min_design",128).data)
        val design=energyDesign?:chargeDesign
        appendLine("Battery design energy candidate: "+
            (design?.let{
                String.format(Locale.US,"%.2f Wh [%s]",it.designWh,it.provenance)
            }?:"unavailable")+
            " (NOT validated usable capacity; runtime estimation disabled)")
        val meminfo=readBounded("/proc/meminfo",8192)
        val swap=meminfo.data?.let{SystemCapabilityParser.swapFieldsKiB(it)}
        appendLine("proc/meminfo: "+meminfo.reason)
        appendLine("SwapTotal / SwapFree / SwapUsed: "+
            if(swap!=null)
                "${swap.totalKiB} / ${swap.freeKiB} / ${swap.usedKiB} KiB"
            else "unavailable (missing or invalid swap fields)")
        appendLine("SwapUsed is swap allocation, NOT physical zRAM usage/PSI")
        val psi=readBounded("/proc/pressure/memory",2048)
        appendLine("Memory PSI path: "+psi.reason)
        val psiLines=psi.data?.let{SystemCapabilityParser.psiLines(it)}.orEmpty()
        if(psiLines.isEmpty())appendLine("Memory PSI: unavailable")
        else psiLines.forEach{appendLine("Memory PSI: "+it)}
        // Compare vendor GPU clock sources once, explicitly on diagnostic
        // request. A readable but constant 401 MHz value is not validated as
        // true frequency scaling merely because sysfs returns a number.
        for((label,path) in listOf(
            "devfreq cur_freq" to "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "kgsl gpuclk" to "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "devfreq max_freq" to "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        )) {
            val source=readBounded(path,128)
            val raw=source.data?.trim()?.take(64)
            appendLine("GPU frequency source "+label+": "+
                (raw ?: source.reason)+" (raw kernel units; no scaling claim)")
        }
        val gpu=GpuTelemetryProbe(
            read={path->readBounded(path,128).data},
            devfreqPaths={
                GpuTelemetryProbe.gpuDevfreqCandidates(
                    File("/sys/class/devfreq").listFiles()?.map{it.name}.orEmpty())
            }).sample(android.os.SystemClock.elapsedRealtime())
        appendLine("GPU utilization source: "+gpu.source)
        appendLine("GPU readable load: "+(gpu.loadPct?.toString()?:"unavailable")+
            "%; clock "+(gpu.frequencyMHz?.toString()?:"unavailable")+
            " MHz (readability is NOT validation)")
        val root=File("/sys/devices/system/cpu/cpufreq")
        val names=SystemCapabilityParser.cpuPolicyNames(
            root.listFiles()?.map{it.name}.orEmpty())
        appendLine("CPU frequency policies discovered: "+
            names.ifEmpty{listOf("unavailable")}.joinToString(", "))
        for(name in names) {
            val related=CpuPolicyTelemetry.relatedCpus(
                readBounded(File(root,"$name/related_cpus").path,256).data)
            appendLine("CPU $name related logical cores: "+
                if(related.isEmpty())"unavailable"
                else related.joinToString(","))
            val source=readBounded(File(root,"$name/stats/time_in_state").path,4096)
            val residence=source.data?.let{SystemCapabilityParser.residency(it)}
            if(residence==null)
                appendLine("CPU $name residency: "+source.reason+" / not validated")
            else {
                appendLine("CPU $name states: "+residence.states+
                    " (min/max ${residence.minKHz}/${residence.maxKHz} kHz)")
                appendLine("CPU $name cumulative ticks: "+residence.cumulativeTicks+
                    " (NOT thread affinity, per-game performance or throttling proof)")
            }
        }
        appendLine("This report is a one-shot capability snapshot, not a saved gameplay sample. " +
            "v0.9.9.0 records supported thermal, swap and PSI signals only during REC; " +
            "actual values still require Odin ROM validation.")
    }
}
