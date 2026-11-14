package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.locutus5382.coremetrics.data.SessionMetadata
import com.locutus5382.coremetrics.data.SessionStore

/** Manually entered context. Never infer driver/resolution from sampling. */
@Composable
internal fun SessionMetadataEditor(session:SessionStore.Session,
    onDriver:(String?,(Boolean,String)->Unit)->Unit,
    onResolution:(String?,(Boolean,String)->Unit)->Unit,
    onNote:(String?,(Boolean,String)->Unit)->Unit,onConfig:(String)->Unit,
    onDelete:()->Unit,onClose:()->Unit) {
    var gameNote by rememberSaveable(session.id) {
        mutableStateOf(session.note.orEmpty())
    }
    var confirmDelete by rememberSaveable(session.id) {
        mutableStateOf(false)
    }
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
    var noteFeedback by remember(session.id){mutableStateOf("")}
    var noteSaving by remember(session.id){mutableStateOf(false)}
    var driverFeedback by remember(session.id){mutableStateOf("")}
    var scaleFeedback by remember(session.id){mutableStateOf("")}
    fun submitNote() {
        if(noteSaving)return
        val cleaned=SessionMetadata.note(gameNote)
        noteSaving=true
        noteFeedback="Saving note…"
        onNote(cleaned){saved,detail->
            noteSaving=false
            if(saved) {
                gameNote=cleaned.orEmpty()
                noteFeedback="Game note saved"
            }else noteFeedback="Note not saved: "+detail
        }
    }
    fun submitDriver(value:String?) {
        if(value!=null && SessionMetadata.driver(value)==null) {
            driverFeedback="Invalid driver tag. Use a short name without control characters."
            return
        }
        driverFeedback="Saving driver…"
        onDriver(value){saved,detail->
            driverFeedback=if(saved)"Driver saved: "+(value?:"None")
                else "Driver not saved: "+detail
        }
    }
    fun submitScale(value:String?) {
        if(value!=null && SessionMetadata.resolution(value)==null) {
            scaleFeedback="Invalid resolution tag. Check the custom value."
            return
        }
        scaleFeedback="Saving resolution…"
        onResolution(value){saved,detail->
            scaleFeedback=if(saved)"Resolution saved: "+(value?:"None")
                else "Resolution not saved: "+detail
        }
    }
    Column(Modifier.fillMaxWidth().pureGlass(12).padding(11.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("EDIT SESSION · MANUAL METADATA",color=iceBlue,
            style=MaterialTheme.typography.titleSmall)
        Text("One-tap presets. Custom names are saved separately; "+
            "unknown stays unknown, not guessed from GPU activity.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Text("Game / track note",color=coreText)
        OutlinedTextField(value=gameNote,
            onValueChange={gameNote=it.take(64);noteFeedback=""},
            enabled=!noteSaving,
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text("e.g. Hades · same scene")})
        OutlinedButton(
            enabled=!noteSaving &&
                SessionMetadata.note(gameNote)!=session.note,
            onClick={submitNote()}
        ){Text(if(noteSaving)"Saving note…" else "Save game note")}
        if(noteFeedback.isNotBlank())Text(noteFeedback,color=coreSecondary,
            style=MaterialTheme.typography.labelSmall)
        Text("Configuration profile",color=coreText)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("Stock","Underclocking","Custom").forEach { profile ->
                FilterChip(selected=session.configuration==profile,
                    onClick={onConfig(profile)},label={Text(profile)})
            }
        }
        Text("Profile is user-supplied, not verified from CPU clocks.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        HorizontalDivider()
        Text("Graphics driver · saved: "+
            (session.graphicsDriver?:"None"),color=coreText)
        if(driverFeedback.isNotBlank())Text(driverFeedback,
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("None","System","Turnip","Custom").forEach{tag->
                FilterChip(selected=if(tag=="Custom")driverCustom
                    else !driverCustom && (session.graphicsDriver?:"None")==tag,
                    onClick={
                        if(tag=="Custom")driverCustom=true else {
                            driverCustom=false
                            submitDriver(tag.takeUnless{it=="None"})
                        }
                    },label={Text(tag)})
            }
        }
        if(driverCustom) {
            OutlinedTextField(value=driverName,
                onValueChange={driverName=it.take(38);driverFeedback=""},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={
                    if(driverName.isNotBlank())
                        submitDriver("Custom: "+driverName.trim())
                }),
                label={Text("Custom driver / version")})
            OutlinedButton(enabled=driverName.isNotBlank(),
                onClick={submitDriver("Custom: "+driverName.trim())}){
                Text("Save driver")
            }
        }
        Text("Resolution / display · saved: "+
            (session.resolutionScale?:"None"),color=coreText)
        if(scaleFeedback.isNotBlank())Text(scaleFeedback,
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("None","Handheld 1x","Docked","Custom").forEach{tag->
                FilterChip(selected=if(tag=="Custom")scaleCustom
                    else !scaleCustom && (session.resolutionScale?:"None")==tag,
                    onClick={
                        if(tag=="Custom")scaleCustom=true else {
                            scaleCustom=false
                            submitScale(tag.takeUnless{it=="None"})
                        }
                    },label={Text(tag)})
            }
        }
        if(scaleCustom) {
            OutlinedTextField(value=scaleName,
                onValueChange={scaleName=it.take(38);scaleFeedback=""},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={
                    if(scaleName.isNotBlank())submitScale("Custom: "+scaleName.trim())
                }),
                label={Text("Custom resolution / scale")})
            OutlinedButton(enabled=scaleName.isNotBlank(),
                onClick={submitScale("Custom: "+scaleName.trim())}){
                Text("Save resolution")
            }
        }
        HorizontalDivider()
        Text("Deleting permanently removes samples and linked A/B pairs.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        if(confirmDelete) {
            Text("Permanently delete session #"+session.id+" ("+
                session.count+" samples)?",color=MaterialTheme.colorScheme.error)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={confirmDelete=false}){Text("Cancel")}
                Button(onClick={
                    confirmDelete=false
                    onDelete()
                }){Text("Confirm delete #"+session.id)}
            }
        }else{
            OutlinedButton(onClick={confirmDelete=true}){Text("Delete session…")}
        }
        OutlinedButton(onClick=onClose){Text("Close editor")}
    }
}
