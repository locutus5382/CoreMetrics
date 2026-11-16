package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.SessionIntelligence
import com.locutus5382.coremetrics.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Sparse event timeline; never draw a forest of labels over sensor traces. */
@Composable
internal fun SessionEventsPanel(points:List<SessionStore.Point>) {
    val analysis by produceState<SessionIntelligence.Result?>(
        initialValue=null,points) {
        value=withContext(Dispatchers.Default) {
            SessionIntelligence.analyze(points)
        }
    }
    var expanded by remember(points){mutableStateOf(false)}
    val result=analysis ?:return
    val events=result.events
    Column(Modifier.fillMaxWidth().pureGlass(13).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text("SIGNIFICANT EVENTS · "+events.size,
            color=iceBlue,fontWeight=FontWeight.Bold)
        Text("Measured events and detected patterns are distinct. "+
            "A marker never establishes what caused the change.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        val duration=(points.lastOrNull()?.timeMs?:0L)-
            (points.firstOrNull()?.timeMs?:0L)
        if(duration>0 && events.isNotEmpty()) {
            Canvas(Modifier.fillMaxWidth().height(21.dp)) {
                val mid=size.height/2f
                drawLine(Color(0xFF6A8294),Offset(0f,mid),
                    Offset(size.width,mid),strokeWidth=2.dp.toPx())
                // Group dense events by pixel: show first 30 chronological
                // markers; the list below retains the complete event count.
                events.take(30).forEach{e->
                    val x=(e.offsetMs.toFloat()/duration.toFloat())
                        .coerceIn(0f,1f)*size.width
                    drawCircle(if(e.evidence==SessionIntelligence.Evidence.OBSERVED)
                        Color(0xFFFFC700) else Color(0xFF38BDF8),
                        radius=3.3.dp.toPx(),center=Offset(x,mid))
                }
            }
        }
        if(events.isEmpty()) {
            Text("No significant supported events in this recording. "+
                "Absence of an event is not proof that conditions were normal.",
                color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        } else {
            val shown=if(expanded)events.take(40) else events.take(3)
            shown.forEach{e->
                val m=e.offsetMs/60_000L
                val sec=(e.offsetMs/1_000L)%60L
                val time=String.format(Locale.US,"%02d:%02d",m,sec)
                Text(time+" · "+e.title+" · "+
                    (if(e.evidence==SessionIntelligence.Evidence.OBSERVED)
                        "Observed" else "Pattern"),
                    color=if(e.evidence==SessionIntelligence.Evidence.OBSERVED)
                        solarGold else coreCyan,
                    style=MaterialTheme.typography.bodySmall,
                    fontWeight=FontWeight.Bold)
                Text(e.detail,color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
            }
            if(events.size>3)TextButton(onClick={expanded=!expanded}) {
                Text(if(expanded)"Show fewer events"
                    else "Show all "+events.size+" events")
            }
        }
        val periods=result.periods
        if(periods.isNotEmpty()) {
            Text("SUGGESTED ACTIVITY PERIODS · "+periods.size,
                color=iceBlue,style=MaterialTheme.typography.labelMedium)
            Text("Relative GPU activity, not a detected race/menu boundary. "+
                "Use the existing interval selector to confirm comparable scenes.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                periods.take(8).forEach{p->
                    val label=if(p.activity==SessionIntelligence.Activity.HIGHER)
                        "Higher" else "Lower"
                    Text(label+" "+(p.startMs/1000L)+"–"+(p.endMs/1000L)+"s",
                        color=coreText,style=MaterialTheme.typography.labelSmall,
                        modifier=Modifier.pureGlass(7).padding(6.dp))
                }
            }
        }
    }
}
