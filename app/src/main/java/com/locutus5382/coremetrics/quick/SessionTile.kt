package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.monitor.RecordingService

/** Distinct QS function: user-started REC, STOP or validated recovery. */
class SessionTile:TileService(){
    private fun status():QuickTileStatePolicy.Mode=try{
        val b=contentResolver.call(
            Uri.parse("content://"+packageName+".monitor.status"),
            "status",null,null)
        QuickTileStatePolicy.mode(
            reachable=b!=null,
            serviceAlive=b?.getBoolean("serviceAlive",false)==true,
            pendingRecovery=b?.getBoolean("pendingRecovery",false)==true,
            running=b?.getBoolean("running",false)==true,
            starting=b?.getBoolean("starting",false)==true,
            standby=b?.getBoolean("standby",false)==true)
    }catch(_:Exception){QuickTileStatePolicy.Mode.UNKNOWN}

    override fun onStartListening(){super.onStartListening();refresh()}
    override fun onClick(){
        super.onClick()
        if(isLocked)unlockAndRun{toggle()}else toggle()
    }
    private fun toggle(){
        val action=QuickTileStatePolicy.recorder(status())
        if(action==QuickTileStatePolicy.RecordAction.WAIT){
            Toast.makeText(this,"Recorder is starting or status unavailable; no new session",
                Toast.LENGTH_LONG).show()
            refresh();return
        }
        if(action!=QuickTileStatePolicy.RecordAction.STOP &&
            Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"Open CoreMetrics to allow notifications",
                Toast.LENGTH_LONG).show()
            return
        }
        runCatching{
            when(action){
                QuickTileStatePolicy.RecordAction.STOP ->
                    startService(Intent(this,RecordingService::class.java)
                        .setAction(RecordingService.STOP))
                QuickTileStatePolicy.RecordAction.RECOVER ->
                    startForegroundService(Intent(this,RecordingService::class.java)
                        .setAction(RecordingService.RECOVER))
                QuickTileStatePolicy.RecordAction.START->{
                    val prefs=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                    startForegroundService(Intent(this,RecordingService::class.java)
                        .setAction(RecordingService.START)
                        .putExtra(RecordingService.INTERVAL,
                            prefs.getLong("interval_ms",5000L))
                        .putExtra(RecordingService.LABEL,
                            prefs.getString("label","Gameplay")?:"Gameplay")
                        .putExtra(RecordingService.CONFIG,
                            prefs.getString("configuration","Stock")?:"Stock"))
                }
                else->Unit
            }
        }.onFailure{
            Toast.makeText(this,"Recorder request failed: "+it.javaClass.simpleName,
                Toast.LENGTH_LONG).show()
        }
        refresh()
    }
    private fun refresh(){
        val mode=status()
        qsTile?.apply{
            label=when(mode){
                QuickTileStatePolicy.Mode.RECORDING->"● CM93 REC · STOP"
                QuickTileStatePolicy.Mode.STARTING->"CM93 REC · Starting"
                QuickTileStatePolicy.Mode.RECOVERABLE->"CM93 REC · Recover"
                QuickTileStatePolicy.Mode.STANDBY->"CM93 REC · Ready"
                QuickTileStatePolicy.Mode.UNKNOWN->"CM93 REC · Unknown"
                else->"CM93 REC · Start"
            }
            this.state=if(mode==QuickTileStatePolicy.Mode.RECORDING ||
                mode==QuickTileStatePolicy.Mode.STARTING)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
