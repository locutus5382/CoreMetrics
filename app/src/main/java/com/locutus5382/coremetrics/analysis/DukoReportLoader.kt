package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore

/**
 * One explicit offline export, never a background monitor or record-time task.
 * Cursor iteration examines every saved sample, not the 12k-point UI preview.
 */
internal object DukoReportLoader {
    fun build(
        db:SessionStore,id:Long,
        checkpoint:()->Unit={},
        onProgress:(Long,Long)->Unit={_,_->},
        quantileScratch:DukoSessionInsight.QuantileScratch?=null
    ):DukoSessionInsight.Report {
        require(id>0L){"Select a saved session"}
        val session=db.sessionById(id)?:error("Recording does not exist")
        require(session.status!="RECORDING") {
            "Stop the active recording before generating its DUKO Report"
        }
        val bounds=db.reportBounds(id)?:error("No stored samples")
        require(bounds.count in 6L..DukoSessionInsight.MAX_SAMPLES.toLong()) {
            "DUKO Report requires 6–"+
                DukoSessionInsight.MAX_SAMPLES+
                " stored samples; export full CSV if above the limit"
        }
        require(bounds.lastMs-bounds.firstMs>=20_000L) {
            "DUKO Report requires at least 20 seconds of recorded samples"
        }
        val report=DukoSessionInsight.Accumulator(
            session,bounds,db.reportCadenceMs(id),quantileScratch)
        onProgress(0L,bounds.count)
        var visitedSinceCheck=0
        var accepted=0L
        checkpoint()
        val visited=db.forEachReportSample(id,bounds){ sample ->
            report.acceptSample(sample)
            accepted++
            if(++visitedSinceCheck>=512) {
                checkpoint()
                onProgress(accepted,bounds.count)
                visitedSinceCheck=0
            }
        }
        checkpoint()
        onProgress(accepted,bounds.count)
        check(visited==bounds.count){"Report dataset changed during export"}
        return report.finish()
    }
}
