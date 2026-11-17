package com.locutus5382.coremetrics.analysis

/**
 * Small, printable SAF picker request. No user text or sensor samples are
 * encoded; this immutable ID snapshot survives Activity recreation.
 */
internal data class DukoComparisonRequest(
    val format:String,
    val ids:List<Long>,
    val baselineId:Long,
    val testedVariable:CompareStudioAnalysis.TestedVariable
) {
    init {
        require(format=="png" || format=="pdf")
        require(ids.size in 2..5 && ids.distinct().size==ids.size && ids.all{it>0L})
        require(baselineId in ids)
    }
    fun encode():String =
        listOf(format,ids.joinToString(","),baselineId.toString(),testedVariable.name)
            .joinToString("|")
    companion object {
        fun decode(value:String?):DukoComparisonRequest?=runCatching {
            val parts=value?.split('|')?:return@runCatching null
            if(parts.size!=4)return@runCatching null
            val ids=parts[1].split(',').map { it.toLong() }
            DukoComparisonRequest(parts[0],ids,parts[2].toLong(),
                CompareStudioAnalysis.TestedVariable.valueOf(parts[3]))
        }.getOrNull()
    }
}
