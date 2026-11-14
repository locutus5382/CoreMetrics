package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Confidence in sampling continuity, distinct from signal availability or
 * suitability for comparing two different gameplay workloads.
 * This observes the span between first/last stored samples, not game duration.
 */
object RecordingIntegrityAnalyzer {
    enum class Verdict { RELIABLE, PARTIAL, INTERRUPTED, INSUFFICIENT }
    data class Result(
        val verdict: Verdict,
        val sampleCount: Int,
        val continuityPct: Double?,
        val gaps: Int,
        val missingMs: Long,
        val targetIntervalMs: Long?,
        val medianIntervalMs: Long?,
        val p95JitterMs: Double?,
        val samplerMedianMs: Double?,
        val samplerP95Ms: Double?,
        val message: String,
        // Offline derived statistics; no recorder or schema changes.
        val meanIntervalMs: Double? = null,
        val p99JitterMs: Double? = null,
        val stepsOver2xTarget: Int = 0
    )

    private fun median(values: List<Double>): Double? =
        values.sorted().takeIf { it.isNotEmpty() }?.let { v ->
            if (v.size % 2 == 1) v[v.size / 2]
            else (v[v.size / 2 - 1] + v[v.size / 2]) / 2.0
        }
    private fun percentile95(values: List<Double>): Double? =
        values.sorted().takeIf { it.isNotEmpty() }?.let { v ->
            v[(kotlin.math.ceil(v.size * 0.95).toInt() - 1).coerceIn(0, v.lastIndex)]
        }

    fun analyze(points: List<SessionStore.Point>, status: String? = null): Result {
        // CSV imports preserve their original terminal outcome under an
        // IMPORTED_ prefix. An imported interrupted run is still interrupted.
        val outcome = status?.removePrefix("IMPORTED_")
        val interrupted = outcome?.startsWith("INTERRUPTED") == true ||
            outcome == "FAILED"
        if (points.size < 2) return Result(
            Verdict.INSUFFICIENT,points.size,null,0,0,null,null,null,null,null,
            "At least two readings are needed to assess continuity."
        )
        val sorted = points.sortedBy { it.timeMs }
        val steps = sorted.zipWithNext { a,b ->
            val monotonic = if (a.sampleElapsedMs != null &&
                b.sampleElapsedMs != null &&
                b.sampleElapsedMs > a.sampleElapsedMs)
                b.sampleElapsedMs - a.sampleElapsedMs else null
            monotonic ?: (b.timeMs-a.timeMs)
        }
        val positive = steps.filter { it > 0L && it < 3_600_000L }
        val typical = median(positive.map { it.toDouble() })?.roundToLong()
        if (typical == null) return Result(
            Verdict.INSUFFICIENT,points.size,null,0,0,null,null,null,null,null,
            "No valid increasing timestamps."
        )
        val target = if (abs(typical-2000L) <= abs(typical-5000L))
            2000L else 5000L
        val gapThreshold = target * 3 / 2
        val gaps = steps.count { it <= 0L || it > gapThreshold }
        val missingMs = steps.sumOf { step ->
            if (step <= 0L) target
            else if (step > gapThreshold) (step-target).coerceAtLeast(0L)
            else 0L
        }
        val spanMs = steps.filter { it > 0L }.sum()
        val continuity = if (spanMs > 0L)
            ((spanMs - missingMs).coerceAtLeast(0L) * 100.0 / spanMs)
                .coerceIn(0.0,100.0) else null
        val jitter = positive.filter { it <= gapThreshold }
            .map { abs(it.toDouble()-target) }
        val costs = points.mapNotNull { it.sampleDurationMs }
            .filter { it.isFinite() && it >= 0.0 }
        val verdict = when {
            interrupted -> Verdict.INTERRUPTED
            continuity == null || positive.size < 2 -> Verdict.INSUFFICIENT
            gaps > 0 || continuity < 95.0 -> Verdict.PARTIAL
            else -> Verdict.RELIABLE
        }
        val message = when(verdict) {
            Verdict.RELIABLE -> "No missing sampling intervals detected."
            Verdict.PARTIAL -> "Recording has gaps; interpret affected periods cautiously."
            Verdict.INTERRUPTED -> "Recording ended unexpectedly; saved samples remain usable."
            Verdict.INSUFFICIENT -> "Not enough valid timing data."
        }
        val p99 = if(jitter.size>=20) {
            val ordered=jitter.sorted()
            ordered[(kotlin.math.ceil(ordered.size*0.99).toInt()-1)
                .coerceIn(0,ordered.lastIndex)]
        } else null
        return Result(verdict,points.size,continuity,gaps,missingMs,target,
            typical,percentile95(jitter),median(costs),percentile95(costs),message,
            meanIntervalMs=positive.takeIf{it.size>=2}?.map{it.toDouble()}?.average(),
            p99JitterMs=p99,
            stepsOver2xTarget=steps.count{it>2*target})

    }
}
