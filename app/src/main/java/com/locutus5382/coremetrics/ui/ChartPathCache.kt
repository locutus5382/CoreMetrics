package com.locutus5382.coremetrics.ui

import androidx.compose.ui.graphics.Path
import com.locutus5382.coremetrics.analysis.ChartTraceReducer

/**
 * Build a read-only chart trace once per dataset/size, not for every scrub
 * cursor movement. These paths are display-only, never used for analysis.
 * Explicit null/segment/time-gap boundaries remain disconnected.
 */
internal object ChartPathCache {
    fun build(
        source:List<ChartTraceReducer.Sample>,
        start:Long,duration:Double,width:Float,height:Float,
        bottom:Double,span:Double,gapLimit:Long
    ):Path {
        val path=Path()
        var previous:ChartTraceReducer.Sample?=null
        for(sample in source) {
            val value=sample.value
            if(value==null || !value.isFinite()){
                previous=null
                continue
            }
            val x=((sample.timeMs-start).toDouble()/duration*width)
                .toFloat().coerceIn(0f,width)
            val y=((1.0-(value-bottom)/span)*height)
                .toFloat().coerceIn(0f,height)
            if(previous==null || sample.segment!=previous.segment ||
                sample.timeMs-previous.timeMs>gapLimit)
                path.moveTo(x,y)
            else path.lineTo(x,y)
            previous=sample
        }
        return path
    }
}
