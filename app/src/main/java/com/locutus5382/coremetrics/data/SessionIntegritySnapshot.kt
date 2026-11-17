package com.locutus5382.coremetrics.data

/**
 * v0.9.9.4: one-shot, strictly read-only recorder integrity snapshot.
 *
 * Called only when the user presses Diagnostics. No session content,
 * filenames, game labels, process names or CPU/GPU telemetry are retained.
 * O(recent sessions + indexed COUNT/MIN/MAX), not a raw sample scan.
 */
internal object SessionIntegritySnapshot {
    data class Entry(
        val id: Long,
        val status: String,
        val summaryCount: Long,
        val bounds: SessionStore.ReportBounds?,
        val startedAt: Long,
        val endedAt: Long?
    )
    data class Assessment(
        val id: Long,
        val status: String,
        val count: Long,
        val firstMs: Long?,
        val lastMs: Long?,
        val durationMs: Long?,
        val warnings: List<String>
    )

    fun assess(entry:Entry):Assessment {
        val issues=ArrayList<String>(3)
        val bounds=entry.bounds
        val count=bounds?.count?:0L
        if(entry.summaryCount!=count)
            issues+="Row count changed between diagnostic queries"
        if(count<=0L && entry.status!="RECORDING")
            issues+="Saved session contains no telemetry samples"
        if(bounds!=null) {
            if(bounds.lastMs<bounds.firstMs)
                issues+="Timestamp range is reversed"
            if(bounds.firstMs<entry.startedAt-60_000L)
                issues+="First sample predates recorded start by over 60s"
            if(entry.endedAt!=null &&
                bounds.lastMs>entry.endedAt+60_000L)
                issues+="Last sample occurs over 60s after recorded finish"
        }
        if(entry.endedAt!=null &&
            entry.endedAt<entry.startedAt)
            issues+="Recorded finish predates recorded start"
        return Assessment(entry.id,entry.status,count,
            bounds?.firstMs,bounds?.lastMs,
            bounds?.let{it.lastMs-it.firstMs},issues)
    }

    fun render(items:List<Assessment>):String=buildString {
        appendLine("Recent recording-integrity snapshot (read-only)")
        appendLine("Source: SQLite session rows and indexed COUNT/MIN/MAX only")
        appendLine("No raw samples inspected; no claim of full data validation")
        if(items.isEmpty())appendLine("No saved sessions")
        for(item in items) {
            append("Session #").append(item.id)
                .append(" · ").append(item.status)
                .append(" · ").append(item.count).append(" saved samples")
            item.durationMs?.let{append(" · observed ")
                .append(it/1_000L).append(" s")}
            appendLine()
            if(item.warnings.isEmpty())
                appendLine("  Basic structure: no inconsistency detected")
            else item.warnings.forEach {
                append("  CHECK: ").appendLine(it)
            }
        }
        appendLine("Counts are point-in-time snapshots; active recordings can grow between queries.")
        appendLine("A clean snapshot does not establish HUD persistence, FPS or sensor accuracy.")
    }

    fun report(store:SessionStore,limit:Int=5):String {
        // Even if a user clicks repeatedly, inspect at most 5 recent records.
        val results=store.list(limit.coerceIn(1,5)).map { session ->
            val entry=Entry(session.id,session.status,
                session.count.toLong(),store.reportBounds(session.id),
                session.startedAt,session.endedAt)
            assess(entry)
        }
        return render(results)
    }
}
