package com.locutus5382.coremetrics.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import com.locutus5382.coremetrics.ui.MainActivity

/** Missing permissions never start an unready recorder. Offer the same
 * rationale screen as first launch, through Android's official tile API.
 */
internal object QuickTileSetup {
    fun open(service:TileService) {
        val i=Intent(service,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("show_setup",true)
        runCatching {
            if(Build.VERSION.SDK_INT>=34) {
                val pending=PendingIntent.getActivity(service,802,i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                service.startActivityAndCollapse(pending)
            }else{
                @Suppress("DEPRECATION")
                service.startActivityAndCollapse(i)
            }
        }.onFailure {
            Toast.makeText(service,
                "Open CoreMetrics → Live to grant the required permission",
                Toast.LENGTH_LONG).show()
        }
    }
}
