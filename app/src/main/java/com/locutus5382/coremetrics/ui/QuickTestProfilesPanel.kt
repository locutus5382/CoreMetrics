package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locutus5382.coremetrics.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit reusable TEST metadata, not device settings controls. The currently
 * selected shortcut is copied to a NEW recording at REC, never live-written
 * onto older recordings. Profile edits/deletion cannot rewrite old metadata.
 */
@Composable
internal fun QuickTestProfilesPanel(
    database:SessionStore,
    sessionId:Long?=null,
    onSessionChanged:()->Unit={}
) {
    val scope=rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var profiles by remember { mutableStateOf(emptyList<SessionStore.QuickProfile>()) }
    var activeId by remember { mutableLongStateOf(0L) }
    var selectedId by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }
    var performance by remember { mutableStateOf("Not specified") }
    var fan by remember { mutableStateOf("Not specified") }
    var cluster by remember { mutableStateOf("Not specified") }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(database,refresh) {
        val pair=withContext(Dispatchers.IO) {
            database.quickProfiles() to database.activeProfileId()
        }
        profiles=pair.first;activeId=pair.second
    }
    fun select(p:SessionStore.QuickProfile) {
        selectedId=p.id
        name=p.name;game=p.gameNote.orEmpty()
        driver=p.graphicsDriver?.removePrefix("Custom: ").orEmpty()
        resolution=p.resolutionScale?.removePrefix("Custom: ").orEmpty()
        performance=p.performanceMode?:"Not specified"
        fan=p.fanMode?:"Not specified"
        cluster=p.clusterTuneMode?:"Not specified"
        confirmDelete=false
    }
    fun clear() {
        selectedId=0L;name="";game="";driver="";resolution=""
        performance="Not specified";fan="Not specified";cluster="Not specified"
        confirmDelete=false
    }
    fun action(success:String,block:()->Unit,onSuccess:()->Unit={}) {
        if(busy)return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching { block() }
            }
            busy=false
            result.fold(
                onSuccess={
                    feedback=success;refresh++;onSessionChanged();onSuccess()
                },
                onFailure={feedback="Not saved: "+(it.message?:it.javaClass.simpleName)})
        }
    }
    Column(Modifier.fillMaxWidth().pureGlass(12).padding(11.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text("QUICK TEST PROFILES · MANUAL CONDITIONS",
            color=iceBlue,style=MaterialTheme.typography.titleSmall)
        Text("Reusable shortcuts. Use for next REC before recording, or explicitly apply to a completed session. Presets never configure hardware.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            profiles.forEach { p->
                FilterChip(selected=selectedId==p.id,onClick={select(p)},
                    label={Text(p.name+(if(activeId==p.id)" ✓ NEXT" else ""))})
            }
            OutlinedButton(onClick={clear()}){Text("+ New")}
        }
        OutlinedTextField(value=name,onValueChange={name=it.take(48)},
            singleLine=true,modifier=Modifier.fillMaxWidth(),
            label={Text("Shortcut name, e.g. Mr.Purple T30")})
        OutlinedTextField(value=game,onValueChange={game=it.take(64)},
            singleLine=true,modifier=Modifier.fillMaxWidth(),
            label={Text("Game / track (optional)")})
        OutlinedTextField(value=driver,onValueChange={driver=it.take(40)},
            singleLine=true,modifier=Modifier.fillMaxWidth(),
            label={Text("Graphics driver (custom name or System / Turnip)")})
        OutlinedTextField(value=resolution,onValueChange={resolution=it.take(38)},
            singleLine=true,modifier=Modifier.fillMaxWidth(),
            label={Text("Render scale (optional, e.g. Handheld 1x)")})
        QuickConditionChips("Odin performance",performance,
            listOf("Standard","Performance","High Performance")){performance=it}
        QuickConditionChips("Fan",fan,
            listOf("Off","Quiet","Smart","Sport","Custom")){fan=it}
        QuickConditionChips("ClusterTune",cluster,
            listOf("Stock","Small","Medium","Large","Custom")){cluster=it}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Button(enabled=!busy && name.isNotBlank(),
                onClick={
                    val parsedDriver=driver.trim().takeIf{it.isNotBlank()}?.let {
                        if(it=="System"||it=="Turnip"||it.startsWith("Custom: "))
                            it else "Custom: "+it
                    }
                    val parsedScale=resolution.trim().takeIf{it.isNotBlank()}?.let{
                        if(it=="Handheld 1x"||it=="Docked"||it.startsWith("Custom: "))
                            it else "Custom: "+it
                    }
                    // Capture one immutable UI snapshot BEFORE crossing to
                    // Dispatchers.IO; no Compose state read in the worker.
                    val id=selectedId
                    val chosenName=name
                    val chosenGame=game.ifBlank{null}
                    val chosenPerformance=performance.takeUnless{it=="Not specified"}
                    val chosenFan=fan.takeUnless{it=="Not specified"}
                    val chosenCluster=cluster.takeUnless{it=="Not specified"}
                    action("Profile saved",block={
                        database.saveQuickProfile(id=id,
                            name=chosenName,gameNote=chosenGame,
                            graphicsDriver=parsedDriver,resolutionScale=parsedScale,
                            performanceMode=chosenPerformance,
                            fanMode=chosenFan,clusterTuneMode=chosenCluster)
                    })
                }){Text(if(selectedId>0)"Update" else "Save preset")}
            OutlinedButton(enabled=!busy && selectedId>0,
                onClick={val id=selectedId
                    action("Preset set for NEXT recording") {
                        check(database.selectActiveProfile(id)){"Preset no longer exists"}
                    }
                }){Text("Use for next REC")}
        }
        if(sessionId!=null && selectedId>0) {
            OutlinedButton(enabled=!busy,onClick={val id=selectedId
                action("Selected session tagged with preset snapshot") {
                    check(database.applyQuickProfile(sessionId,id)) {
                        "Only completed sessions can be edited"
                    }
                }
            }){Text("Apply to session #"+sessionId)}
        }
        if(selectedId>0) {
            if(confirmDelete) {
                Text("Delete shortcut only? Past session tags remain unchanged.",
                    color=solarGold,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={confirmDelete=false}){Text("Cancel")}
                    Button(enabled=!busy,onClick={val id=selectedId
                        action("Shortcut deleted; past sessions preserved",
                            block={
                                check(database.deleteQuickProfile(id)){
                                    "Preset no longer exists"
                                }
                            },onSuccess={clear()})
                    }){Text("Delete shortcut")}
                }
            }else OutlinedButton(onClick={confirmDelete=true}) {
                Text("Delete selected shortcut…")
            }
        }
        Text("ACTIVE NEXT REC: "+
            (profiles.firstOrNull{it.id==activeId}?.name?:"No preset selected")+
            " · user-entered settings are not hardware-verified.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
        if(activeId!=0L) TextButton(enabled=!busy,
            onClick={action("Next recording preset cleared"){
                check(database.selectActiveProfile(0L))
            }}){Text("Clear next-recording preset")}
        if(feedback.isNotEmpty())Text(feedback,color=coreSecondary,
            style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickConditionChips(title:String,selected:String,
    choices:List<String>,onChange:(String)->Unit) {
    Text(title,color=coreText,style=MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        (listOf("Not specified")+choices).forEach { value->
            FilterChip(selected=selected==value,onClick={onChange(value)},
                label={Text(value)})
        }
    }
}

/** Lightweight shortcut selector for Live; edits remain in Sessions. */
@Composable
internal fun NextRecordingProfileSelector(database:SessionStore) {
    val scope=rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var profiles by remember { mutableStateOf(emptyList<SessionStore.QuickProfile>()) }
    var activeId by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf("") }
    LaunchedEffect(database,refresh) {
        val loaded=withContext(Dispatchers.IO) {
            database.quickProfiles() to database.activeProfileId()
        }
        profiles=loaded.first;activeId=loaded.second
    }
    Column(Modifier.fillMaxWidth().pureGlass(12).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("NEXT RECORDING · MANUAL TEST CONDITIONS",
            color=iceBlue,style=MaterialTheme.typography.labelMedium)
        Text("Choose a saved preset before tapping REC. Manage shortcuts under Sessions → Edit.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=activeId==0L,onClick={
                scope.launch{
                    val ok=withContext(Dispatchers.IO){database.selectActiveProfile(0L)}
                    feedback=if(ok)"No preset selected" else "Could not save selection"
                    refresh++
                }
            },label={Text("None")})
            profiles.forEach{p->
                FilterChip(selected=activeId==p.id,onClick={
                    scope.launch{
                        val ok=withContext(Dispatchers.IO){database.selectActiveProfile(p.id)}
                        feedback=if(ok)"Next REC: "+p.name else "Preset not available"
                        refresh++
                    }
                },label={Text(p.name)})
            }
        }
        if(feedback.isNotEmpty())Text(feedback,color=coreSecondary,
            style=MaterialTheme.typography.labelSmall)
    }
}
