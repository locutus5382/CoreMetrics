package com.locutus5382.coremetrics.analysis

/**
 * FIFO for explicit user requests only. This class has no clock, thread,
 * Android context, polling, auto-report trigger, or background worker.
 * A request leaves the queue only after its document-picker/export turn ends.
 */
internal data class DukoReportTask(
    val kind:Kind,
    val format:String,
    val ids:List<Long>,
    val baselineId:Long=0L,
    val variable:CompareStudioAnalysis.TestedVariable=
        CompareStudioAnalysis.TestedVariable.UNDECLARED
) {
    enum class Kind { SESSION, COMPARISON }
    init {
        require(format=="pdf" || format=="png")
        require(ids.all{it>0L} && ids.distinct().size==ids.size)
        when(kind) {
            Kind.SESSION -> require(ids.size==1 && baselineId==0L)
            Kind.COMPARISON ->
                require(ids.size in 2..5 && baselineId in ids)
        }
    }
    val title:String get()=when(kind) {
        Kind.SESSION -> "DUKO Session #"+ids.single()+" "+format.uppercase()
        Kind.COMPARISON -> "DUKO "+ids.size+"-session comparison "+
            format.uppercase()
    }
    val filename:String get()=when(kind) {
        Kind.SESSION -> "DUKO_Session_"+ids.single()+"."+format
        Kind.COMPARISON -> "DUKO_Comparison_"+ids.joinToString("_")+"."+format
    }
    companion object {
        fun session(id:Long,format:String)=DukoReportTask(
            Kind.SESSION,format,listOf(id))
        fun comparison(
            ids:List<Long>,baselineId:Long,
            variable:CompareStudioAnalysis.TestedVariable,format:String
        )=DukoReportTask(Kind.COMPARISON,format,ids.toList(),
            baselineId,variable)
    }
}

/**
 * One active request (including its Storage Access Framework picker).
 * All later requests retain their exact click order and immutable parameters.
 * Cancelling one does not cancel the remaining requests.
 */
internal class DukoReportScheduler(private val maxWaiting:Int=12) {
    private val waiting=ArrayDeque<DukoReportTask>()
    var active:DukoReportTask?=null
        private set
    val waitingCount:Int get()=waiting.size
    val waitingSnapshot:List<DukoReportTask> get()=waiting.toList()

    fun submit(request:DukoReportTask):Boolean {
        if(waiting.size>=maxWaiting)return false
        waiting.addLast(request)
        nextIfIdle()
        return true
    }
    private fun nextIfIdle() {
        if(active==null && waiting.isNotEmpty())
            active=waiting.removeFirst()
    }
    fun complete(request:DukoReportTask) {
        check(active==request){"Completing a report that is not current"}
        active=null
        nextIfIdle()
    }
    fun cancelWaiting(index:Int):DukoReportTask? {
        if(index !in 0 until waiting.size)return null
        val entries=waiting.toMutableList()
        val removed=entries.removeAt(index)
        waiting.clear()
        waiting.addAll(entries)
        return removed
    }
}
