package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.data.SessionStore

/**
 * Dedicated v0.9.9.2 UI. Session IDs are selections only:
 * analysis is deferred until after the user picks a document destination.
 */
@Composable
internal fun DukoComparisonPanel(
    sessions:List<SessionStore.Session>,
    busy:Boolean,
    onExport:(List<Long>,Long,CompareStudioAnalysis.TestedVariable,String)->Unit
) {
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    var baselineId by rememberSaveable { mutableLongStateOf(0L) }
    var variableName by rememberSaveable {
        mutableStateOf(CompareStudioAnalysis.TestedVariable.UNDECLARED.name)
    }
    val completed=sessions.filter{it.status!="RECORDING"}
    val valid=completed.take(50)
    val available=valid.map{it.id}.toSet()
    val selected=selectedCsv.split(',').mapNotNull{it.toLongOrNull()}
        .filter{it in available}.distinct().take(5)
    val baseline=baselineId.takeIf{it in selected}?:selected.firstOrNull()?:0L
    val variable=runCatching {
        CompareStudioAnalysis.TestedVariable.valueOf(variableName)
    }.getOrDefault(CompareStudioAnalysis.TestedVariable.UNDECLARED)
    val noteColor=Color(0xFFA8C4D1)
    val accent=Color(0xFF71D7E2)

    Column(
        Modifier.fillMaxWidth().padding(vertical=10.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)
    ) {
        Text("DUKO COMPARISON REPORT · v0.9.9.2",
            color=accent,fontWeight=FontWeight.Bold)
        Text("Select 2–5 saved sessions. Every chosen recording is scanned in full "+
            "(up to 250,000 samples each). Choose a reference baseline; "+
            "a baseline is not a performance winner.",
            color=noteColor,style=MaterialTheme.typography.bodySmall)
        Text("Select recordings · "+selected.size+"/5",
            color=accent,style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            valid.forEach { s->
                val checked=s.id in selected
                FilterChip(
                    selected=checked,
                    enabled=!busy && (checked || selected.size<5),
                    onClick={
                        val updated=if(checked)selected.filterNot{it==s.id}
                            else selected+s.id
                        selectedCsv=updated.joinToString(",")
                        if(baselineId !in updated)baselineId=updated.firstOrNull()?:0L
                    },
                    label={
                        Text("#"+s.id+" · "+
                            (s.note?.take(18)?.takeIf{it.isNotBlank()}
                                ?:s.label.take(18))+
                            " · "+(s.graphicsDriver?:"driver ?").take(18))
                    }
                )
            }
        }
        Text("Baseline · choose one of the selected sessions",
            color=accent,style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            selected.forEach{id->
                FilterChip(selected=baseline==id,enabled=!busy,
                    onClick={baselineId=id},label={Text("Session #"+id)})
            }
        }
        Text("Declared test variable (manual metadata only)",
            color=accent,style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompareStudioAnalysis.TestedVariable.entries.forEach{entry->
                FilterChip(selected=entry==variable,enabled=!busy,
                    onClick={variableName=entry.name},label={Text(entry.label)})
            }
        }
        Text("All P50/P95 data remain observational. Numeric differences "+
            "are hidden when source identity, coverage or device comparability "+
            "is insufficient. Reports never infer FPS, workload equivalence "+
            "or a winning driver.",
            color=noteColor,style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled=!busy && selected.size in 2..5 && baseline>0,
                onClick={onExport(selected.toList(),baseline,variable,"png")}
            ){Text("DUKO Comparison PNG")}
            OutlinedButton(
                enabled=!busy && selected.size in 2..5 && baseline>0,
                onClick={onExport(selected.toList(),baseline,variable,"pdf")}
            ){Text("DUKO Comparison PDF")}
        }
        if(busy)Text("Generating offline report from complete recordings…",
            color=noteColor,style=MaterialTheme.typography.labelSmall)
        if(completed.size>50)Text("Showing the 50 most recent saved sessions.",
            color=noteColor,style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
    }
}
