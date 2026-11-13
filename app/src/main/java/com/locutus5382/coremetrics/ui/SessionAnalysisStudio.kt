package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.SegmentAnalysis
import com.locutus5382.coremetrics.analysis.SessionDiagnosticsAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Post-recording Studio; range controls do no IO and never touch the recorder. */
@Composable
internal fun SessionAnalysisStudio(
    session:SessionStore.Session,
    source:List<SessionStore.Point>,
    initialSkipWarmup:Boolean,
    onExportText:(SegmentAnalysis.Range)->Unit,
    onExportCsv:(SegmentAnalysis.Range)->Unit,
    technicalContent:@Composable (SegmentAnalysis.Bundle)->Unit
) {
    val full=remember(source) { SegmentAnalysis.fullRange(source) }
    if(full==null) {
        Text("At least two time-ordered samples are required to analyze a session.",
            color=coreSecondary)
        return
    }
    val origin=full.startMs
    val total=(full.endMs-full.startMs)/1000f
    val minimum=min(20f,total)
    val warmupEnabled=total>=50f
    val initialStart=if(initialSkipWarmup && warmupEnabled)30f else 0f
    var draftStart by remember(source,initialSkipWarmup) {
        mutableFloatStateOf(initialStart)
    }
    var draftEnd by remember(source,initialSkipWarmup) {
        mutableFloatStateOf(total)
    }
    var preset by remember(source,initialSkipWarmup) {
        mutableStateOf(if(initialStart>0f)"Warm-up" else "Full")
    }
    fun asRange(start:Float,end:Float):SegmentAnalysis.Range {
        val from=(origin+(start*1000f).roundToLong()).coerceIn(origin,full.endMs-1)
        val to=(origin+(end*1000f).roundToLong()).coerceIn(from+1,full.endMs)
        return SegmentAnalysis.Range(from,to)
    }
    var applied by remember(source,initialSkipWarmup) {
        mutableStateOf(asRange(initialStart,total))
    }
    fun setRange(start:Float,end:Float,asPreset:String="Custom") {
        val a=start.coerceIn(0f,(total-minimum).coerceAtLeast(0f))
        val b=end.coerceIn(a+minimum,total)
        draftStart=a;draftEnd=b
        applied=asRange(a,b)
        preset=asPreset
    }
    val bundle by produceState<SegmentAnalysis.Bundle?>(
        initialValue=null,session,source,applied
    ) {
        value=withContext(Dispatchers.Default) {
            runCatching{SegmentAnalysis.create(session,source,applied)}.getOrNull()
        }
    }
    val loadedFully=session.count<=source.size
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().pureGlass(15).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("SESSION ANALYSIS STUDIO · v0.8.7",
                color=iceBlue,fontWeight=FontWeight.Bold)
            Text("Select the measured game interval. Original samples and timestamps stay intact.",
                color=coreSecondary,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                FilterChip(selected=preset=="Full",
                    onClick={setRange(0f,total,"Full")},
                    label={Text("Full recording")})
                FilterChip(selected=preset=="Warm-up",
                    onClick={setRange(30f,total,"Warm-up")},
                    enabled=warmupEnabled,
                    label={Text("Skip first 30s")})
                FilterChip(selected=preset=="Custom",
                    onClick={preset="Custom"},
                    label={Text("Custom range")})
            }
            RangeOverview(source,full,draftStart,draftEnd)
            RangeSlider(value=draftStart..draftEnd,
                onValueChange={range ->
                    val nextA=range.start.coerceIn(0f,(total-minimum).coerceAtLeast(0f))
                    val nextB=range.endInclusive.coerceIn(nextA+minimum,total)
                    draftStart=nextA;draftEnd=nextB
                    preset="Custom"
                },
                onValueChangeFinished={
                    applied=asRange(draftStart,draftEnd)
                },
                valueRange=0f..total,
                modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Start "+stamp(draftStart),
                    color=coreText,style=MaterialTheme.typography.labelMedium)
                Text("Selected "+stamp(draftEnd-draftStart),
                    color=solarGold,style=MaterialTheme.typography.labelMedium)
                Text("End "+stamp(draftEnd),
                    color=coreText,style=MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={
                    setRange(draftStart-5f,draftEnd)
                },enabled=draftStart>0f) {Text("Start −5s")}
                OutlinedButton(onClick={
                    setRange(draftStart+5f,draftEnd)
                },enabled=draftEnd-draftStart>minimum+0.1f) {Text("Start +5s")}
                OutlinedButton(onClick={
                    setRange(draftStart,draftEnd-5f)
                },enabled=draftEnd-draftStart>minimum+0.1f) {Text("End −5s")}
                OutlinedButton(onClick={
                    setRange(draftStart,draftEnd+5f)
                },enabled=draftEnd<total) {Text("End +5s")}
            }
            val current=bundle
            if(current==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                val a=(applied.startMs-origin)/1000.0
                val b=(applied.endMs-origin)/1000.0
                Text("Analyzing "+format(a)+"–"+format(b)+" s · "+
                    current.points.size+" recorded samples",
                    color=coreText,style=MaterialTheme.typography.bodySmall)
                if(!loadedFully) Text(
                    "WARNING: Only the first 12,000 samples are loaded; "+
                        "segment exports are disabled to avoid incomplete results.",
                    color=solarOrange,style=MaterialTheme.typography.bodySmall)
                if(!current.validForReport)Text(
                    "Insufficient segment: need at least 6 samples spanning 20s. "+
                        "Widen the range to enable exports.",
                    color=solarOrange,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onExportText(applied)},
                        enabled=current.validForReport && loadedFully) {
                        Text("Export segment report")
                    }
                    OutlinedButton(onClick={onExportCsv(applied)},
                        enabled=current.validForReport && loadedFully) {
                        Text("Export segment CSV")
                    }
                }
            }
            Text("The timeline shows original data and the selected boundaries. "+
                "Statistics update after releasing the range handles or using preset/5s controls.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        val current=bundle
        if(current!=null) {
            Text("ALL NUMBERS BELOW APPLY TO THE SELECTED WINDOW",
                color=iceBlue,fontWeight=FontWeight.Bold)
            SessionQuickOverview(current.benchmark)
            StabilityPanel(current)
            SessionDiagnosticsPanel(current.points,current.benchmark,
                referenceStartMs=origin,precomputed=current.diagnostics)
            technicalContent(current)
        }
    }
}

