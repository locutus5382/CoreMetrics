package com.locutus5382.coremetrics.analysis

/** Saved across Activity recreation while the SAF document picker is open.
 * Only numerical IDs/bounds and approved theme keys, no filesystem paths.
 */
internal data class ScorecardExportRequest(
    val firstId:Long,val secondId:Long,
    val startMs:Long,val endMs:Long,
    val styleKey:String,val shareAfterSave:Boolean
) {
    fun encode():String=listOf("1",firstId,secondId,startMs,endMs,styleKey,
        if(shareAfterSave)"1" else "0").joinToString("|")

    companion object {
        private val styles=setOf("frosted","crystal_aero",
            "solar_pulse","cobalt_matrix")
        fun decode(raw:String?):ScorecardExportRequest? {
            val p=raw?.split('|')?.takeIf{it.size==7 && it[0]=="1"}?:return null
            val a=p[1].toLongOrNull()?:return null
            val b=p[2].toLongOrNull()?:return null
            val start=p[3].toLongOrNull()?:return null
            val end=p[4].toLongOrNull()?:return null
            if(a<=0L || b<0L || a==b || start<0L || end<=start ||
                p[5] !in styles || p[6] !in setOf("0","1"))return null
            return ScorecardExportRequest(a,b,start,end,p[5],p[6]=="1")
        }
    }
}
