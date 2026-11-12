package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.os.SystemClock

/**
 * Best-effort continuation ONLY of user-started, ongoing standby/REC after
 * Android restarts a previously running START_STICKY foreground service.
 *
 * This does NOT keep a low-memory-killed process alive, bypass the LMK, launch
 * after reboot, or self-start when the user has explicitly stopped.
 */
internal object RecorderLease {
    private const val FILE="coremetrics_started_service_lease"
    private const val MAX_REC_AGE_MS=90_000L
    private const val MAX_STANDBY_AGE_MS=10*60_000L
    data class Saved(val recording:Boolean,val id:Long,val label:String,
        val configuration:String,val intervalMs:Long)

    private fun prefs(context:Context)=
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
    private fun save(context:Context,recording:Boolean,id:Long,label:String,
        config:String,interval:Long):Boolean {
        val p=prefs(context)
        return p.edit()
            .putBoolean("active",true).putBoolean("recording",recording)
            .putLong("id",id).putString("label",label.take(64))
            .putString("config",config.take(40))
            .putLong("interval",if(interval==2000L)2000L else 5000L)
            .putLong("heartbeat_uptime",SystemClock.elapsedRealtime())
            .putLong("heartbeat_wall",System.currentTimeMillis())
            .commit()
    }
    fun standby(context:Context,label:String,config:String,intervalMs:Long)=
        save(context,false,0L,label,config,intervalMs)
    fun start(context:Context,id:Long,label:String,config:String,intervalMs:Long)=
        if(id>0L) save(context,true,id,label,config,intervalMs) else false
    fun heartbeat(context:Context) {
        val p=prefs(context)
        if(p.getBoolean("active",false) && p.getBoolean("recording",false)) {
            p.edit().putLong("heartbeat_uptime",SystemClock.elapsedRealtime())
                .putLong("heartbeat_wall",System.currentTimeMillis()).commit()
        }
    }
    fun clear(context:Context):Boolean = prefs(context).edit()
        .putBoolean("active",false).putBoolean("recording",false)
        .remove("id").commit()

    /**
     * A process killed during an explicit recording has a fresh lease;
     * a boot or time discontinuity cannot resume an old session.
     * Reading this lease creates no process, service, or polling task.
     */
    fun recover(context:Context):Saved? {
        val p=prefs(context)
        if(!p.getBoolean("active",false))return null
        val recording=p.getBoolean("recording",false)
        val savedUptime=p.getLong("heartbeat_uptime",-1L)
        val savedWall=p.getLong("heartbeat_wall",-1L)
        val deltaU=SystemClock.elapsedRealtime()-savedUptime
        val deltaWall=System.currentTimeMillis()-savedWall
        val max=if(recording)MAX_REC_AGE_MS else MAX_STANDBY_AGE_MS
        if(savedUptime<=0L || savedWall<=0L ||
            deltaU<0L || deltaU>max || deltaWall<0L ||
            deltaWall>max+10_000L)return null
        val id=p.getLong("id",0L)
        if(recording && id<=0L)return null
        return Saved(recording,id,
            p.getString("label","Gameplay")?:"Gameplay",
            p.getString("config","Unknown")?:"Unknown",
            p.getLong("interval",5000L))
    }
}
