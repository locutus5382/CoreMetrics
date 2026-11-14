package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.monitor.HudOptions

/** Interactive UI only: the user toggles HUD monitoring through the same service. */
@Composable
internal fun HudSettingsPanel(
    options:HudOptions,
    isArmed:Boolean,
    recording:Boolean,
    locked:Boolean,
    permissionGranted:Boolean,
    onChange:(HudOptions)->Unit,
    onArm:()->Unit,
    closeUiOnArm:Boolean,
    onCloseUiOnArmChange:(Boolean)->Unit,
    onDisarm:()->Unit,
    onUnlock:()->Unit,
    onGrantPermission:()->Unit,
    onTile:()->Unit,
    onHudTile:()->Unit
) {
    var opacityDraft by remember(options.opacity) {
        mutableFloatStateOf(options.opacity)
    }
    Column(Modifier.fillMaxWidth().pureGlass(22).padding(15.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("FLOATING HUD STUDIO",color=iceBlue,
            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text("CM HUD tile shows/hides the HUD without stopping active recording. "+
            "Use only one CoreMetrics variant at a time. "+
            "Start Eden first, open HUD from Quick Settings, then tap REC. Drag the pill to the " +
            "left, right or top edge to snap into a labeled frosted dock tab. " +
            "Tap the tab to expand, or use ‹ to dock; ⚙ opens controls. " +
            "Standby performs no telemetry sampling.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Button(onClick=onArm,enabled=permissionGranted && !isArmed,
                colors=ButtonDefaults.buttonColors(containerColor=solarOrange)) {
                Text(if(isArmed) "Standby enabled" else "Launch standby")
            }
            OutlinedButton(onClick=onDisarm,enabled=isArmed || recording) {
                Text(if(recording) "Stop & close" else "Close HUD")
            }
            OutlinedButton(onClick=onTile){Text("Add REC tile")}
            OutlinedButton(onClick=onHudTile){Text("Add HUD tile")}
        }
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Close this screen after launching standby",
                    color=coreSecondary,style=MaterialTheme.typography.bodyMedium)
                Text("Optional. Frees the UI for Android to reclaim; does not " +
                    "stop the monitor or guarantee an immediate RAM reduction.",
                    color=coreSecondary,style=MaterialTheme.typography.labelSmall)
            }
            Switch(checked=closeUiOnArm,onCheckedChange=onCloseUiOnArmChange)
        }
        if(isArmed) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(locked) "LOCKED · Click-through HUD" else "UNLOCKED · Interactive HUD",
                    color=if(locked) solarGold else coreSecondary,
                    style=MaterialTheme.typography.bodySmall)
                if(locked) OutlinedButton(onClick=onUnlock) { Text("Unlock HUD") }
            }
            Text("Lock from the floating HUD. While locked, overlay REC and drag " +
                "are disabled; unlock from the notification or here. On Android 12+, " +
                "overlapping overlays can restrict pass-through.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        }
        if(!permissionGranted) {
            Text("Android overlay permission is required for the floating HUD.",
                color=solarGold,style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onGrantPermission){Text("Grant overlay permission")}
        }
        Row(Modifier.fillMaxWidth().pureGlass(28).padding(12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("COREMETRICS  ·  READY",color=Color.White,
                    style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
                Text("No readings until REC",color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.background(solarOrange.copy(alpha=.24f),
                RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=8.dp)) {
                Text("● REC",color=solarGold,fontWeight=FontWeight.Bold)
            }
        }
        Text("GLASS TRANSPARENCY",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Slider(value=opacityDraft,
                onValueChange={opacityDraft=it},
                onValueChangeFinished={onChange(options.copy(opacity=opacityDraft))},
                valueRange=.30f..90f/100f,colors=cmSliderColors(),
                modifier=Modifier.weight(1f))
            Text((opacityDraft*100).toInt().toString()+"%",color=Color.White)
        }
        Text("HUD SIZE",color=iceBlue,style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("small" to "Small","regular" to "Medium",
                "large" to "Large").forEach{(key,label)->
                FilterChip(selected=options.size==key,
                    onClick={onChange(options.copy(size=key))},label={Text(label)})
            }
        }
        Text("LAYOUT",color=iceBlue,style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=options.layout=="pill",
                onClick={onChange(options.copy(layout="pill"))},
                label={Text("Compact pill")})
            FilterChip(selected=options.layout=="pro",
                onClick={onChange(options.copy(layout="pro"))},
                label={Text("Rounded card · 2 lines")})
        }
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text("VISIBLE TELEMETRY",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        val visible=listOf(
            Triple("CPU °C",options.showCpuTemp,options.copy(showCpuTemp=!options.showCpuTemp)),
            Triple("GPU °C",options.showGpuTemp,options.copy(showGpuTemp=!options.showGpuTemp)),
            Triple("CPU %",options.showCpuLoad,options.copy(showCpuLoad=!options.showCpuLoad)),
            Triple("GPU %",options.showGpuLoad,options.copy(showGpuLoad=!options.showGpuLoad)),
            Triple("RAM",options.showRam,options.copy(showRam=!options.showRam)),
            Triple("Battery W",options.showPower,options.copy(showPower=!options.showPower))
        )
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            visible.forEach{(name,checked,newOption)->
                FilterChip(selected=checked,onClick={onChange(newOption)},
                    label={Text(name)})
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ADAPTIVE METRIC COLORS",color=iceBlue,
                    style=MaterialTheme.typography.labelLarge)
                Text("CPU/GPU: 65°C yellow · 80°C red. High CPU/GPU load " +
                    "can turn yellow but never red on utilization alone. " +
                    "RAM uses Android's low-memory threshold. Display-only.",
                    color=coreSecondary,style=MaterialTheme.typography.bodySmall)
            }
            Switch(checked=options.adaptiveColors,
                onCheckedChange={onChange(options.copy(adaptiveColors=it))})
        }
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text("RECORDING INDICATOR COLOR",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("red" to "Red","orange" to "Orange",
                "cyan" to "Cyan","violet" to "Violet").forEach{(key,label)->
                FilterChip(selected=options.indicatorColor==key,
                    onClick={onChange(options.copy(indicatorColor=key))},
                    label={Text(label)})
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Blink only the indicator while recording",color=coreText,
                modifier=Modifier.weight(1f))
            Switch(checked=options.blink,
                onCheckedChange={onChange(options.copy(blink=it))})
        }
        Text("HUD draws from existing recorder samples only. Charts are in " +
            "post-session analysis. Hiding the HUD does not stop REC.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
