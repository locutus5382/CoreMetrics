package com.locutus5382.coremetrics.monitor

/** Pure parsing for read-only, on-demand kernel capability diagnostics. */
internal object SystemCapabilityParser {
    data class Swap(val totalKiB:Long,val freeKiB:Long) {
        val usedKiB:Long get()=totalKiB-freeKiB
    }

    fun swapFieldsKiB(meminfo:String):Swap? {
        var total:Long?=null
        var free:Long?=null
        for(line in meminfo.lineSequence()){
            val key=line.substringBefore(':').trim()
            if(key!="SwapTotal" && key!="SwapFree")continue
            val value=line.substringAfter(':',"").trim()
                .split(Regex("\\s+"),limit=2).firstOrNull()
                ?.toLongOrNull() ?: continue
            if(value<0L)continue
            if(key=="SwapTotal")total=value else free=value
        }
        val t=total?:return null
        val f=free?:return null
        return if(f<=t)Swap(t,f) else null
    }

    fun swapUsedKiB(meminfo:String):Long?=swapFieldsKiB(meminfo)?.usedKiB

    /** Android PowerManager constants as stable readable report labels.
     * They indicate the OS classification, not observed SoC throttling. */
    fun thermalStatusName(status:Int?):String=when(status) {
        0->"NONE"
        1->"LIGHT"
        2->"MODERATE"
        3->"SEVERE"
        4->"CRITICAL"
        5->"EMERGENCY"
        6->"SHUTDOWN"
        null->"UNAVAILABLE"
        else->"UNKNOWN"
    }

    /** CPUfreq time_in_state is cumulative kernel ticks, NOT core load,
     * clock frequency measured during one gameplay interval, or useful work.
     * This probe only establishes whether well-formed data are readable. */
    data class Residency(val states:Int,val minKHz:Long,val maxKHz:Long,
        val cumulativeTicks:Long)
    fun residency(raw:String):Residency? {
        var count=0;var minimum=Long.MAX_VALUE;var maximum=0L;var total=0L
        val seen=HashSet<Long>()
        for(line in raw.lineSequence()){
            val fields=line.trim().split(Regex("\\s+"))
            if(fields.size!=2)continue
            val khz=fields[0].toLongOrNull()?:continue
            val ticks=fields[1].toLongOrNull()?:continue
            if(khz !in 10_000L..10_000_000L || ticks<0L || !seen.add(khz))
                continue
            if(total>Long.MAX_VALUE-ticks)return null
            minimum=minOf(minimum,khz)
            maximum=maxOf(maximum,khz)
            total+=ticks
            count++
        }
        return if(count>0)Residency(count,minimum,maximum,total) else null
    }

    /** PSI text is reporting only; no extrapolation into a past LMK event. */
    fun psiLines(raw:String):List<String> =
        raw.lineSequence().map{it.trim()}
            .filter{it.startsWith("some ") || it.startsWith("full ")}
            .take(2).map{it.take(160)}.toList()
}
