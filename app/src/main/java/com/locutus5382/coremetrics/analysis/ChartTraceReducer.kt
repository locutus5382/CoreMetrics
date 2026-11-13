package com.locutus5382.coremetrics.analysis

import kotlin.math.max

/**
 * Display-only renderer input. Four real observations maximum per occupied
 * pixel column and uninterrupted sensor segment (first/min/max/last). This
 * preserves short-lived peaks and valleys but NEVER changes the statistics,
 * exported samples or source timestamps. Explicit break markers prohibit
 * drawing a line over missing observations or long recording gaps.
 */
object ChartTraceReducer {
    data class Sample(val timeMs:Long,val value:Double?,val segment:Int=0)

    fun reduce(
        source:List<Sample>,startMs:Long,endMs:Long,pixelWidth:Int,
        gapLimitMs:Long
    ):List<Sample> {
        require(endMs>startMs && gapLimitMs>0L)
        if(source.isEmpty())return emptyList()
        val width=pixelWidth.coerceIn(1,8192)
        val result=ArrayList<Sample>(minOf(source.size,width*4+32))
        data class Item(val ordinal:Int,val sample:Sample)
        var bucket=-1
        var first:Item?=null
        var last:Item?=null
        var min:Item?=null
        var max:Item?=null
        fun flush() {
            val group=listOfNotNull(first,min,max,last)
                .distinctBy{it.ordinal}.sortedBy{it.ordinal}
            group.forEach{result.add(it.sample)}
            first=null;last=null;min=null;max=null
        }
        var previous:Sample?=null
        for((ordinal,sample) in source.withIndex()) {
            if(sample.timeMs<startMs || sample.timeMs>endMs)continue
            if(sample.value==null || !sample.value.isFinite()) {
                flush()
                if(result.lastOrNull()?.value!=null)
                    result.add(Sample(sample.timeMs,null,sample.segment))
                previous=null
                bucket=-1
                continue
            }
            val prev=previous
            if(prev!=null && (sample.timeMs<=prev.timeMs ||
                    sample.timeMs-prev.timeMs>gapLimitMs ||
                    sample.segment!=prev.segment)) {
                flush()
                result.add(Sample(sample.timeMs,null,sample.segment))
                bucket=-1
            }
            val position=((sample.timeMs-startMs).toDouble()/
                (endMs-startMs).toDouble()*width).toInt().coerceIn(0,width-1)
            if(bucket!=position) {
                flush()
                bucket=position
            }
            val item=Item(ordinal,sample)
            if(first==null)first=item
            last=item
            if(min==null || sample.value<min!!.sample.value!!)min=item
            if(max==null || sample.value>max!!.sample.value!!)max=item
            previous=sample
        }
        flush()
        return result
    }
}

/** Cursor selection over already sorted valid diagnostic readings.
 * Independent of chart reduction: labels always use original observations.
 */
object DiagnosticTimeCursor {
    fun nearest(
        readings:List<SessionDiagnosticsAnalyzer.Reading>,
        targetMs:Long,toleranceMs:Long
    ):SessionDiagnosticsAnalyzer.Reading? {
        if(readings.isEmpty() || toleranceMs<0L)return null
        if(targetMs<readings.first().timeMs || targetMs>readings.last().timeMs)
            return null
        var low=0;var high=readings.size
        while(low<high) {
            val middle=low+(high-low)/2
            if(readings[middle].timeMs<targetMs)low=middle+1
            else high=middle
        }
        val later=readings.getOrNull(low)
        val earlier=readings.getOrNull(low-1)
        // A missing sensor/sample gap is not an inferred continuous reading.
        if(earlier!=null && later!=null &&
            earlier.segment!=later.segment &&
            targetMs>earlier.timeMs && targetMs<later.timeMs)return null
        val pick=when {
            earlier==null -> later
            later==null -> earlier
            targetMs-earlier.timeMs<later.timeMs-targetMs -> earlier
            else -> later
        }
        return pick?.takeIf {
            kotlin.math.abs(targetMs-it.timeMs)<=toleranceMs
        }
    }
}
