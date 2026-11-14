package com.locutus5382.coremetrics.telemetry

import java.io.Reader

/**
 * Bounded, streaming read of Linux proc statistics. Parsing must not assume an
 * eight-core device or fixed meminfo ordering. This class has no Android APIs
 * so its failure/edge behavior can be checked by local JVM unit tests.
 */
internal object ProcTelemetryReader {
    private const val MAX_CPU_LINES=64
    private const val MAX_MEM_LINES=128

    /** Aggregate CPU followed by cpuN lines. Stops before intr/softirq. */
    fun cpuLines(source:Reader):List<String> = source.buffered().use { input ->
        val rows=ArrayList<String>(16)
        var lines=0
        while(lines++<MAX_CPU_LINES) {
            val line=input.readLine() ?: break
            val name=line.takeWhile { !it.isWhitespace() }
            if(name!="cpu" && !(name.startsWith("cpu") &&
                    name.length>3 && name.drop(3).all { it.isDigit() }))break
            if(line.length>2048)break // Unexpected kernel output
            rows.add(line)
        }
        rows
    }

    /** Used system memory, in GiB; null for unavailable/invalid readings. */
    fun usedGiB(source:Reader):Double? {
        var total:Long?=null
        var available:Long?=null
        source.buffered().use { input ->
            var lines=0
            while(lines++<MAX_MEM_LINES && (total==null || available==null)) {
                val line=input.readLine() ?: break
                if(line.length>2048)continue
                val name=line.substringBefore(':')
                if(name!="MemTotal" && name!="MemAvailable")continue
                val number=line.substringAfter(':',"").trim()
                    .takeWhile { !it.isWhitespace() }.toLongOrNull()
                    ?.takeIf { it>=0L }
                if(name=="MemTotal")total=number else available=number
            }
        }
        val t=total
        val a=available
        return if(t!=null && t>0L && a!=null && a<=t)
            (t-a)/1048576.0 else null
    }
}
