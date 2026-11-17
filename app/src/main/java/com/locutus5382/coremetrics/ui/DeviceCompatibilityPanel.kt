package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.monitor.DeviceCapabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-shot, opt-in capability snapshot. Readable does not mean calibrated;
 * this never runs in READY, every REC tick or a composable recomposition.
 */
@Composable
internal fun DeviceCompatibilityPanel() {
    val ctx=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().pureGlass(12).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("DEVICE TELEMETRY COMPATIBILITY",
            color=iceBlue,style=MaterialTheme.typography.titleSmall)
        Text("Check which read-only CPU policy, GPU, thermal and battery "+
            "sources this Android ROM exposes. This does not certify "+
            "the accuracy of a sensor or promise that all games can be profiled.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=!busy,onClick={
            busy=true;error=""
            scope.launch {
                val result=withContext(Dispatchers.IO) {
                    runCatching{DeviceCapabilityProbe.report(ctx)}
                }
                busy=false
                result.fold(onSuccess={report=it},
                    onFailure={error=it.message?:"Capability check failed"})
            }
        }) {Text(if(busy)"Checking read-only sources..." else
            if(report==null)"Check this device" else "Refresh capability check")}
        if(error.isNotEmpty())Text(error,color=solarOrange,
            style=MaterialTheme.typography.bodySmall)
        if(report!=null) {
            Text("AVAILABLE means readable only. UNAVAILABLE means "+
                "not accessible to an ordinary app. Accuracy and actual "+
                "hardware limits still require device-specific validation.",
                color=solarGold,style=MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max=310.dp)
                .verticalScroll(rememberScrollState()).pureGlass(8).padding(8.dp)) {
                Text(report.orEmpty(),color=coreText,
                    style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
