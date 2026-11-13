package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.R
import android.graphics.drawable.Icon
import com.locutus5382.coremetrics.monitor.RecordingService

/** Independent HUD visibility/standby switch. Never stops an active recording. */
class HudTile:TileService(){
    private fun status():QuickTileSnapshot=try {
        QuickTileSnapshot.read(this)
    } catch(_:Exception) { QuickTileSnapshot.unknown() }

    override fun onStartListening(){super.onStartListening();refresh()}
    override fun onClick(){
        super.onClick()
        if(isLocked)unlockAndRun{toggle()}else toggle()
    }
    private fun toggle(){
        val current=status()
        val action=QuickTileStatePolicy.hud(current.mode,current.hudVisible)
        if(action==QuickTileStatePolicy.HudAction.WAIT){
            Toast.makeText(this,"HUD status unknown or starting; nothing duplicated",
                Toast.LENGTH_LONG).show()
            refresh();return
        }
        if((action==QuickTileStatePolicy.HudAction.ARM ||
                action==QuickTileStatePolicy.HudAction.SHOW) &&
            !Settings.canDrawOverlays(this)){
            Toast.makeText(this,"Opening CoreMetrics setup for overlay permission",
                Toast.LENGTH_SHORT).show()
            QuickTileSetup.open(this)
            return
        }
        if((action==QuickTileStatePolicy.HudAction.ARM ||
                action==QuickTileStatePolicy.HudAction.RECOVER) &&
            Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"Opening CoreMetrics setup for notifications",
                Toast.LENGTH_SHORT).show()
            QuickTileSetup.open(this)
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
        val secondary=when(current.mode){
            QuickTileStatePolicy.Mode.UNKNOWN->"Status unknown · Recover"
            QuickTileStatePolicy.Mode.RECOVERABLE->"Recover session"
            QuickTileStatePolicy.Mode.STARTING->"Starting…"
            QuickTileStatePolicy.Mode.RECORDING->
                if(current.hudVisible)"REC · HUD visible" else "REC · HUD hidden"
            QuickTileStatePolicy.Mode.STANDBY->
                if(current.hudVisible)"Ready · Tap REC" else "HUD hidden"
            QuickTileStatePolicy.Mode.IDLE->"Off"
        }
        qsTile?.apply{
            label="CoreMetrics HUD"
            subtitle=secondary
            contentDescription="CoreMetrics HUD, "+secondary
            stateDescription=secondary
            icon=Icon.createWithResource(this@HudTile,
                if(current.hudVisible)R.drawable.ic_qs_cm_on
                else R.drawable.ic_qs_cm_off)
            state=if(current.hudVisible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
