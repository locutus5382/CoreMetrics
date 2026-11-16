package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.RecordingIntegrityAnalyzer
import com.locutus5382.coremetrics.analysis.SessionObservationAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.monitor.ThermalIndicatorPolicy
import com.locutus5382.coremetrics.telemetry.ProcTelemetryReader
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class Phase2TelemetryTest {
    @Test fun thermalIndicatorUsesDeviceWideStatusAndHeadroom() {
        val policy = ThermalIndicatorPolicy
        assertEquals(ThermalIndicatorPolicy.Level.UNKNOWN, policy.level(null,null))
        assertEquals(ThermalIndicatorPolicy.Level.NORMAL, policy.level(0,null))
        assertEquals(ThermalIndicatorPolicy.Level.WARNING, policy.level(1,null))
        assertEquals(ThermalIndicatorPolicy.Level.WARNING, policy.level(0,0.9))
        assertEquals(ThermalIndicatorPolicy.Level.SEVERE, policy.level(3,null))
        assertEquals(ThermalIndicatorPolicy.Level.SEVERE, policy.level(0,1.0))
        assertEquals(ThermalIndicatorPolicy.Level.UNKNOWN, policy.level(-1,Double.NaN))
    }

    @Test fun normalOrUnknownOsStatusNeverOccupiesHudSpace() {
        assertFalse(ThermalIndicatorPolicy.showHudAlert(
            ThermalIndicatorPolicy.Level.NORMAL))
        assertFalse(ThermalIndicatorPolicy.showHudAlert(
            ThermalIndicatorPolicy.Level.UNKNOWN))
        assertTrue(ThermalIndicatorPolicy.showHudAlert(
            ThermalIndicatorPolicy.Level.WARNING))
        assertTrue(ThermalIndicatorPolicy.showHudAlert(
            ThermalIndicatorPolicy.Level.SEVERE))
    }

    @Test fun meminfoParsesRamAndSwapInOnePass() {
        val text = """SwapFree:  1024 kB
MemAvailable: 2097152 kB
SwapTotal: 4096 kB
MemTotal: 4194304 kB
"""
        val snap = ProcTelemetryReader.memorySnapshot(StringReader(text))
        assertEquals(2.0,snap.usedRamGiB!!,0.00001)
        assertEquals(3072.0/1048576.0,snap.swapUsedGiB!!,0.00001)
        assertNull(ProcTelemetryReader.memorySnapshot(StringReader(
            "MemTotal: 10 kB\nSwapTotal: 0 kB\nSwapFree: 0 kB\n"
        )).swapUsedGiB)
    }

    @Test fun psiAcceptsValidZeroRejectsInvalidOrMissing() {
        assertEquals(0.0,ProcTelemetryReader.memoryPsiSomeAvg10(
            StringReader("some avg10=0.00 avg60=0.12 avg300=0.04 total=30\n")
        )!!,0.00001)
        assertNull(ProcTelemetryReader.memoryPsiSomeAvg10(
            StringReader("some avg10=NaN avg60=0.1 total=0\n")
        ))
        assertNull(ProcTelemetryReader.memoryPsiSomeAvg10(
            StringReader("full avg10=1.0 avg60=2.0 total=0\n")
        ))
    }

    private fun point(time:Long, elapsed:Long = time,
        status:Int? = null, cost:Double? = 2.0) =
        SessionStore.Point(time,null,null,null,null,null,null,null,null,
            thermalStatus=status,sampleDurationMs=cost,sampleElapsedMs=elapsed)

    @Test fun completeSamplesAreReliableDespiteMissingSensors() {
        val samples=(0L..10L).map { point(it*5000L) }
        val result=RecordingIntegrityAnalyzer.analyze(samples,"COMPLETED")
        assertEquals(RecordingIntegrityAnalyzer.Verdict.RELIABLE,result.verdict)
        assertEquals(100.0,result.continuityPct!!,0.0001)
        assertEquals(0,result.gaps)
    }

    @Test fun gapsAndInterruptionsAreNotConfusedWithSensorAvailability() {
        val input=listOf(point(0),point(5000),point(10000),point(25000))
        val partial=RecordingIntegrityAnalyzer.analyze(input,"COMPLETED")
        assertEquals(RecordingIntegrityAnalyzer.Verdict.PARTIAL,partial.verdict)
        assertEquals(1,partial.gaps)
        val interrupted=RecordingIntegrityAnalyzer.analyze(input,"INTERRUPTED")
        assertEquals(RecordingIntegrityAnalyzer.Verdict.INTERRUPTED,interrupted.verdict)
        assertEquals(RecordingIntegrityAnalyzer.Verdict.INTERRUPTED,
            RecordingIntegrityAnalyzer.analyze(input,"IMPORTED_INTERRUPTED_RECOVERED").verdict)
        assertEquals(RecordingIntegrityAnalyzer.Verdict.INTERRUPTED,
            RecordingIntegrityAnalyzer.analyze(input,"IMPORTED_FAILED").verdict)
        assertTrue(partial.missingMs>0)
    }

    @Test fun derivedTimingStatisticsRequireNoNewStoredSensorData() {
        val regular=(0L..39L).map { point(it*5_000L,cost=0.65) }
        val result=RecordingIntegrityAnalyzer.analyze(regular,"COMPLETED")
        assertEquals(5_000.0,result.meanIntervalMs!!,0.01)
        assertEquals(0.0,result.p99JitterMs!!,0.01)
        assertEquals(0,result.stepsOver2xTarget)
        val delayed=regular.take(20)+regular.drop(20).map {
            it.copy(timeMs=it.timeMs+15_000L,sampleElapsedMs=it.sampleElapsedMs?.plus(15_000L))
        }
        val affected=RecordingIntegrityAnalyzer.analyze(delayed,"COMPLETED")
        assertEquals(1,affected.stepsOver2xTarget)
        assertEquals(RecordingIntegrityAnalyzer.Verdict.PARTIAL,affected.verdict)
    }

    @Test fun shortWindowsDoNotPretendP99JitterIsReliable() {
        val samples=(0L..10L).map{point(it*5_000L)}
        assertNull(RecordingIntegrityAnalyzer.analyze(samples,"COMPLETED").p99JitterMs)
    }

    @Test fun observationsDoNotMisdiagnoseOsStatus() {
        val input=(0L..4L).map { point(it*5000L,status=0) }
        val text=SessionObservationAnalyzer.observations(input).joinToString(" ")
        assertTrue(text.contains("does not exclude throttling"))
    }
}
