package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore

/** Explicit user-initiated operation; the recording/monitor process is never touched. */
internal object DukoComparisonLoader {
    fun build(
        store:SessionStore,ids:List<Long>,baselineId:Long,
        testedVariable:CompareStudioAnalysis.TestedVariable,
        checkpoint:()->Unit={},
        onProgress:(Int,Int,Int)->Unit={_,_,_->}
    ):DukoComparisonInsight.Result {
        require(ids.size in 2..5 && ids.all{it>0L} && ids.distinct().size==ids.size) {
            "Select 2–5 different completed sessions"
        }
        require(baselineId in ids) { "Baseline must belong to the selected sessions" }
        // Fail before any PDF/PNG destination stream is opened.
        val reports=ids.mapIndexed { index,id ->
            checkpoint()
            onProgress(index,ids.size,0)
            val stored=store.sessionById(id)?:error("Session #"+id+" was deleted")
            require(stored.status!="RECORDING") { "Stop recording session #"+id+" first" }
            val report=DukoReportLoader.build(
                store,id,checkpoint,
                onProgress={read,total->
                    onProgress(index,ids.size,
                        (read*100L/total.coerceAtLeast(1L)).toInt().coerceIn(0,100))
                })
            report
        }
        // A user may delete a previously loaded run while the next run is
        // scanned. Refuse a mixed-age comparison instead of exporting silently.
        reports.forEach { report ->
            checkpoint()
            check(store.storedSampleCount(report.session.id)==
                report.sourceSampleCount) {
                "Selected session #"+report.session.id+
                    " changed or was deleted during comparison export"
            }
        }
        return DukoComparisonInsight.analyze(reports,baselineId,testedVariable)
    }
}
