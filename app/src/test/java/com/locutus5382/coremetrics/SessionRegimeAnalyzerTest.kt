package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.SessionRegimeAnalyzer as A
import org.junit.Assert.*
import org.junit.Test

class SessionRegimeAnalyzerTest {
    private fun row(t:Int, p3:String, p7:String, power:Double? = 5.0,
                    load:Double=45.0, status:String="DISCHARGING; BATTERY_NET_ONLY") =
        A.Sample(t * 5000L,
            "policy0 max 1.80 GHz · policy3 max $p3 GHz (below hw) · policy7 max $p7 GHz (below hw)",
            cpuTempC=65.0-t*.5, gpuTempC=50.0,
            cpuLoadPct=40.0,busiestCorePct=80.0,gpuLoadPct=load,
            ramUsedGib=4.0,batteryNetW=power,batteryPowerStatus=status)

    @Test fun contiguousPhasesDebounceBlipAndIgnorePolicy0() {
        val stock=(0..4).map { row(it,"2.80","3.19",6.0) }
        val medium=(5..9).map { row(it,"2.05","2.09",5.0) }
        val blip=listOf(row(10,"2.80","3.19",5.0))
        val mediumAgain=(11..13).map { row(it,"2.05","2.09",5.0) }
        val large=(14..18).map { row(it,"1.79","1.84",4.0) }
        val r=A.analyze(stock + medium + blip + mediumAgain + large)
        assertEquals(3,r.size)
        assertTrue(r[0].baseline)
        assertTrue(r[1].label.startsWith("Medium UC"))
        assertEquals(9,r[1].sampleCount)
        assertTrue(r[2].label.startsWith("Large UC"))
        assertEquals(-2.0,r[2].deltaMeanDischargeW!!,0.001)
        assertEquals(-33.3333,r[2].deltaDischargePct!!,0.01)
        assertEquals(30.8/4.0,r[2].projectedHours!!,0.001)
    }

    @Test fun activeFilterDoesNotBridgeRegimesAndNoChargingProjection() {
        val stock=(0..3).map { row(it,"2.80","3.19",power=6.0,load=2.0) }
        val tuned=(4..8).map { row(it,"2.05","2.09",power=4.0,load=50.0,
            status="CHARGING_OR_EXTERNAL_POWER; BATTERY_NET_ONLY") }
        val r=A.analyze(stock+tuned,activeGameplayOnly=true)
        assertEquals(1,r.size)
        assertTrue(r[0].label.startsWith("Medium UC"))
        assertNull(r[0].projectedHours)
        assertNull(r[0].meanDischargeW)
    }

    @Test fun unavailableReadingsRemainNullAndRepeatedStockIsANewPhase() {
        val a=(0..3).map { row(it,"2.80","3.19",null) }
        val b=(4..7).map { row(it,"2.05","2.09",null) }
        val c=(8..11).map { row(it,"2.80","3.19",null) }
        val phases=A.analyze(a+b+c)
        assertEquals(3,phases.size)
        assertTrue(phases[0].baseline)
        assertTrue(phases[2].label.startsWith("Stock ("))
        assertNull(phases[1].deltaMeanDischargeW)
        assertNull(phases[2].meanDischargeW)
    }

    @Test fun matchingCpuCapsSeparatedByLongRecordingGapStaySeparate() {
        val early=(0..4).map{row(it,"2.80","3.19",6.0)}
        val late=(40..44).map{row(it,"2.80","3.19",4.0)}
        val phases=A.analyze(early+late)
        assertEquals(2,phases.size)
        assertEquals(0L,phases[0].startMs)
        assertEquals(20_000L,phases[0].endMs)
        assertEquals(200_000L,phases[1].startMs)
        assertEquals(220_000L,phases[1].endMs)
        assertEquals(6.0,phases[0].meanDischargeW!!,.001)
        assertEquals(4.0,phases[1].meanDischargeW!!,.001)
    }
    @Test fun filteredOutLoadIntervalsAreNotIntegratedIntoEnergy() {
        val rows=(0..5).map {
            row(it,"2.80","3.19",if(it<3)3.0 else 9.0,
                load=if(it==3)1.0 else 60.0)
        }
        val phases=A.analyze(rows,activeGameplayOnly=true)
        assertEquals(1,phases.size)
        assertEquals(5.0,phases[0].meanDischargeW!!,.001) // no bridged gap
    }
    @Test fun missingCapsAreNeverLabelledStock() {
        assertEquals("Limits unavailable", A.label(A.key(null)))
        assertTrue(A.label(A.key("policy3 max 2.05 GHz")).startsWith("Observed cap"))
    }
}
