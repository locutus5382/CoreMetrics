package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.BenchmarkAnalyzer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Compact comparisons are descriptive, not a winner picker.
 * Data-integrity scores and test-comparability warnings are separate.
 */
@Composable
internal fun CompactCompareOverview(
    a:BenchmarkAnalyzer.Report?,b:BenchmarkAnalyzer.Report?
) {
    if(a==null || b==null){
        Text("Choose two saved recordings for a side-by-side comparison.",
            color=coreSecondary)
        return
    }
    fun number(v:Double?,unit:String,decimals:Int=1)=
        v?.takeIf{it.isFinite()}?.let {
            String.format(Locale.US,"%.${decimals}f%s",it,unit)
        }?:"—"
    @Composable fun sessionCard(r:BenchmarkAnalyzer.Report,id:String,mod:Modifier){
        Column(mod.pureGlass(16).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text("SESSION "+id+" · #"+r.session.id,color=iceBlue,
                style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
            Text(r.session.configuration+" · "+r.session.status,
                color=coreText)
            Text("CPU "+number(r.meanCpuC,"°C")+
                "  · GPU "+number(r.meanGpuC,"°C"),
                color=coreText)
            Text("Battery "+number(r.energy.weightedMeanW," W",2)+
                " · "+number(r.energy.milliWhPerMinute," mWh/min",2),
                color=solarGold)
            Text(number(r.elapsedSeconds," s",0)+" · "+
                r.sampleCount+" samples · integrity "+r.quality.score+"/100",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("AT A GLANCE · A VS B",color=iceBlue,
            style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            sessionCard(a,"A",Modifier.weight(1f))
            sessionCard(b,"B",Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
            InstrumentKpi("Δ mean CPU",
                if(a.meanCpuC!=null && b.meanCpuC!=null)
                    number(b.meanCpuC-a.meanCpuC,"°C") else null,
                "B − A",coreCyan,Modifier.weight(1f))
            InstrumentKpi("Δ mean GPU",
                if(a.meanGpuC!=null && b.meanGpuC!=null)
                    number(b.meanGpuC-a.meanGpuC,"°C") else null,
                "B − A",coreCyan,Modifier.weight(1f))
            InstrumentKpi("Δ discharge rate",
                if(a.energy.milliWhPerMinute!=null &&
                    b.energy.milliWhPerMinute!=null)
                    number(b.energy.milliWhPerMinute-
                        a.energy.milliWhPerMinute," mWh/min",2) else null,
                "B − A; battery-terminal",solarGold,Modifier.weight(1f))
        }
        val warnings=mutableListOf<String>()
        if(a.session.status!="COMPLETED" || b.session.status!="COMPLETED")
            warnings+="At least one session is not completed"
        if(a.elapsedSeconds<=0.0||b.elapsedSeconds<=0.0 ||
            max(a.elapsedSeconds,b.elapsedSeconds)/
                min(a.elapsedSeconds,b.elapsedSeconds).coerceAtLeast(1.0)>1.2)
            warnings+="Run durations differ by over 20%"
        if(a.meanGpuLoad!=null && b.meanGpuLoad!=null &&
            abs(a.meanGpuLoad-b.meanGpuLoad)>10.0)
            warnings+="Observed GPU workloads differ by over 10 percentage points"
        if(a.session.configuration!=b.session.configuration)
            warnings+="Selected performance profiles differ"
        if(a.session.graphicsDriver!=null && b.session.graphicsDriver!=null &&
            a.session.graphicsDriver!=b.session.graphicsDriver)
            warnings+="Tagged graphics drivers differ"
        if(a.session.resolutionScale!=null && b.session.resolutionScale!=null &&
            a.session.resolutionScale!=b.session.resolutionScale)
            warnings+="Tagged resolution scales differ"
        if(a.energy.coveragePct<80.0 || b.energy.coveragePct<80.0)
            warnings+="Insufficient battery discharge-data coverage"
        if(a.session.note.isNullOrBlank() || b.session.note.isNullOrBlank() ||
            a.session.note!=b.session.note)
            warnings+="Matched game scene/track is not verified"
        Column(Modifier.fillMaxWidth().pureGlass(12).padding(10.dp)){
            Text("COMPARABILITY · "+if(warnings.isEmpty())
                "No obvious metadata conflicts" else "REVIEW REQUIRED",
                color=if(warnings.isEmpty())coreCyan else solarOrange,
                fontWeight=FontWeight.Bold)
            if(warnings.isEmpty())Text("Quality scores report telemetry integrity, "+
                "not identical game workloads or FPS.",color=coreSecondary)
            else warnings.take(4).forEach {
                Text("• "+it,color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
            }
            Text("A integrity "+a.quality.score+"/100 · B integrity "+
                b.quality.score+"/100. Neither score verifies gameplay FPS.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
    }
}
