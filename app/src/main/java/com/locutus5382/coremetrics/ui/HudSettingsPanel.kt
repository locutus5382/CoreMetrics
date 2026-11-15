package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.background
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
            "left, right or top edge to snap into a clearly marked CM edge tab. " +
            "Tap the tab to expand, or use ⇥ to collapse; ⚙ opens controls. " +
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
        Text("LAYOUT",color=iceBlue,style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=options.layout=="pill",
                onClick={onChange(options.copy(layout="pill"))},
                label={Text("Compact pill")})
            FilterChip(selected=options.layout=="pro",
                onClick={onChange(options.copy(layout="pro"))},
                label={Text("Rounded card · 2 lines")})
        }
        // A configuration preview, never a live hardware reading. Mirrors the
        // two-row HUD, label choices and size scaling without any polling.
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
        }
        val demoText=buildString {
            if(exampleReadings.isEmpty())append("REC · no metrics selected")
            exampleReadings.forEachIndexed {index,v ->
                if(index>0)append(if(exampleReadings.size>2 && index%2==0)
                    "\\n" else " · ")
                append(v)
            }
        }.replace("\\n","\n")
        Column(Modifier.fillMaxWidth().pureGlass(25).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("LIVE PREVIEW · ILLUSTRATIVE VALUES, NOT SENSOR READINGS",
                color=iceBlue,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                Column(Modifier.widthIn(min=210.dp,max=470.dp)
                    .background(Color(0xFF0A1A36).copy(alpha=opacityDraft),
                        RoundedCornerShape(if(options.layout=="pro")13.dp else 23.dp))
                    .border(1.dp,Color(0xB0CEE9FA),
                        RoundedCornerShape(if(options.layout=="pro")13.dp else 23.dp))
                    .padding(horizontal=10.dp,vertical=8.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(demoText,color=coreText,
                            fontSize=(12f*previewScale).sp,
                            lineHeight=(17f*previewScale).sp,
                            modifier=Modifier.weight(1f))
                        Box(Modifier.background(Color(0xFF101F36),RoundedCornerShape(8.dp))
                            .border(2.dp,Color.White,RoundedCornerShape(8.dp))
                            .padding(horizontal=7.dp,vertical=5.dp)) {
                            Text("▌ OS OK",fontSize=10.sp,color=Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(5.dp,Alignment.End)) {
                        Text("● STOP 05:00",color=solarGold,
                            fontSize=(11f*previewScale).sp)
                        Text("⚙",color=coreText,fontSize=16.sp)
                        Text("⇥",color=coreText,fontSize=16.sp)
                        Text("LOCK",color=coreSecondary,fontSize=10.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,
                verticalAlignment=Alignment.CenterVertically) {
                Text("Collapsed edge tab  ",color=coreSecondary,
                    style=MaterialTheme.typography.labelSmall)
                Box(Modifier.background(Color(0xF012385B),RoundedCornerShape(10.dp))
                    .border(2.dp,coreCyan,RoundedCornerShape(10.dp))
                    .padding(horizontal=9.dp,vertical=7.dp)) {
                    Text("CM\n● REC",color=coreText,fontSize=11.sp,
                        fontWeight=FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text("OS THERMAL BADGE · WHITE = NONE · AMBER = CAUTION · RED = SEVERE · GREY = UNAVAILABLE",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Visible while recording; Android's device-wide thermal status and sparse headroom forecast, not proof of CPU/GPU throttling. No polling during READY.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
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
