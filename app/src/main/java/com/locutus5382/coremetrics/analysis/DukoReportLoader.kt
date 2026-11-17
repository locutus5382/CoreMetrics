package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore

/**
 * One explicit offline export, never a background monitor or record-time task.
 * Cursor iteration examines every saved sample, not the 12k-point UI preview.
 */
internal object DukoReportLoader {
    fun build(db:SessionStore,id:Long):DukoSessionInsight.Report {
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
            session,bounds,db.reportCadenceMs(id))
        val visited=db.forEachReportPoint(id){report.accept(it)}
        check(visited==bounds.count){"Report dataset changed during export"}
        return report.finish()
    }
}
