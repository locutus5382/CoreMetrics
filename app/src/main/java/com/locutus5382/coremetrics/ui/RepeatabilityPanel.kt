package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.RepeatabilityAnalyzer
import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit two-to-five A/A repetitions. Existing A/B controls remain the
 * default. Loads data ONLY on request, after recording, never during REC.
 */
@Composable
internal fun RepeatabilityPanel(
    database:SessionStore,sessions:List<SessionStore.Session>,
    skipWarmUp:Boolean
) {
    val groups=remember(sessions) {
        sessions.asSequence()
            .filter{it.status!="RECORDING" && it.count>1 &&
                !it.profileName.isNullOrBlank()}
            .groupBy{it.profileName!!.trim()}
            .filterValues{it.size>=2}
            .toSortedMap()
    }
    Column(Modifier.fillMaxWidth().pureGlass(13).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("REPEATABILITY · A/A EXPERIMENTS",color=iceBlue,
            fontWeight=FontWeight.Bold)
        Text("Two to five runs with the same manually declared test profile. "+
            "Each run counts once, regardless of how many telemetry samples it contains.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        if(groups.isEmpty()) {
            Text("Record at least two completed sessions using the same named "+
                "Quick Test Profile to enable repeatability summaries.",
                color=solarOrange,style=MaterialTheme.typography.bodySmall)
            return@Column
        }
        var chosenKey by remember { mutableStateOf("") }
        val actualKey=chosenKey.takeIf{groups.containsKey(it)}
            ?:groups.keys.first()
        val available=groups.getValue(actualKey).take(15)
        var selected by remember(actualKey,sessions) {
            mutableStateOf(available.take(5).map{it.id}.toSet())
        }
        var result by remember { mutableStateOf<RepeatabilityAnalyzer.Result?>(null) }
        var lastRequest by remember { mutableStateOf<Pair<String,List<Long>>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val scope=rememberCoroutineScope()
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            groups.keys.forEach{key->
                FilterChip(selected=actualKey==key,onClick={
                    if(!busy){chosenKey=key;result=null;error="";lastRequest=null}
                },label={Text(key.take(40))})
            }
        }
        Text("SELECT REPETITIONS (2–5)",color=coreSecondary,
            style=MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            available.forEach {session->
                FilterChip(selected=session.id in selected,
                    onClick={
                        if(!busy) {
                            selected=if(session.id in selected)
                                selected-session.id
                                else if(selected.size<5)selected+session.id
                                else selected
                            result=null;lastRequest=null;error=""
                        }
                    },label={Text("#"+session.id+" "+(session.graphicsDriver?:"driver ?").take(26))})
            }
        }
        Text("Selected: "+selected.size+" · matching profile names do NOT "+
            "prove identical game scenes or actual hardware settings.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Button(enabled=!busy && selected.size in 2..5,
            onClick={
                val ids=selected.sorted()
                val currentKey=actualKey
                val chosen=available.filter{it.id in selected}
                busy=true;error="";result=null
                scope.launch {
                    val attempt=withContext(Dispatchers.IO) {
                        runCatching {
                            RepeatabilityAnalyzer.analyze(chosen.map{session->
                                RepeatabilityAnalyzer.Run(session,
                                    database.points(session.id))
                            },if(skipWarmUp)30 else 0)
                        }
                    }
                    busy=false
                    if(actualKey==currentKey && selected.sorted()==ids) {
                        attempt.fold(onSuccess={
                            result=it;lastRequest=currentKey to ids
                        },onFailure={error=it.message?:"Unable to analyze these repetitions"})
                    }
                }
            }){Text(if(busy)"Analyzing..." else "Analyze selected A/A runs")}
        if(error.isNotBlank()) Text(error,color=solarOrange,
            style=MaterialTheme.typography.bodySmall)
        val report=result?.takeIf {
            lastRequest==actualKey to selected.sorted()
        }
        if(report!=null) {
            Text("Run IDs: "+report.runIds.joinToString(", ") { "#"+it },
                color=coreText,style=MaterialTheme.typography.bodySmall)
            report.measurements.forEach{m->
                val f: (Double?)->String={v->
                    v?.let{String.format(Locale.US,"%.2f",it)}?:"—"
                }
                Text(m.label+" · median of "+m.runCount+" run medians: "+
                    f(m.medianOfRuns)+(if(m.runCount>=2)
                        " "+m.unit+" · range "+f(m.minimumRun)+"–"+
                            f(m.maximumRun)+" "+m.unit else
                        " · insufficient repeated-run evidence"),
                    color=coreText,style=MaterialTheme.typography.bodySmall)
            }
            Text("CAUTIONS · "+report.cautions.size,color=iceBlue,
                style=MaterialTheme.typography.labelMedium)
            report.cautions.forEach{caution->
                Text("• "+caution,color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
            }
            Text("Descriptive run-to-run variability, not a confidence interval, "+
                "statistical significance, FPS comparison or driver ranking.",
                color=solarGold,style=MaterialTheme.typography.bodySmall)
        }
    }
}
