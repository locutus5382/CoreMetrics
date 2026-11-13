package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.V08Analytics
import com.locutus5382.coremetrics.data.SessionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** No database or polling in composable charts; render persisted points only. */
@Composable
internal fun ObservedProfilePanel(
    selected:String,points:List<SessionStore.Point>,modifier:Modifier=Modifier,
    referenceStartMs:Long?=null
) {
    val profile=remember(selected,points) {
        V08Analytics.profile(selected,points)
    }
    // Warm-up exclusion must not silently reset elapsed time to zero.
    val timeOrigin=referenceStartMs ?: points.firstOrNull()?.timeMs ?: 0L
    Column(modifier.pureGlass(12).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("SELECTED vs OBSERVED",color=iceBlue,fontWeight=FontWeight.Bold)
        Text("Selected: $selected",color=coreText)
        if(profile.phases.isEmpty())Text("Observed: unavailable",color=coreSecondary)
        else profile.phases.forEachIndexed { index, phase ->
            Text("Observed phase ${index+1}: ${phase.description}",
                color=if(profile.conflict)solarOrange else coreCyan,
                style=MaterialTheme.typography.bodySmall)
            val offset=(phase.startMs-timeOrigin)/1000.0
            val end=(phase.endMs-timeOrigin)/1000.0
            Text(String.format(Locale.US,"%.0f–%.0f s · %d samples",
                offset,end,phase.sampleCount),
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        if(profile.conflict)Text(
            "Selected label conflicts with measured CPU limits; neither proves tuner identity.",
            color=solarOrange,style=MaterialTheme.typography.labelSmall)
        Text(profile.note,color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun CoreWinnerPanel(
    points:List<SessionStore.Point>,modifier:Modifier=Modifier
) {
    val distribution=remember(points) {V08Analytics.busiestCores(points)}
    Column(modifier.pureGlass(12).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("BUSIEST CORE DISTRIBUTION",color=iceBlue,fontWeight=FontWeight.Bold)
        if(distribution.observations==0) {
            Text("No valid busiest-core samples were recorded.",
                color=coreSecondary)
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                for(group in 0..1) {
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        distribution.winners.filter{item->
                            item.core in (group*4)..(group*4+3)
                        }.forEach { item ->
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("CPU ${item.core}",Modifier.width(42.dp),
                                    color=coreText,
                                    style=MaterialTheme.typography.labelSmall)
                                Box(Modifier.weight(1f).height(12.dp)
                                    .background(Color(0x552A4059),
                                        RoundedCornerShape(3.dp))) {
                                    Box(Modifier.fillMaxWidth(
                                        (item.sharePct/100.0).toFloat().coerceIn(0f,1f))
                                        .fillMaxHeight()
                                        .background(if(item.core>=7)solarOrange
                                            else if(item.core>=3)coreCyan else coreMint,
                                            RoundedCornerShape(3.dp)))
                                }
                                Text(String.format(Locale.US,"%.0f%%",item.sharePct),
                                    Modifier.width(36.dp),color=coreText,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        Text("${distribution.observations} valid 2–5 s winner samples; "+
            "${distribution.ignored} unavailable or tied. This is NOT CPU core residency.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun LinkedSessionsTimeline(
    first:SessionStore.Session,firstPoints:List<SessionStore.Point>,
    second:SessionStore.Session,secondPoints:List<SessionStore.Point>
) {
    val summary=remember(first,firstPoints,second,secondPoints) {
        V08Analytics.linkedTimeline(first,firstPoints,second,secondPoints)
    }
    if(summary==null) {
        Text("Both recordings require measured points to display a linked timeline.",
            color=coreSecondary)
        return
    }
    val ordered=remember(first,firstPoints,second,secondPoints) {
        listOf(first to firstPoints,second to secondPoints)
            .sortedBy{it.second.minOf{p->p.timeMs}}
    }
    val dateFmt=remember {SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault())}
    Column(Modifier.fillMaxWidth().pureGlass(14).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("LINKED RECORDINGS · ACTUAL WALL-CLOCK TIME",
            color=iceBlue,fontWeight=FontWeight.Bold)
        ordered.forEachIndexed { index,entry ->
            val points=entry.second
            val start=points.minOf{it.timeMs}
            val end=points.maxOf{it.timeMs}
            Text("Session ${entry.first.id}  ·  ${entry.first.status} · "+
                dateFmt.format(Date(start))+" – "+dateFmt.format(Date(end)),
                color=if(index==0)coreCyan else solarGold,
                style=MaterialTheme.typography.bodySmall)
        }
        Canvas(Modifier.fillMaxWidth().height(112.dp)
            .background(Color(0x9D09172A),RoundedCornerShape(10.dp))) {
            val w=size.width
            val h=size.height
            val dur=(summary.endMs-summary.startMs).coerceAtLeast(1L)
            fun x(t:Long)=((t-summary.startMs).toDouble()/dur*w).toFloat()
                .coerceIn(0f,w)
            for(i in 1..3){
                val xx=w*i/4f
                drawLine(Color(0x335C7993),Offset(xx,0f),
                    Offset(xx,h),1f)
            }
            // The empty region is *not* a bridge. Pale red merely indicates
            // that neither of the two sources recorded telemetry there.
            if(summary.gapMs>0L) {
                val left=x(summary.firstEnd)
                val right=x(summary.secondStart)
                if(right>left)drawRect(Color(0x44E55555),
                    Offset(left,0f),Size(right-left,h))
            }
            val c=listOf(coreCyan,solarGold)
            ordered.forEachIndexed{ i,entry ->
                val points=entry.second
                val y=19.dp.toPx()+i*44.dp.toPx()
                val start=x(points.minOf{it.timeMs})
                val end=x(points.maxOf{it.timeMs})
                drawRoundRect(c[i],Offset(start,y),
                    Size((end-start).coerceAtLeast(2.dp.toPx()),24.dp.toPx()),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            }
        }
        val explanation=when {
            summary.gapMs>0L -> String.format(Locale.US,
                "Unrecorded gap: %.1f s (no synthetic samples)",summary.gapMs/1000.0)
            summary.overlap -> "These recordings overlap in time; no continuity implied."
            else -> "Recordings touch in timestamp; continuity is not verified."
        }
        Text(explanation,color=if(summary.gapMs>0)solarOrange else coreSecondary,
            style=MaterialTheme.typography.bodySmall)
        Text("Manual link only · original session IDs, samples, timestamps, "+
            "notes and completion statuses are unchanged.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun EnergyRatePanel(
    energy:com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer.Energy,
    modifier:Modifier=Modifier
) {
    Column(modifier.pureGlass(12).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("BATTERY-TERMINAL ENERGY",color=iceBlue,fontWeight=FontWeight.Bold)
        Text("Integrated  "+(energy.wh?.let{
            String.format(Locale.US,"%.4f Wh",it)
        }?:"unavailable"),color=coreText)
        Text("Rate  "+(energy.milliWhPerMinute?.let{
            String.format(Locale.US,"%.2f mWh/min",it)
        }?:"unavailable"),color=solarGold)
        Text("Time-weighted  "+(energy.weightedMeanW?.let{
            String.format(Locale.US,"%.2f W",it)
        }?:"unavailable"),color=coreText)
        Text(String.format(Locale.US,"Discharge coverage %.0f%% · %.1f s valid",
            energy.coveragePct,energy.measuredSeconds),
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Not wall power. Invalid, missing and charging periods excluded.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
