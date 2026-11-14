package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.os.PowerManager
import java.io.File
import java.io.Reader
import java.io.BufferedReader

/**
 * One-shot, read-only capability inspection on user-requested diagnostic
 * report export. No recorder-loop calls, Android privileges or DB changes.
 *
 * Accessible procfs/sysfs paths depend on the actual Android ROM/SELinux
 * policy; these reads establish availability only, not telemetry accuracy.
 */
internal object DeviceCapabilityProbe {
    private data class ReadResult(val data:String?,val reason:String) {
        val available:Boolean get()=data!=null
    }

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
        appendLine("Odin/Android read-only capability probe")
        appendLine("Timestamp: "+System.currentTimeMillis()+
            " (at report export; NOT at prior process exit or during gameplay)")
        val power=context.getSystemService(PowerManager::class.java)
        val status=runCatching {power?.currentThermalStatus}
        appendLine("OS thermal status: "+status.fold(
            onSuccess={it?.toString() ?: "unavailable"},
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
        val policy7="/sys/devices/system/cpu/cpufreq/policy7/stats/time_in_state"
        val cpu=readBounded(policy7,4096)
        appendLine("CPU policy7 time_in_state: "+cpu.reason)
        val residence=cpu.data?.let{SystemCapabilityParser.residency(it)}
        if(residence==null) {
            appendLine("CPU policy7 residency data: unavailable or invalid")
        }else{
            appendLine("CPU policy7 freq states: "+residence.states+
                " (min/max ${residence.minKHz}/${residence.maxKHz} kHz)")
            appendLine("CPU policy7 cumulative residency ticks: "+
                residence.cumulativeTicks+" (since kernel counter origin, NOT % load)")
        }
        appendLine("No samples stored; a readable signal is not yet validated for v0.9.7.")
    }
}
