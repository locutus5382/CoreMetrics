package com.locutus5382.coremetrics.analysis

/**
 * Pending Storage Access Framework (SAF) selection token. Compose can save a
 * String across Activity recreation; source SQLite contents are looked up
 * afresh only after the user picks a destination. Invalid tokens are rejected.
 */
internal object DocumentWindowRequest {
    data class Segment(val sessionId:Long,val startMs:Long,val endMs:Long)
    data class Ab(val firstId:Long,val secondId:Long,
        val startMs:Long,val endMs:Long)

    fun segment(r:Segment):String =
        "segment|"+r.sessionId+"|"+r.startMs+"|"+r.endMs
    fun ab(r:Ab):String =
        "ab|"+r.firstId+"|"+r.secondId+"|"+r.startMs+"|"+r.endMs

    fun parseSegment(value:String?):Segment? {
        val parts=value?.split('|')?.takeIf{
            it.size==4 && it.first()=="segment"}?:return null
        val id=parts[1].toLongOrNull()?:return null
        val from=parts[2].toLongOrNull()?:return null
        val to=parts[3].toLongOrNull()?:return null
        if(id<=0L || from<0L || to<=from)return null
        return Segment(id,from,to)
    }

    fun parseAb(value:String?):Ab? {
        val parts=value?.split('|')?.takeIf{
            it.size==5 && it.first()=="ab"}?:return null
        val a=parts[1].toLongOrNull()?:return null
        val b=parts[2].toLongOrNull()?:return null
        val from=parts[3].toLongOrNull()?:return null
        val to=parts[4].toLongOrNull()?:return null
        if(a<=0L || b<=0L || a==b || from<0L || to<=from)return null
        return Ab(a,b,from,to)
    }
}
