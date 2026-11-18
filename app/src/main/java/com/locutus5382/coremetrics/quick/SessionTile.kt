package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.locutus5382.coremetrics.monitor.RecordingService
import com.locutus5382.coremetrics.R
import android.graphics.drawable.Icon

/** Distinct QS function: user-started REC, STOP or validated recovery. */
class SessionTile:TileService(){
    // One deferred status refresh per tile instance; never retain a stale
    // TileService after Android stops listening or destroys it.
    private val refreshHandler=Handler(Looper.getMainLooper())
    private val refreshOnce=Runnable {refresh()}
    override fun onStopListening() {
        refreshHandler.removeCallbacks(refreshOnce)
        super.onStopListening()
    }
    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshOnce)
        super.onDestroy()
    }
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
            Toast.makeText(this,
                if(status()==QuickTileStatePolicy.Mode.REPAIR_REQUIRED)
                    "Recovery lease needs review; no new recording was started"
                else "Recorder is starting or status unavailable; no new session",
                Toast.LENGTH_LONG).show()
            refresh();return
        }
        if(action!=QuickTileStatePolicy.RecordAction.STOP &&
            Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"Opening CoreMetrics setup for notifications",
                Toast.LENGTH_SHORT).show()
            QuickTileSetup.open(this)
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
                    // No main-thread SQLite helper/open; the service uses the
                    // authoritative snapshot after its foreground notification.
                    startForegroundService(Intent(this,RecordingService::class.java)
                        .setAction(RecordingService.START))
                }
                else->Unit
            }
        }.onFailure{
            Toast.makeText(this,"Recorder request failed: "+it.javaClass.simpleName,
                Toast.LENGTH_LONG).show()
        }
        refresh()
        refreshHandler.removeCallbacks(refreshOnce)
        refreshHandler.postDelayed(refreshOnce,450L)
    }
    private fun refresh(){
        val mode=status()
        val secondary=when(mode){
            QuickTileStatePolicy.Mode.RECORDING->"REC · Tap Stop"
            QuickTileStatePolicy.Mode.STARTING->"Starting…"
            QuickTileStatePolicy.Mode.SAVING->"Saving…"
            QuickTileStatePolicy.Mode.STOP_RETRY->"Retry STOP"
            QuickTileStatePolicy.Mode.SAVE_RETRY->"Retry Save"
            QuickTileStatePolicy.Mode.REPAIR_REQUIRED->"Repair required"
            QuickTileStatePolicy.Mode.STOP_PENDING->"Finish Stop"
            QuickTileStatePolicy.Mode.RECOVERABLE->"Recover"
            QuickTileStatePolicy.Mode.STANDBY->"Ready"
            QuickTileStatePolicy.Mode.UNKNOWN->"Unknown"
            QuickTileStatePolicy.Mode.IDLE->"Tap to Rec"
        }
        qsTile?.apply{
            label="CM REC"
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
