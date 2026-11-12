package com.locutus5382.coremetrics.quick

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.monitor.RecordingService

/** One role: toggle session recording. No profile or frequency controls. */
class SessionTile : TileService() {
    private data class RecState(val running:Boolean,val starting:Boolean,val standby:Boolean)
    private fun status(): RecState = try {
        val b = contentResolver.call(
            Uri.parse("content://" + packageName + ".monitor.status"),
            "status", null, null)
        RecState(b?.getBoolean("running", false) == true,
            b?.getBoolean("starting", false) == true,
            b?.getBoolean("standby",false)==true)
    } catch (_: Exception) { RecState(false,false,false) }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val state = status()
        if (!state.running && !state.starting && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Open CoreMetrics to allow notifications", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (state.running || state.starting) {
                startService(Intent(this, RecordingService::class.java)
                    .setAction(RecordingService.STOP))
            } else {
                val prefs = getSharedPreferences("coremetrics_settings", MODE_PRIVATE)
                val interval = prefs.getLong("interval_ms", 5000L)
                val label = prefs.getString("label", "Gameplay") ?: "Gameplay"
                val config = prefs.getString("configuration", "Stock") ?: "Stock"
                startForegroundService(Intent(this, RecordingService::class.java)
                    .setAction(RecordingService.START)
                    .putExtra(RecordingService.INTERVAL, interval)
                    .putExtra(RecordingService.LABEL, label)
                    .putExtra(RecordingService.CONFIG, config))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Recording request failed: " + e.javaClass.simpleName,
                Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun refresh() {
        val state = status()
        qsTile?.apply {
            label = when {
                state.running -> "● CM REC · STOP"
                state.starting -> "Starting…"
                state.standby -> "CM REC · Ready"
                else -> "CM REC · Start"
            }
            this.state = if (state.running || state.starting)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
