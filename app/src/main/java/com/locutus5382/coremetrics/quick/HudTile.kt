package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.ComponentName
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

/**
 * Separate, opt-in HUD STANDBY toggle; never stops an active recording.
 * This is not the REC tile and never samples hardware by itself.
 */
class HudTile:TileService() {
    private data class State(
        val recording:Boolean,val standby:Boolean,val hudVisible:Boolean
    )
    private fun state():State=try {
        val b=contentResolver.call(Uri.parse(
            "content://"+packageName+".monitor.status"),"status",null,null)
        val status=b?.getString("hudStatus").orEmpty()
        val visible=status=="HUD_ATTACHED" ||
            status=="HUD_ALREADY_ATTACHED" || status=="HUD_CONFIGURED" ||
            status.startsWith("HUD_LOCKED") || status.startsWith("HUD_UNLOCKED")
        State(b?.getBoolean("running",false)==true ||
            b?.getBoolean("starting",false)==true,
            b?.getBoolean("standby",false)==true,visible)
    }catch(_:Exception){State(false,false,false)}

    override fun onStartListening(){super.onStartListening();refresh()}
    override fun onClick(){
        super.onClick()
        if(isLocked)unlockAndRun{toggle()} else toggle()
    }
    private fun toggle(){
        val current=state()
        if(current.recording){
            Toast.makeText(this,
                "Recording active · use the REC tile to stop and save",
                Toast.LENGTH_LONG).show()
            refresh();return
        }
        if(current.standby){
            runCatching{
                startService(Intent(this,RecordingService::class.java)
                    .setAction(RecordingService.DISARM))
            }.onFailure{
                Toast.makeText(this,"Could not close HUD",Toast.LENGTH_LONG).show()
            }
            refresh();return
        }
        if(!Settings.canDrawOverlays(this)){
            Toast.makeText(this,
                "Open CoreMetrics and grant floating overlay permission",
                Toast.LENGTH_LONG).show()
            return
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"Allow CoreMetrics notifications first",
                Toast.LENGTH_LONG).show()
            return
        }
        val preferences=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
        runCatching {
            startForegroundService(Intent(this,RecordingService::class.java)
                .setAction(RecordingService.ARM)
                .putExtra(RecordingService.LABEL,
                    preferences.getString("label","")?:"")
                .putExtra(RecordingService.CONFIG,
                    preferences.getString("configuration","Stock")?:"Stock")
                .putExtra(RecordingService.INTERVAL,
                    preferences.getLong("interval_ms",5000L)))
        }.onFailure{
            Toast.makeText(this,"Cannot launch HUD: "+
                it.javaClass.simpleName,Toast.LENGTH_LONG).show()
        }
        refresh()
    }
    private fun refresh(){
        val current=state()
        qsTile?.apply {
            label=when {
                current.recording && current.hudVisible->"CM HUD · REC"
                current.recording->"REC · HUD OFF"
                current.standby->"CM HUD · ON"
                else->"CM HUD · OFF"
            }
            icon=android.graphics.drawable.Icon.createWithResource(
                this@HudTile,R.drawable.ic_coremetrics_hud_tile)
            this.state=if(current.hudVisible||current.standby)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
