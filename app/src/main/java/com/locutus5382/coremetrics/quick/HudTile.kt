package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.R
import com.locutus5382.coremetrics.monitor.RecordingService

/** Independent HUD visibility/standby switch. Never stops an active recording. */
class HudTile:TileService(){
    private data class State(val mode:QuickTileStatePolicy.Mode,val visible:Boolean)
    private fun status():State=try{
        val b=contentResolver.call(Uri.parse(
            "content://"+packageName+".monitor.status"),"status",null,null)
        State(QuickTileStatePolicy.mode(
            reachable=b!=null,
            serviceAlive=b?.getBoolean("serviceAlive",false)==true,
            pendingRecovery=b?.getBoolean("pendingRecovery",false)==true,
            running=b?.getBoolean("running",false)==true,
            starting=b?.getBoolean("starting",false)==true,
            standby=b?.getBoolean("standby",false)==true),
            b?.getBoolean("hudVisible",false)==true)
    }catch(_:Exception){State(QuickTileStatePolicy.Mode.UNKNOWN,false)}

    override fun onStartListening(){super.onStartListening();refresh()}
    override fun onClick(){
        super.onClick()
        if(isLocked)unlockAndRun{toggle()}else toggle()
    }
    private fun toggle(){
        val current=status()
        val action=QuickTileStatePolicy.hud(current.mode,current.visible)
        if(action==QuickTileStatePolicy.HudAction.WAIT){
            Toast.makeText(this,"HUD status unknown or starting; nothing duplicated",
                Toast.LENGTH_LONG).show()
            refresh();return
        }
        if(action==QuickTileStatePolicy.HudAction.ARM &&
            !Settings.canDrawOverlays(this)){
            Toast.makeText(this,"Grant overlay permission in CoreMetrics",
                Toast.LENGTH_LONG).show()
            return
        }
        if((action==QuickTileStatePolicy.HudAction.ARM ||
                action==QuickTileStatePolicy.HudAction.RECOVER) &&
            Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"Allow CoreMetrics notifications first",
                Toast.LENGTH_LONG).show()
            return
        }
        runCatching{
            val actionCode=when(action){
                QuickTileStatePolicy.HudAction.ARM->RecordingService.ARM
                QuickTileStatePolicy.HudAction.SHOW->RecordingService.HUD_SHOW
                QuickTileStatePolicy.HudAction.HIDE->RecordingService.HUD_HIDE
                QuickTileStatePolicy.HudAction.CLOSE->RecordingService.DISARM
                QuickTileStatePolicy.HudAction.RECOVER->RecordingService.RECOVER
                else->error("Unknown HUD action")
            }
            val i=Intent(this,RecordingService::class.java).setAction(actionCode)
            if(action==QuickTileStatePolicy.HudAction.ARM){
                val prefs=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                i.putExtra(RecordingService.LABEL,
                    prefs.getString("label","Gameplay")?:"Gameplay")
                    .putExtra(RecordingService.CONFIG,
                        prefs.getString("configuration","Stock")?:"Stock")
                    .putExtra(RecordingService.INTERVAL,
                        prefs.getLong("interval_ms",5000L))
            }
            if(action==QuickTileStatePolicy.HudAction.ARM ||
                action==QuickTileStatePolicy.HudAction.RECOVER)
                startForegroundService(i) else startService(i)
        }.onFailure{
            Toast.makeText(this,"HUD request failed: "+it.javaClass.simpleName,
                Toast.LENGTH_LONG).show()
        }
        refresh()
    }
    private fun refresh(){
        val current=status()
        qsTile?.apply{
            label=when(current.mode){
                QuickTileStatePolicy.Mode.UNKNOWN->"CM93 HUD · Try recover"
                QuickTileStatePolicy.Mode.RECOVERABLE->"CM93 HUD · Recover"
                QuickTileStatePolicy.Mode.STARTING->"CM93 HUD · Starting"
                QuickTileStatePolicy.Mode.RECORDING->
                    if(current.visible)"CM93 HUD · REC" else "CM93 HUD · Hidden REC"
                QuickTileStatePolicy.Mode.STANDBY->
                    if(current.visible)"CM93 HUD · ON" else "CM93 HUD · Hidden"
                else->"CM93 HUD · OFF"
            }
            icon=android.graphics.drawable.Icon.createWithResource(
                this@HudTile,R.drawable.ic_coremetrics_hud_tile)
            this.state=if(current.visible)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
