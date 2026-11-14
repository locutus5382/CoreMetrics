package com.locutus5382.coremetrics.monitor

/** Pure meminfo parsing; no Android dependency or kernel access in unit tests. */
internal object SystemCapabilityParser {
    fun swapUsedKiB(meminfo:String):Long? {
        var total:Long?=null
        var free:Long?=null
        for(line in meminfo.lineSequence()){
            val trimmed=line.trim()
            val key=trimmed.substringBefore(':')
            if(key!="SwapTotal" && key!="SwapFree")continue
            val amount=trimmed.substringAfter(':',"").trim()
                .substringBefore(' ').toLongOrNull() ?: continue
            if(amount<0L)continue
            if(key=="SwapTotal")total=amount else free=amount
        }
        val t=total?:return null
        val f=free?:return null
        return if(f<=t)t-f else null
    }
}
