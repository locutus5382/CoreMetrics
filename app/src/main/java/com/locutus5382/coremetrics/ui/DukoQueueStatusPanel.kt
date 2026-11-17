package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Only reflects requests explicitly clicked by the user. No idle/automatic
 * report computation, timer, polling or background service is introduced.
 */
@Composable
internal fun DukoQueueStatusPanel(queue:DukoExportQueueViewModel) {
    val active=queue.current
    val pending=queue.waitingTitles()
    if(active==null && pending.isEmpty() && queue.resultMessage.isBlank())return
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text("DUKO REPORT QUEUE",color=Color(0xFF71D7E2),
            style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
        if(active!=null) {
            Text("Preparing one request at a time · "+active.title,
                color=Color(0xFFE8F1F8),
                style=MaterialTheme.typography.bodySmall)
            Text(queue.stage,color=Color(0xFFA8C4D1),
                style=MaterialTheme.typography.labelSmall)
            if(queue.busy) {
                LinearProgressIndicator(
                    progress={queue.percent.coerceIn(0,100)/100f},
                    modifier=Modifier.fillMaxWidth())
                Text(queue.percent.toString()+"% · "+pending.size+
                    " more report"+if(pending.size==1)"" else "s"+" queued",
                    color=Color(0xFFA8C4D1),
                    style=MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick={queue.cancelCurrent()}) {
                Text(if(queue.busy)"Cancel current report" else
                    "Cancel current request")
            }
        }
        pending.forEachIndexed {index,title->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text((index+1).toString()+". "+title,
                    modifier=Modifier.weight(1f),
                    color=Color(0xFFA8C4D1),
                    style=MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick={queue.cancelQueued(index)}) {
                    Text("Remove")
                }
            }
        }
        if(queue.resultMessage.isNotBlank())
            Text(queue.resultMessage,color=Color(0xFFA8C4D1),
                style=MaterialTheme.typography.labelSmall)
    }
}
