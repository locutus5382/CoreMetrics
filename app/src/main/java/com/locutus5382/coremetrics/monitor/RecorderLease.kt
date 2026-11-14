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
    // Terminal-only reconciliation may be requested long after STOP. This
    // window never authorizes sampling or resurrects an abandoned recording.
    private const val MAX_STOP_AGE_MS=24*60*60_000L
    data class Saved(val recording:Boolean,val id:Long,val label:String,
        val configuration:String,val intervalMs:Long,
        val hudEnabled:Boolean,val hudLocked:Boolean,
        val stopRequested:Boolean,val standbyRecoveryEnabled:Boolean)

    private fun prefs(context:Context)=
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
    private fun save(context:Context,recording:Boolean,id:Long,label:String,
        config:String,interval:Long,hud:Boolean,restored:Boolean,
        standbyRecovery:Boolean=true):Boolean {
        val p=prefs(context)
        return p.edit()
            .putBoolean("active",true).putBoolean("recording",recording)
            .putBoolean("stop_requested",false)
            .putBoolean("standby_recovery",if(recording)false else standbyRecovery)
            .putLong("id",id).putString("label",label.take(64))
            .putString("config",config.take(40))
            .putLong("interval",if(interval==2000L)2000L else 5000L)
            .putBoolean("hud_enabled",hud)
            .putBoolean("hud_locked",if(restored)
                p.getBoolean("hud_locked",false) else false)
            .putInt("recoveries",if(restored)
                p.getInt("recoveries",0) else 0)
            .putLong("heartbeat_uptime",SystemClock.elapsedRealtime())
            .putLong("heartbeat_wall",System.currentTimeMillis())
            .commit()
    }
    fun standby(context:Context,label:String,config:String,intervalMs:Long,
        restored:Boolean=false,recoverable:Boolean=true)=
        save(context,false,0L,label,config,intervalMs,true,restored,recoverable)
    fun start(context:Context,id:Long,label:String,config:String,
        intervalMs:Long,hud:Boolean,restored:Boolean=false)=
        if(id>0L) save(context,true,id,label,config,intervalMs,hud,restored) else false
    fun markRecovery(context:Context):Boolean {
        val p=prefs(context)
        // Pending user STOP is terminal reconciliation, not another attempt
        // to resume the background sampler; allow it even after two restarts.
        if(p.getBoolean("stop_requested",false))return true
        val count=p.getInt("recoveries",0)
        if(count>=2)return false
        return p.edit().putInt("recoveries",count+1).commit()
    }
    /** Synchronously preserve user's STOP/DISARM intent before task.cancel().
     * If LMK occurs during final SQLite commit, recovery may only finish the
     * original session, never restart its sensor collector. */
    fun requestStop(context:Context):Boolean {
        val p=prefs(context)
        if(!p.getBoolean("active",false))return false
        return p.edit().putBoolean("stop_requested",true).commit()
    }
    /** Persist user-requested HUD visibility independently of recording.
     * A low-memory restarted REC must not unexpectedly re-show a hidden HUD. */
    fun setHudEnabled(context:Context,visible:Boolean):Boolean {
        val p=prefs(context)
        if(!p.getBoolean("active",false) ||
            !p.getBoolean("recording",false))return false
        return p.edit().putBoolean("hud_enabled",visible).commit()
    }
    /** The unlock handle must survive a best-effort sticky service restart.
     * Lock state is user-chosen and restored only for a fresh valid lease. */
    fun setHudLocked(context:Context,locked:Boolean):Boolean {
        val p=prefs(context)
        if(!p.getBoolean("active",false))return false
        return p.edit().putBoolean("hud_locked",locked).commit()
    }
    fun heartbeat(context:Context) {
        val p=prefs(context)
        if(p.getBoolean("active",false) && p.getBoolean("recording",false)) {
            p.edit().putLong("heartbeat_uptime",SystemClock.elapsedRealtime())
                .putLong("heartbeat_wall",System.currentTimeMillis()).commit()
        }
    }
    fun clear(context:Context):Boolean = prefs(context).edit()
        .putBoolean("active",false).putBoolean("recording",false)
        .remove("id").remove("recoveries").remove("hud_locked")
        .remove("stop_requested").remove("standby_recovery").commit()

    /**
     * A process killed during an explicit recording has a fresh lease;
     * a boot or time discontinuity cannot resume an old session.
     * Reading this lease creates no process, service, or polling task.
     */
    fun recover(context:Context):Saved? {
        val p=prefs(context)
        if(!p.getBoolean("active",false))return null
        val stopRequested=p.getBoolean("stop_requested",false)
        if(!stopRequested && p.getInt("recoveries",0)>=2)return null
        val recording=p.getBoolean("recording",false)
        val standbyRecovery=p.getBoolean("standby_recovery",true)
        if(!RecorderContinuation.allowLeaseRecovery(
                recording,stopRequested,standbyRecovery))return null
        val savedUptime=p.getLong("heartbeat_uptime",-1L)
        val savedWall=p.getLong("heartbeat_wall",-1L)
        val deltaU=SystemClock.elapsedRealtime()-savedUptime
        val deltaWall=System.currentTimeMillis()-savedWall
        val max=when {
            stopRequested->MAX_STOP_AGE_MS
            recording->MAX_REC_AGE_MS
            else->MAX_STANDBY_AGE_MS
        }
        if(savedUptime<=0L || savedWall<=0L ||
            deltaU<0L || deltaU>max || deltaWall<0L ||
            deltaWall>max+10_000L)return null
        val id=p.getLong("id",0L)
        if(recording && id<=0L)return null
        return Saved(recording,id,
            p.getString("label","Gameplay")?:"Gameplay",
            p.getString("config","Unknown")?:"Unknown",
            p.getLong("interval",5000L),
            p.getBoolean("hud_enabled",false),
            p.getBoolean("hud_locked",false),stopRequested,standbyRecovery)
    }
}
