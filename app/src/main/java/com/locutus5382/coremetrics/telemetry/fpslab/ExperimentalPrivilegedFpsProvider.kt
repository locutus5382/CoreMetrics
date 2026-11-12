package com.locutus5382.coremetrics.telemetry.fpslab

import com.locutus5382.coremetrics.telemetry.FrameEstimate

/**
 * Strictly isolated, off-by-default FPS experiment.
 *
 * Important: This is a transport-neutral module; it does NOT implement a
 * PServer launcher, execute shells or obtain privileges. A future *audited*
 * external read-only host can implement this interface only after actual
 * Portal handshake and SurfaceFlinger column verification.
 */
internal interface VerifiedFrameReadHost {
    fun prove(): FrameHostProof
    fun listLayers(timeoutMs:Long):String?
    fun layerLatency(layer:String,timeoutMs:Long):String?
}

internal data class FrameHostProof(
    val privilegedUid:Int,
    val authenticatedBinderCaller:Boolean,
    val nonceMatched:Boolean,
    val readOnlyAllowlist:Boolean,
    val portalFirmwareVerified:Boolean,
    val devicePresentationColumn:Int?,
    val allowsSurfaceFlingerDump:Boolean
) {
    val trusted: Boolean get() =
        privilegedUid in setOf(0,1000) &&
            authenticatedBinderCaller && nonceMatched && readOnlyAllowlist &&
            portalFirmwareVerified && allowsSurfaceFlingerDump &&
            devicePresentationColumn in 0..2
}

internal object VerifiedFrameLatency {
    /**
     * A column index is supplied by device evidence; SurfaceFlinger latency
     * output variants must NOT be silently guessed. Long.MAX_VALUE is sentinel.
     */
    fun parse(text:String,column:Int,nowNanos:Long):FrameEstimate {
        require(column in 0..2)
        if(text.contains("permission denied",true)||text.contains("Permission Denial",true))
            return FrameEstimate(null,null,0,"PERMISSION_DENIED")
        val rows=text.lineSequence().filter{it.isNotBlank()}.take(140).toList()
        if(rows.size<12)return FrameEstimate(null,null,0,"TOO_FEW_PRESENT_TIMESTAMPS")
        val present=rows.drop(1).mapNotNull { line ->
            val c=line.trim().split(Regex("\\s+"))
            if(c.size!=3)null else c[column].toLongOrNull()?.takeIf{
                it>0L && it!=Long.MAX_VALUE && it<nowNanos+1_000_000_000L
            }
        }.distinct().sorted()
        if(present.size<10)return FrameEstimate(null,null,present.size,
            "TOO_FEW_VALID_PRESENT_TIMESTAMPS")
        val recent=present.last()
        if(nowNanos-recent>10_000_000_000L)return FrameEstimate(null,null,
            present.size,"STALE_PRESENT_TIMESTAMPS")
        val intervals=present.zipWithNext().map { (a,b)->b-a }
            .filter { it in 1_000_000L..1_000_000_000L }
        if(intervals.size<9)return FrameEstimate(null,null,intervals.size,
            "TOO_FEW_VALID_FRAME_INTERVALS")
        val avgNs=intervals.average()
        val fps=1_000_000_000.0/avgNs
        if(!fps.isFinite() || fps !in 1.0..240.0)
            return FrameEstimate(null,null,intervals.size,"OUT_OF_RANGE")
        val med=intervals.sorted()[intervals.size/2]/1_000_000.0
        return FrameEstimate(fps,med,intervals.size,
            "VERIFIED_SF_LAYER_RATE_EXPERIMENTAL_NOT_EMULATOR_INTERNAL_FPS")
    }
}

/**
 * No app calls this in v0.6. Transport must authenticate and attest via actual
 * Binder caller identity, nonce and device proof, not merely report UID=0.
 * Two failures latch until explicitly creating a new provider; no retry storm.
 */
internal class ExperimentalPrivilegedFpsProvider(
    private val host:VerifiedFrameReadHost?,
    private val userOptedIn:Boolean,
    private val minIntervalMs:Long=15_000L
) {
    private var errors=0
    private var previousPackage:String?=null
    private var layer:String?=null
    private var nextAt=0L
    private var previous=FrameEstimate(null,null,0,"FPS_LAB_NOT_STARTED")
    private var proof:FrameHostProof?=null

    fun probe(gamePackage:String?,elapsedMs:Long,nowNanos:Long):FrameEstimate {
        if(!userOptedIn)return FrameEstimate(null,null,0,"FPS_LAB_DISABLED")
        if(host==null)return FrameEstimate(null,null,0,"NO_VERIFIED_PSERVER_HOST_ADAPTER")
        if(errors>=2)return FrameEstimate(null,null,0,"FPS_LAB_CIRCUIT_OPEN")
        if(gamePackage.isNullOrBlank() || !gamePackage.matches(
                Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+")))
            return FrameEstimate(null,null,0,"GAME_PACKAGE_UNAVAILABLE")
        val evidence=proof ?: try {
            host.prove().also { proof=it }
        }catch(_:Exception){return failed("HOST_HANDSHAKE_FAILED")}
        if(!evidence.trusted)return failed("UNVERIFIED_READONLY_HOST_OR_PRESENT_COLUMN")
        if(elapsedMs<nextAt)return previous
        nextAt=elapsedMs+minIntervalMs.coerceAtLeast(15_000L)
        if(previousPackage!=gamePackage){layer=null;previousPackage=gamePackage}
        return try {
            if(layer==null) {
                val available=host.listLayers(1500L)
                    ?.takeIf { it.length<=48_000 }
                    ?: return failed("LAYER_LIST_UNAVAILABLE")
                if(available.contains("permission denied",true))
                    return failed("SURFACEFLINGER_PERMISSION_DENIED")
                val matches=available.lineSequence().map { it.trim() }
                    .filter { it.contains(gamePackage,ignoreCase=true) &&
                        it.contains("SurfaceView",ignoreCase=true) }.take(4).toList()
                if(matches.size!=1)return failed(
                    if(matches.isEmpty())"NO_UNIQUE_GAME_LAYER"
                    else "AMBIGUOUS_GAME_LAYER")
                layer=matches.single()
            }
            val raw=host.layerLatency(layer!!,1500L)
                ?.takeIf { it.length<=64_000 }
                ?: return failed("LATENCY_UNAVAILABLE")
            val result=VerifiedFrameLatency.parse(raw,
                evidence.devicePresentationColumn!!,nowNanos)
            if(result.fps==null)failed(result.status)
            else {
                errors=0
                previous=result
                result
            }
        }catch(_:Exception){failed("READONLY_FPS_PROBE_FAILED")}
    }

    private fun failed(reason:String):FrameEstimate {
        errors++
        previous=FrameEstimate(null,null,0,
            if(errors>=2)"FPS_LAB_CIRCUIT_OPEN: "+reason else reason)
        return previous
    }
}