private fun stamp(seconds:Float):String {
    val total=seconds.coerceAtLeast(0f).toInt()
    return "%02d:%02d".format(Locale.US,total/60,total%60)
}
private fun format(value:Double):String=String.format(Locale.US,"%.1f",value)

@Composable
private fun RangeOverview(points:List<SessionStore.Point>,
    full:SegmentAnalysis.Range,from:Float,to:Float) {
    val duration=(full.endMs-full.startMs).coerceAtLeast(1L).toDouble()
    val values=remember(points) { points.mapNotNull{it.cpu?.takeIf{v->v.isFinite()}} }
    val bottom=(values.minOrNull()?:25.0)-2.0
    val extent=max(4.0,(values.maxOrNull()?:50.0)+2.0-bottom)
    Canvas(Modifier.fillMaxWidth().height(92.dp)
        .background(Color(0x71081830),RoundedCornerShape(10.dp))) {
        val w=size.width;val h=size.height
        fun x(t:Long)=((t-full.startMs)/duration*w).toFloat().coerceIn(0f,w)
        fun y(v:Double)=((1.0-(v-bottom)/extent)*h).toFloat().coerceIn(0f,h)
        val path=Path()
        var prev:Long?=null
        for(p in points) {
            val temp=p.cpu?.takeIf{it.isFinite()}
            if(temp==null){prev=null;continue}
            val px=x(p.timeMs);val py=y(temp)
            if(prev==null || p.timeMs-prev!!>15_000L)path.moveTo(px,py)
            else path.lineTo(px,py)
            prev=p.timeMs
        }
        drawPath(path,coreCyan,style=Stroke(1.8.dp.toPx()))
        val left=(from.toDouble()*1000.0/duration*w).toFloat().coerceIn(0f,w)
        val right=(to.toDouble()*1000.0/duration*w).toFloat().coerceIn(left,w)
        drawRect(Color(0xA0050B1C),Offset(0f,0f),Size(left,h))
        drawRect(Color(0xA0050B1C),Offset(right,0f),Size(w-right,h))
        drawRect(coreMint.copy(alpha=.07f),Offset(left,0f),Size(right-left,h))
        drawLine(solarGold,Offset(left,0f),Offset(left,h),2.dp.toPx())
        drawLine(solarGold,Offset(right,0f),Offset(right,h),2.dp.toPx())
    }
    Text("CPU temperature overview · dimmed areas excluded; gold = range boundaries",
        color=coreSecondary,style=MaterialTheme.typography.labelSmall)
}

@Composable
private fun StabilityPanel(bundle:SegmentAnalysis.Bundle) {
    Column(Modifier.fillMaxWidth().pureGlass(14).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("STABILITY AND VOLATILITY · SELECTED RANGE",
            color=iceBlue,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("SIGNAL",Modifier.weight(1.5f),
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            Text("STABLE TIME",Modifier.weight(1f),
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            Text("MEAN |Δ|/STEP",Modifier.weight(1.2f),
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            Text("VALID s",Modifier.weight(.7f),
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        bundle.stability.forEach { stat ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1.5f)) {
                    Text(stat.title,color=coreText,
                        style=MaterialTheme.typography.bodySmall,maxLines=1)
                    Text(stat.threshold,color=coreSecondary,
                        style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
                Text(stat.percentOfValidTime?.let {format(it)+"%"} ?: "—",
                    Modifier.weight(1f),color=coreText,
                    style=MaterialTheme.typography.bodySmall)
                Text(stat.meanAbsoluteStep?.let {format(it)+" "+stat.unit} ?: "—",
                    Modifier.weight(1.2f),color=coreText,
                    style=MaterialTheme.typography.bodySmall)
                Text(format(stat.measuredSeconds),Modifier.weight(.7f),
                    color=coreText,style=MaterialTheme.typography.bodySmall)
            }
        }
        val cpu=bundle.stability.firstOrNull()
        Text("Valid-time stability counts contiguous intervals where both "+
            "recorded endpoints lie near the selected-window median. "+
            "Short/insufficient windows display —, not 0%.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Volatility = mean absolute consecutive change per sampling step, "+
            "not per second. Typical CPU paired step: "+
            (cpu?.typicalStepSeconds?.let{format(it)+"s"} ?:"—")+
            ". Compare only recordings using similar sampling intervals.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Stable readings do not imply thermal equilibrium or absence of "+
            "performance issues.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
