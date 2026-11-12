package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.telemetry.fpslab.*
import org.junit.Assert.*
import org.junit.Test

class ExperimentalFpsLabTest {
    private val proof=FrameHostProof(0,true,true,true,true,1,true)
    private fun response(base:Long) =
        "16666667\n"+(0..70).joinToString("\n") { i ->
            val time=base+i*16_666_667L
            "1 $time 2"
        }
    private class Host(private val proofValue:FrameHostProof,private val data:String?)
        :VerifiedFrameReadHost {
        var readCalls=0
        override fun prove()=proofValue
        override fun listLayers(timeoutMs:Long):String {
            readCalls++
            return "SurfaceView[org.eden.emulator]#0"
        }
        override fun layerLatency(layer:String,timeoutMs:Long):String? {
            readCalls++
            return data
        }
    }
    @Test fun disabledIsNoOp() {
        val host=Host(proof,response(1_000_000_000L))
        assertNull(ExperimentalPrivilegedFpsProvider(host,false)
            .probe("org.eden.emulator",100_000L,2_000_000_000L).fps)
        assertEquals(0,host.readCalls)
    }
    @Test fun rejectsClaimedUidWithoutBinderNonce() {
        val host=Host(proof.copy(nonceMatched=false),response(1_000_000_000L))
        val meter=ExperimentalPrivilegedFpsProvider(host,true)
        meter.probe("org.eden.emulator",100_000L,2_500_000_000L)
        assertEquals(0,host.readCalls)
        assertNull(meter.probe("org.eden.emulator",116_000L,2_500_000_000L).fps)
        assertTrue(meter.probe("org.eden.emulator",132_000L,2_500_000_000L)
            .status.startsWith("FPS_LAB_CIRCUIT_OPEN"))
    }
    @Test fun deviceVerifiedLayerRateAndRateLimit() {
        val host=Host(proof,response(1_000_000_000L))
        val meter=ExperimentalPrivilegedFpsProvider(host,true)
        val r=meter.probe("org.eden.emulator",100_000L,3_000_000_000L)
        assertEquals(60.0,r.fps!!,0.02)
        assertEquals(2,host.readCalls)
        meter.probe("org.eden.emulator",101_000L,3_000_000_000L)
        assertEquals(2,host.readCalls)
    }
    @Test fun sentinelsAndStaleWindowAreRejected() {
        val bad="16666667\n"+(0..20).joinToString("\n") {
            "1 ${Long.MAX_VALUE} 3"
        }
        assertNull(VerifiedFrameLatency.parse(bad,1,4_000_000_000L).fps)
        assertNull(VerifiedFrameLatency.parse(response(1_000_000_000L),
            1,80_000_000_000L).fps)
    }
}
