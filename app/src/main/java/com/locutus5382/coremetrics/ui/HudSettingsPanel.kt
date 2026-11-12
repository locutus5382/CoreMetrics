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
        Text("HUD tile toggles standby; REC tile starts/stops the session. "+
            "Use only one CoreMetrics variant at a time. "+
            "Launch standby, load Eden, then tap REC. Drag the glass pill to within " +
            "16dp of either edge to dock into a 12dp visual tab with a 44dp tap area. " +
            "Tap tab to expand, or ⚙ for live metric, opacity and REC controls. " +
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
                valueRange=.30f..90f/100f,modifier=Modifier.weight(1f))
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
                label={Text("Two-line Pro")})
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
            Triple("Battery W",options.showPower,options.copy(showPower=!options.showPower)),
            Triple("FPS",options.showFps,options.copy(showFps=!options.showFps))
        )
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            visible.forEach{(name,checked,newOption)->
                FilterChip(selected=checked,onClick={onChange(newOption)},
                    label={Text(name)})
            }
        }
        if(options.showFps)Text("FPS source currently unavailable; the HUD displays —.",
            color=solarGold,style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Four 30-sample graphs in Pro layout: CPU %, GPU %, CPU temp, " +
                "and layer FPS if available",color=coreText,
                modifier=Modifier.weight(1f))
            Switch(checked=options.showSparkline,
                onCheckedChange={onChange(options.copy(showSparkline=it))})
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
        Text("HUD drawer saves controls immediately. Micrographs reuse the " +
            "existing recorder data, with no extra polling. " +
            "When locked, unlock via notification or this Studio.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}
