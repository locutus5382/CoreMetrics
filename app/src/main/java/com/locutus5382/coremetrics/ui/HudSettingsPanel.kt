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
import androidx.compose.ui.text.font.FontFamily
import com.locutus5382.coremetrics.monitor.HudReadoutFormatter
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
            "Drag a metric zone to reposition it, or swipe to the left/right " +
            "edge to auto-collapse. Tap the tiny edge indicator to expand. " +
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
        Text("HUD DESIGN LAB · FIVE THEMES",color=iceBlue,
            style=MaterialTheme.typography.labelLarge)
        Text("Choose the in-game presentation. The same recorder, REC/STOP, " +
            "telemetry and overlay owner serve every theme. Legacy themes " +
            "are migrated to Striker / Tactical Card / Cinema Strip.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            HudLayoutPolicy.themes.forEach {id ->
                FilterChip(
                    selected=HudLayoutPolicy.normalize(options.layout)==id,
                    onClick={onChange(options.copy(layout=id))},
                    label={Text(HudLayoutPolicy.label(id))})
            }
        }
        val theme=HudLayoutPolicy.normalize(options.layout)
        Text(HudLayoutPolicy.description(theme),
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)

        // Fixed, illustrative values; NEVER initiates device sampling or
        // report preparation. The overlay uses the exact same text model.
        val demo=HudReadoutFormatter.render(theme,
            HudReadoutFormatter.Values(
                cpuTemp=if(options.showCpuTemp)"43.0 "+if(HudLayoutPolicy.lens(theme))
                    "C" else "°C" else null,
                gpuTemp=if(options.showGpuTemp)"39.8 "+if(HudLayoutPolicy.lens(theme))
                    "C" else "°C" else null,
                cpuUse=if(options.showCpuLoad)"30 %" else null,
                gpuUse=if(options.showGpuLoad)"38 %" else null,
                ram=if(options.showRam)"6.2 G" else null,
                power=if(options.showPower)"3.8 W" else null,
                batteryTemp=if(options.showBatteryTemp)"25.0 C" else null
            ),recording=true)
        val shape=RoundedCornerShape(
            if(HudLayoutPolicy.lens(theme))0.dp
            else if(theme==HudLayoutPolicy.STRIP)5.dp else 10.dp)
        val ghost=theme==HudLayoutPolicy.GHOST
        val lens=HudLayoutPolicy.lens(theme)
        val tactical=theme==HudLayoutPolicy.TACTICAL
        val striker=theme==HudLayoutPolicy.STRIKER
        Column(Modifier.fillMaxWidth().pureGlass(25).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("IN-GAME PREVIEW · FIXED DEMONSTRATION VALUES",
                color=iceBlue,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.End) {
                val bg=if(lens)listOf(Color(0xED182535),Color(0xB01E3548))
                    else if(striker)listOf(Color(0xE2071328),Color(0xC9104967))
                    else listOf(Color(0xE208192B),Color(0xBA174365))
                val borderColor=when {
                    ghost->Color.Transparent
                    lens->Color(0xB9DEEDF5)
                    else->Color(0xFF55CBED)
                }
                val panel=Modifier
                    .widthIn(min=if(theme==HudLayoutPolicy.STRIP)390.dp
                        else if(striker)340.dp else 200.dp,
                        max=if(theme==HudLayoutPolicy.STRIP)750.dp
                        else if(striker)480.dp else 390.dp)
                    .then(if(ghost)Modifier else
                        Modifier.background(Brush.linearGradient(bg),shape)
                            .border(1.dp,borderColor,shape))
                    .padding(horizontal=if(ghost)3.dp else 12.dp,
                        vertical=if(ghost)2.dp else 9.dp)
                Column(panel,
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(tactical)
                        Text("COREMETRICS / LIVE TELEMETRY  ● REC",
                            color=Color(0xFF63D6EC),fontSize=10.sp)
                    if(striker){
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text(demo.primary,
                                color=Color(0xFFF3F9FF),fontSize=12.sp,
                                lineHeight=17.sp,
                                fontWeight=FontWeight.SemiBold)
                            Text(demo.secondary,
                                color=Color(0xFFB9E6F2),fontSize=12.sp,
                                lineHeight=17.sp)
                            Text("● STOP 25:00\n↔ DRAG  [LOCK]",
                                color=Color(0xFFFF8994),fontSize=10.sp,
                                lineHeight=17.sp)
                        }
                    }else if(tactical){
                        listOf(demo.primary,demo.secondary,demo.tertiary)
                            .filter{it.isNotBlank()}.forEach {value->
                                Box(Modifier.fillMaxWidth()
                                    .background(Color(0x44142840),shape)
                                    .padding(6.dp)) {
                                    Text(value,color=Color.White,fontSize=12.sp,
                                        lineHeight=16.sp)
                                }
                            }
                        Text("● STOP 25:00     [LOCK]    [ANCHOR]",
                            color=Color(0xFFFF8C99),fontSize=11.sp)
                    }else if(lens){
                        Text(demo.primary,color=Color.White,
                            fontFamily=FontFamily.Monospace,
                            fontSize=12.sp,lineHeight=18.sp)
                        HorizontalDivider(color=Color.White.copy(alpha=.30f))
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("● 25:00",color=Color(0xFFFF667C),
                                fontFamily=FontFamily.Monospace,
                                fontSize=12.sp)
                            Text("[ LOCK ]",color=Color.White,
                                fontFamily=FontFamily.Monospace,
                                fontSize=11.sp)
                        }
                    }else{
                        Text(demo.primary.ifBlank{"CM · REC"},
                            color=Color.White,
                            fontFamily=if(ghost)FontFamily.Default
                                else FontFamily.Monospace,
                            fontSize=if(ghost)12.sp else 11.sp,
                            lineHeight=17.sp)
                        Text("● STOP 25:00  [LOCK]",
                            color=Color(0xFFFF8C99),fontSize=10.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text("SWIPE TO EDGE →  COLLAPSED",
                    color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
                Box(Modifier.width(44.dp).height(44.dp)
                    .background(Color(0xBA11263A),RoundedCornerShape(7.dp))
                    .border(1.dp,Color(0xA855D9EC),RoundedCornerShape(7.dp)),
                    contentAlignment=Alignment.Center) {
                    Text(if(lens)"│●" else "●",
                        color=Color(0xFFFF6C79),fontSize=12.sp)
                }
            }
            Text("Preview does not run sensors. The real collapsed HUD " +
                "retains a 44dp accessible touch target, with a 2dp line " +
                "and recording dot in DUKO Lens.",
                color=coreSecondary,style=MaterialTheme.typography.labelSmall)
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
