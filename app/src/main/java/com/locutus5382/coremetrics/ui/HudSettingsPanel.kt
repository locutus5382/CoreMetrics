package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import com.locutus5382.coremetrics.monitor.HudOptions
import com.locutus5382.coremetrics.monitor.HudLayoutPolicy

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
            "Start Eden first, open HUD from Quick Settings, then tap REC. " +
            "Drag the expanded HUD to a screen corner to park it. " +
            "Tap ⇥ to collapse deliberately; tap the tab to expand. " +
            "Configure the HUD metrics and appearance here, not during gameplay. " +
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
        Text("HUD LAYOUT",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(
                HudLayoutPolicy.PILL to "Classic pill · default",
                HudLayoutPolicy.GLASS_CARD to "Compact Glass Card",
                HudLayoutPolicy.RIBBON to "Telemetry Ribbon"
            ).forEach {(id,label)->
                FilterChip(selected=HudLayoutPolicy.normalize(options.layout)==id,
                    onClick={onChange(options.copy(layout=id))},
                    label={Text(label)})
            }
        }
        Text(when(HudLayoutPolicy.normalize(options.layout)) {
            HudLayoutPolicy.GLASS_CARD ->
                "Stacked glass panel with cyan outline, slim telemetry header and " +
                "REC/STOP controls below the readings. Best for readability."
            HudLayoutPolicy.RIBBON ->
                "Low-profile horizontal telemetry strip for landscape gameplay. " +
                "On narrow screens it wraps rather than hiding selected readings."
            else ->
                "Your original compact floating HUD pill. Preserved as default."
        },color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Text("All three share the same recorder, metrics, lock/unlock and " +
            "dock controls. Changing layout never restarts REC or adds an overlay.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)

        // Visual-only preview: fixed example numbers, no extra sensor polling.
        val previewLayout=HudLayoutPolicy.normalize(options.layout)
        val isCard=previewLayout==HudLayoutPolicy.GLASS_CARD
        val isRibbon=previewLayout==HudLayoutPolicy.RIBBON
        val previewScale=when(options.size) {
            "small"->.82f
            "large"->1.20f
            else->1f
        }
        val exampleReadings=buildList {
            if(options.showCpuTemp)add("CPU T 52.3°C")
            if(options.showGpuTemp)add("GPU T 44.1°C")
            if(options.showCpuLoad)add("CPU % 42%")
            if(options.showGpuLoad)add("GPU % 58%")
            if(options.showRam)add("RAM 5.9 GiB")
            if(options.showPower)add("BAT 4.2 W")
            if(options.showBatteryTemp)add("BAT T 35.2°C")
        }
        val demoText=buildString {
            if(exampleReadings.isEmpty())append("REC · no metrics selected")
            exampleReadings.forEachIndexed {index,value ->
                append(HudLayoutPolicy.divider(previewLayout,index,
                    exampleReadings.size))
                append(value)
            }
        }
        val previewShape=RoundedCornerShape(when(previewLayout) {
            HudLayoutPolicy.GLASS_CARD -> 16.dp
            HudLayoutPolicy.RIBBON -> 12.dp
            else -> 23.dp
        })
        val previewGlassColors=when(previewLayout) {
            HudLayoutPolicy.GLASS_CARD -> listOf(
                Color(0xFF071428).copy(alpha=opacityDraft),
                Color(0xFF1B567C).copy(alpha=opacityDraft),
                Color(0xFF0B2344).copy(alpha=opacityDraft))
            HudLayoutPolicy.RIBBON -> listOf(
                Color(0xFF051326).copy(alpha=opacityDraft),
                Color(0xFF163450).copy(alpha=opacityDraft))
            else -> listOf(
                Color(0xFF071128).copy(alpha=opacityDraft),
                Color(0xFF1A386B).copy(alpha=opacityDraft))
        }
        Column(Modifier.fillMaxWidth().pureGlass(25).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("LAYOUT PREVIEW · ILLUSTRATIVE VALUES, NOT LIVE READINGS",
                color=iceBlue,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.End) {
                val previewModifier=Modifier
                    .widthIn(min=if(isRibbon)350.dp else 245.dp,
                        max=if(isRibbon)690.dp else if(isCard)415.dp else 455.dp)
                    .background(Brush.linearGradient(previewGlassColors),previewShape)
                    .border(1.dp,Color(0xB0CEE9FA),previewShape)
                    .padding(horizontal=11.dp,vertical=if(isCard)9.dp else 7.dp)
                if(isCard) {
                    Column(previewModifier,
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("COREMETRICS  /  LIVE TELEMETRY",
                            color=Color(0xFF87E7FF),
                            fontSize=10.sp,fontWeight=FontWeight.Bold,
                            letterSpacing=1.sp)
                        Text(demoText,color=coreText,
                            fontSize=(12.5f*previewScale).sp,
                            lineHeight=(18f*previewScale).sp)
                        HorizontalDivider(color=Color(0x35CEE9FA))
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween,
                            verticalAlignment=Alignment.CenterVertically) {
                            if(locked)Text("LOCK · TAP TO UNLOCK",
                                color=solarGold,fontSize=11.sp)
                            else {
                                Text("● STOP 05:00",color=solarGold,
                                    fontSize=(11f*previewScale).sp,
                                    fontWeight=FontWeight.SemiBold)
                                Text("⇥     LOCK",color=coreText,fontSize=11.sp)
                            }
                        }
                    }
                }else{
                    Row(previewModifier,
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(demoText,color=coreText,
                            fontSize=(if(isRibbon)11.2f else 12f).times(previewScale).sp,
                            lineHeight=(17f*previewScale).sp,
                            modifier=Modifier.weight(1f))
                        if(locked) {
                            Text("LOCK",fontSize=11.sp,color=solarGold)
                        }else{
                            Column(horizontalAlignment=Alignment.End,
                                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("● STOP 05:00",color=solarGold,
                                    fontSize=(11f*previewScale).sp)
                                Text("⇥   LOCK",color=coreText,fontSize=11.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,
                verticalAlignment=Alignment.CenterVertically) {
                Text("Collapsed edge tab  ",color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
                Box(Modifier.background(Color(0xF012385B),RoundedCornerShape(10.dp))
                    .border(2.dp,Color(0xFFFF6870),RoundedCornerShape(10.dp))
                    .padding(horizontal=9.dp,vertical=7.dp)) {
                    Text("● R\n05:00",color=Color(0xFFFFAFB4),fontSize=11.sp,
                        fontWeight=FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text("OS WARNING ACCENT · AMBER = CAUTION · RED = SEVERE",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Normal or unavailable Android thermal signals do not occupy HUD space. A narrow amber/red accent appears only for a reported OS thermal warning, never for CPU temperature alone. No polling during READY.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Text("VISIBLE TELEMETRY",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        val visible=listOf(
            Triple("CPU °C",options.showCpuTemp,options.copy(showCpuTemp=!options.showCpuTemp)),
            Triple("GPU °C",options.showGpuTemp,options.copy(showGpuTemp=!options.showGpuTemp)),
            Triple("CPU %",options.showCpuLoad,options.copy(showCpuLoad=!options.showCpuLoad)),
            Triple("GPU %",options.showGpuLoad,options.copy(showGpuLoad=!options.showGpuLoad)),
            Triple("RAM",options.showRam,options.copy(showRam=!options.showRam)),
            Triple("Battery W",options.showPower,options.copy(showPower=!options.showPower)),
            Triple("Battery °C",options.showBatteryTemp,
                options.copy(showBatteryTemp=!options.showBatteryTemp))
        )
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            visible.forEach{(name,checked,newOption)->
                FilterChip(selected=checked,onClick={onChange(newOption)},
                    label={Text(name)})
            }
        }
        if(options.showBatteryTemp) {
            Text("BATTERY TEMPERATURE · OPTIONAL WARMTH ADVISORIES",
                color=iceBlue,style=MaterialTheme.typography.labelLarge)
            Text("Normal: plain text below 39°C. Warm: thin yellow outline from 39°C. "+
                "Hot: thin orange outline after more than 42°C for 15 seconds. "+
                "Releases below 38°C / 41°C with 10 seconds of hysteresis. "+
                "App advisories, not device-certified critical limits.",
                color=coreSecondary,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(9.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("BAT T 35.2°C",color=coreText,
                    style=MaterialTheme.typography.labelSmall)
                Text("BAT T 40.3°C",color=Color(0xFFEAB308),
                    style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.border(1.dp,Color(0xFFEAB308),
                        RoundedCornerShape(3.dp)).padding(horizontal=4.dp,vertical=2.dp))
                Text("BAT T 43.2°C",color=Color(0xFFF97316),
                    style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.border(1.dp,Color(0xFFF97316),
                        RoundedCornerShape(3.dp)).padding(horizontal=4.dp,vertical=2.dp))
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
