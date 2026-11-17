package com.locutus5382.coremetrics.analysis

import org.junit.Assert.*
import org.junit.Test

class DukoReportSchedulerTest {
    private fun compare(ids:List<Long>,format:String="pdf") =
        DukoReportTask.comparison(ids,ids.first(),
            CompareStudioAnalysis.TestedVariable.DRIVER,format)

    @Test fun requestsAreStrictlyExplicitAndFirstInFirstOut() {
        val queue=DukoReportScheduler()
        assertNull(queue.active)
        assertEquals(0,queue.waitingCount)
        val first=DukoReportTask.session(10,"png")
        val second=compare(listOf(11,12))
        val third=DukoReportTask.session(15,"pdf")
        assertTrue(queue.submit(first))
        assertTrue(queue.submit(second))
        assertTrue(queue.submit(third))
        assertEquals(first,queue.active)
        assertEquals(2,queue.waitingCount)
        queue.complete(first)
        assertEquals(second,queue.active)
        assertEquals(1,queue.waitingCount)
        queue.complete(second)
        assertEquals(third,queue.active)
        queue.complete(third)
        assertNull(queue.active)
        assertTrue(queue.waitingSnapshot.isEmpty())
    }

    @Test fun cancellationOfPendingRequestDoesNotReorderRemainingTasks() {
        val queue=DukoReportScheduler()
        val items=(1L..5L).map {
            DukoReportTask.session(it,if(it%2L==0L)"png" else "pdf")
        }
        items.forEach {assertTrue(queue.submit(it))}
        assertEquals(items[2],queue.cancelWaiting(1))
        assertEquals(listOf(items[1],items[3],items[4]),
            queue.waitingSnapshot)
        assertNull(queue.cancelWaiting(99))
        queue.complete(items[0])
        assertEquals(items[1],queue.active)
        queue.complete(items[1])
        assertEquals(items[3],queue.active)
        queue.complete(items[3])
        assertEquals(items[4],queue.active)
    }

    @Test fun schedulerNeverStartsSecondReportWhileFirstIsActive() {
        val queue=DukoReportScheduler(maxWaiting=2)
        val one=DukoReportTask.session(1,"pdf")
        val two=DukoReportTask.session(2,"png")
        val three=DukoReportTask.session(3,"pdf")
        val rejected=DukoReportTask.session(4,"pdf")
        assertTrue(queue.submit(one))
        assertTrue(queue.submit(two))
        assertTrue(queue.submit(three))
        assertFalse(queue.submit(rejected))
        assertEquals(one,queue.active)
        assertEquals(listOf(two,three),queue.waitingSnapshot)
        assertTrue(runCatching {queue.complete(two)}.isFailure)
        assertEquals(one,queue.active)
        queue.complete(one)
        assertEquals(two,queue.active)
    }

    @Test fun jobSnapshotKeepsBaselineOrderAndSourceIds() {
        val original=mutableListOf(2L,8L,9L)
        val task=compare(original)
        original.clear()
        assertEquals(listOf(2L,8L,9L),task.ids)
        assertEquals(2L,task.baselineId)
        assertTrue(task.filename.startsWith("DUKO_Comparison_2_8_9"))
        assertTrue(runCatching {
            DukoReportTask.comparison(listOf(2,2),2,
                CompareStudioAnalysis.TestedVariable.DRIVER,"pdf")
        }.isFailure)
        assertTrue(runCatching {
            DukoReportTask.session(1,"xls")
        }.isFailure)
    }
}
