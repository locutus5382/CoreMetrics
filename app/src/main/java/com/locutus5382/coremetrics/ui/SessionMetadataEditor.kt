package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.data.SessionStore

/** Manually entered context. Never infer driver/resolution from sampling. */
@Composable
internal fun SessionMetadataEditor(session:SessionStore.Session,
    onSave:(String?,String?)->Unit,onClose:()->Unit) {
    var driverCustom by rememberSaveable(session.id){
        mutableStateOf(session.graphicsDriver?.startsWith("Custom: ")==true)
    }
    var scaleCustom by rememberSaveable(session.id){
        mutableStateOf(session.resolutionScale?.startsWith("Custom: ")==true)
    }
    var driverName by rememberSaveable(session.id){
        mutableStateOf(session.graphicsDriver?.takeIf{it.startsWith("Custom: ")}
            ?.removePrefix("Custom: ").orEmpty())
    }
    var scaleName by rememberSaveable(session.id){
        mutableStateOf(session.resolutionScale?.takeIf{it.startsWith("Custom: ")}
            ?.removePrefix("Custom: ").orEmpty())
    }
    Column(Modifier.fillMaxWidth().pureGlass(12).padding(11.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("MANUAL TEST TAGS · OPTIONAL",color=iceBlue,
            style=MaterialTheme.typography.titleSmall)
        Text("One-tap presets. Custom names are saved separately; "+
            "unknown stays unknown, not guessed from GPU activity.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Graphics driver",color=coreText)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("None","System","Turnip","Custom").forEach{tag->
                FilterChip(selected=if(tag=="Custom")driverCustom
                    else !driverCustom && (session.graphicsDriver?:"None")==tag,
                    onClick={
                        if(tag=="Custom")driverCustom=true else {
                            driverCustom=false
                            onSave(tag.takeUnless{it=="None"},session.resolutionScale)
                        }
                    },label={Text(tag)})
            }
        }
        if(driverCustom) {
            OutlinedTextField(value=driverName,
                onValueChange={driverName=it.take(38)},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Custom driver / version")})
            OutlinedButton(enabled=driverName.isNotBlank(),
                onClick={onSave("Custom: "+driverName.trim(),session.resolutionScale)}){
                Text("Save driver")
            }
        }
        Text("Resolution / display",color=coreText)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("None","Handheld 1x","Docked","Custom").forEach{tag->
                FilterChip(selected=if(tag=="Custom")scaleCustom
                    else !scaleCustom && (session.resolutionScale?:"None")==tag,
                    onClick={
                        if(tag=="Custom")scaleCustom=true else {
                            scaleCustom=false
                            onSave(session.graphicsDriver,tag.takeUnless{it=="None"})
                        }
                    },label={Text(tag)})
            }
        }
        if(scaleCustom) {
            OutlinedTextField(value=scaleName,onValueChange={scaleName=it.take(38)},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Custom resolution / scale")})
            OutlinedButton(enabled=scaleName.isNotBlank(),
                onClick={onSave(session.graphicsDriver,"Custom: "+scaleName.trim())}){
                Text("Save resolution")
            }
        }
        OutlinedButton(onClick=onClose){Text("Close tags")}
    }
}
