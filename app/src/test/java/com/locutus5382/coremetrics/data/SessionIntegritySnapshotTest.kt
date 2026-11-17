package com.locutus5382.coremetrics.data

import org.junit.Assert.*
import org.junit.Test

class SessionIntegritySnapshotTest {
    private fun make(
        count:Long=31L,status:String="COMPLETED",first:Long=1_000_000L,
        last:Long=1_150_000L,started:Long=1_000_000L,
        ended:Long?=1_155_000L
    )=SessionIntegritySnapshot.Entry(
        17L,status,count,SessionStore.ReportBounds(count,first,last),
        started,ended
    )

    @Test fun completedSessionWithMatchingRowsAndTimesHasNoWarnings() {
        val result=SessionIntegritySnapshot.assess(make())
        assertEquals(31L,result.count)
        assertEquals(150_000L,result.durationMs)
        assertTrue(result.warnings.isEmpty())
        val text=SessionIntegritySnapshot.render(listOf(result))
        assertTrue(text.contains("no inconsistency detected"))
        assertTrue(text.contains("no claim of full data validation"))
    }

    @Test fun concurrentRowCountChangeIsFlaggedWithoutTouchingSource() {
        val entry=make().copy(summaryCount=100L)
        val issues=SessionIntegritySnapshot.assess(entry).warnings
        assertTrue(issues.any{it.contains("Row count changed")})
    }

    @Test fun emptyFinishedSessionAndImpossibleEndTimesAreFlagged() {
        val entry=SessionIntegritySnapshot.Entry(
            18,"COMPLETED",0L,null,100_000L,95_000L)
        val problems=SessionIntegritySnapshot.assess(entry).warnings
        assertTrue(problems.any{it.contains("no telemetry")})
        assertTrue(problems.any{it.contains("predates recorded start")})
    }

    @Test fun recordingWithoutFirstSampleIsNotAutomaticallyMarkedBroken() {
        val entry=SessionIntegritySnapshot.Entry(
            19,"RECORDING",0L,null,100_000L,null)
        val result=SessionIntegritySnapshot.assess(entry)
        assertTrue(result.warnings.isEmpty())
        assertNull(result.durationMs)
    }

    @Test fun inconsistentSampleTimeIsDetected() {
        val entry=make(first=900_000L,last=1_250_000L)
        val issues=SessionIntegritySnapshot.assess(entry).warnings
        assertTrue(issues.any{it.contains("predates recorded start")})
        assertTrue(issues.any{it.contains("after recorded finish")})
    }

    @Test fun manualReportOmitsGameLabelsAndRawSensorValues() {
        val report=SessionIntegritySnapshot.render(
            listOf(SessionIntegritySnapshot.assess(make())))
        assertFalse(report.contains("Hades"))
        assertFalse(report.contains("GPU MHz"))
        assertTrue(report.contains("Session #17"))
        assertTrue(report.contains("read-only"))
    }
}
