package com.locutus5382.coremetrics.telemetry

import java.util.Locale

/**
 * CPU policy numbers and their related CPUs are discovered, never mapped
 * to prime/performance/efficiency just from the index.
 * All values are read-only governor policy snapshots, not game thread clocks.
 */
object CpuPolicyTelemetry {
    private val namePattern=Regex("""policy[0-9]{1,3}""")
    private val capPattern=Regex("""policy([0-9]{1,3})\s+max\s+([0-9]+(?:\.[0-9]+)?)\s+GHz""")

    fun names(values:List<String>):List<String> =
        values.asSequence().filter{it.matches(namePattern)}.distinct()
            .sortedBy{it.removePrefix("policy").toInt()}.take(16).toList()

    fun relatedCpus(raw:String?):List<Int> {
        if(raw.isNullOrBlank())return emptyList()
        val result=mutableSetOf<Int>()
        for(token in raw.trim().split(Regex("""\s+"""))) {
            val value=token.toIntOrNull() ?:return emptyList()
            if(value !in 0..255)return emptyList()
            result+=value
        }
        return result.sorted()
    }

    /** Stable key for the new explicit CPU_POLICIES marker. Older
     * recordings remain interpreted by the legacy P3/P7 analyzer.
     */
    fun signature(raw:String?):String? {
        if(raw?.startsWith("CPU_POLICIES:")!=true)return null
        if(raw.contains("INCOMPLETE"))return "POLICIES:?"
        val caps=capPattern.findAll(raw).mapNotNull{m->
            val id=m.groupValues[1].toIntOrNull()
            val ghz=m.groupValues[2].toDoubleOrNull()
            if(id==null || id !in 0..999 || ghz==null ||
                !ghz.isFinite() || ghz !in 0.1..6.0)null
            else id to ghz
        }.distinctBy{it.first}.sortedBy{it.first}.toList()
        if(caps.isEmpty())return "POLICIES:?"
        return "POLICIES:"+caps.joinToString("|"){
            it.first.toString()+"="+String.format(Locale.US,"%.2f",it.second)
        }
    }

    fun label(key:String):String {
        if(key=="POLICIES:?")return "CPU policy limits unavailable/incomplete"
        val values=key.removePrefix("POLICIES:").split('|')
        return "Observed CPU policy caps ("+values.joinToString(" · "){
            "p"+it.replace('=',':')+" GHz"
        }+")"
    }
}
