package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.R
import android.graphics.drawable.Icon

/** Distinct QS function: user-started REC, STOP or validated recovery. */
class SessionTile:TileService(){
    private fun status():QuickTileStatePolicy.Mode=try {
        QuickTileSnapshot.read(this).mode
    } catch(_:Exception) { QuickTileStatePolicy.Mode.UNKNOWN }

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
        val secondary=when(mode){
            QuickTileStatePolicy.Mode.RECORDING->"Recording · Tap STOP"
            QuickTileStatePolicy.Mode.STARTING->"Starting…"
            QuickTileStatePolicy.Mode.RECOVERABLE->"Recover session"
            QuickTileStatePolicy.Mode.STANDBY->"Ready · Tap REC"
            QuickTileStatePolicy.Mode.UNKNOWN->"Status unknown · Recover"
            QuickTileStatePolicy.Mode.IDLE->"Tap to record"
        }
        qsTile?.apply{
            label="CoreMetrics REC"
            subtitle=secondary
            contentDescription="CoreMetrics recorder, "+secondary
            stateDescription=secondary
            icon=Icon.createWithResource(this@SessionTile,
                if(mode==QuickTileStatePolicy.Mode.RECORDING ||
                    mode==QuickTileStatePolicy.Mode.STARTING)
                    R.drawable.ic_qs_cm_rec_on else R.drawable.ic_qs_cm_rec_off)
            state=if(mode==QuickTileStatePolicy.Mode.RECORDING ||
                mode==QuickTileStatePolicy.Mode.STARTING)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
