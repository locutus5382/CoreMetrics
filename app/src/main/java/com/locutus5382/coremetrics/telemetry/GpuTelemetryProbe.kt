package com.locutus5382.coremetrics.telemetry

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Conservative read-only GPU source adapters. Existence/readability alone is
 * NOT validation of accuracy, scaling or per-game rendering. Missing sources
 * never become 0% or a fictitious clock.
 *
 * The first usable source path is cached. When it stops working, rediscovery
 * is bounded so inaccessible vendor nodes are not polled continuously.
 */
internal class GpuTelemetryProbe(
    private val read:(String)->String?,
    private val devfreqPaths:()->List<String>
) {
    data class Reading(
        val loadPct:Int?,
        val frequencyMHz:Double?,
        val source:String
    )
    private data class Source(val path:String,val type:String)
    private var selectedLoad:Source?=null
    private var selectedClock:Source?=null
    private var retryLoadAtMs=0L
    private var retryClockAtMs=0L

    private val loads=listOf(
        Source("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage","KGSL_PERCENT"),
        Source("/sys/class/kgsl/kgsl-3d0/gpubusy","KGSL_BUSY_RATIO"),
        Source("/sys/class/misc/mali0/device/utilization","MALI_PERCENT"),
        Source("/sys/class/misc/mali0/device/gpu_utilization","MALI_PERCENT"),
        Source("/sys/devices/platform/mali.0/utilization","MALI_PERCENT")
    )
    private val clockPaths=listOf(
        Source("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq","KGSL_CLOCK"),
        Source("/sys/class/kgsl/kgsl-3d0/gpuclk","KGSL_CLOCK")
    )

    companion object {
        private val percent=Regex("""^([0-9]{1,3})\s*%?$""")
        fun load(raw:String?,type:String):Int? {
            val data=raw?.trim()?:return null
            if(type=="KGSL_BUSY_RATIO") {
                val values=data.split(Regex("""\s+"""))
                    .mapNotNull{it.toLongOrNull()}
                if(values.size<2 || values[1]<=0L ||
                    values[0]<0L || values[0]>values[1])return null
                return (values[0].toDouble()/values[1]*100.0)
                    .roundToInt().takeIf{it in 0..100}
            }
            if(type!="KGSL_PERCENT" && type!="MALI_PERCENT")return null
            return percent.matchEntire(data)?.groupValues?.get(1)
                ?.toIntOrNull()?.takeIf{it in 0..100}
        }
        fun clockMHz(raw:String?):Double? {
            val hz=raw?.trim()?.toLongOrNull()?.takeIf{it>0L} ?:return null
            val mhz=when {
                hz>10_000_000L->hz/1_000_000.0
                hz>10_000L->hz/1_000.0
                else->hz.toDouble()
            }
            return mhz.takeIf{it in 30.0..3000.0}
        }
        /** Generic devfreq candidates are frequency ONLY, not GPU load. */
        fun gpuDevfreqCandidates(names:List<String>):List<String> =
            names.asSequence()
                .filter{
                    (it.contains("gpu",true)||it.contains("mali",true) ||
                     it.contains("3d",true)) &&
                    it.length<=100 && !it.contains('/') && !it.contains("..")
                }.distinct().sorted().take(8)
                .map{"/sys/class/devfreq/"+it+"/cur_freq"}.toList()
    }

    private fun loadSources():List<Source> = loads
    private fun clockSources():List<Source> = clockPaths +
        devfreqPaths().take(8).map{Source(it,"GENERIC_DEVFREQ_CLOCK")}

    fun sample(elapsedMs:Long):Reading {
        var loadValue=selectedLoad?.let{load(read(it.path),it.type)}
        if(loadValue==null && elapsedMs>=retryLoadAtMs) {
            selectedLoad=null
            for(s in loadSources()) {
                val n=load(read(s.path),s.type)
                if(n!=null) {selectedLoad=s;loadValue=n;break}
            }
            retryLoadAtMs=elapsedMs+30_000L
        }
        var clockValue=selectedClock?.let{clockMHz(read(it.path))}
        if(clockValue==null && elapsedMs>=retryClockAtMs) {
            selectedClock=null
            for(s in clockSources()) {
                val value=clockMHz(read(s.path))
                if(value!=null){selectedClock=s;clockValue=value;break}
            }
            retryClockAtMs=elapsedMs+30_000L
        }
        val src=if(loadValue==null && clockValue==null)"UNAVAILABLE" else
            "LOAD:"+(if(loadValue==null)"UNAVAILABLE" else selectedLoad?.type?:"UNKNOWN")+
            "; CLOCK:"+(if(clockValue==null)"UNAVAILABLE" else selectedClock?.type?:"UNKNOWN")+
            "; READABLE_NOT_VALIDATED"
        return Reading(loadValue,clockValue,src)
    }
}
