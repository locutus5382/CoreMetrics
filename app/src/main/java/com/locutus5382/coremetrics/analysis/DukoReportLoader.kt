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
        require(bounds.count<=DukoSessionInsight.MAX_SAMPLES) {
            "DUKO Report cannot analyze more than "+
                DukoSessionInsight.MAX_SAMPLES+
                " samples safely in this version; export full CSV instead"
        }
        val report=DukoSessionInsight.Accumulator(
            session,bounds,db.reportCadenceMs(id))
        val visited=db.forEachReportPoint(id){report.accept(it)}
        check(visited==bounds.count){"Report dataset changed during export"}
        return report.finish()
    }
}
