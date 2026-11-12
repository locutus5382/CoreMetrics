package com.locutus5382.coremetrics.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.service.quicksettings.TileService
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.quick.SessionTile
import com.locutus5382.coremetrics.telemetry.DirectSampler
import com.locutus5382.coremetrics.telemetry.ForegroundAttribution
import com.locutus5382.coremetrics.telemetry.FrameEstimate
import com.locutus5382.coremetrics.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User-started :monitor foreground service. ARMED = one visible overlay and
 * notification but NO telemetry, polling task or SQLite writes. REC = existing
 * session collector. HUD click switches modes without an extra service.
 */
class RecordingService:Service() {
    companion object {
        const val START="com.locutus5382.coremetrics.START"
        const val STOP="com.locutus5382.coremetrics.STOP"
        const val ARM="com.locutus5382.coremetrics.ARM_HUD"
        const val DISARM="com.locutus5382.coremetrics.DISARM_HUD"
        const val HUD_CONFIG="com.locutus5382.coremetrics.HUD_CONFIG"
        const val HUD_LOCK="com.locutus5382.coremetrics.HUD_LOCK"
        const val HUD_UNLOCK="com.locutus5382.coremetrics.HUD_UNLOCK"
        const val LABEL="label"
        const val CONFIG="configuration"
        const val INTERVAL="interval"
        private const val CHANNEL="coremetrics_monitor"
        private const val NOTIFICATION_ID=4411
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private lateinit var db:SessionStore
    private lateinit var hud:RecorderHud
    private var task:Job?=null
    private var stopping=false
    private var armed=false
    private var disarmAfterStop=false
    private var settings=HudOptions()
    private var armedLabel="Gameplay"
    private var armedConfig="Stock"
    private var armedInterval=5000L

    override fun onCreate() {
        super.onCreate()
        db=SessionStore(applicationContext)
        hud=RecorderHud(applicationContext)
        settings=HudOptions.from(this)
        RecorderJournal.event(this,"SERVICE_CREATED","pid="+android.os.Process.myPid())
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"CoreMetrics recording / HUD standby",
                NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent:Intent?):IBinder?=null
    private fun notificationsAllowed():Boolean = Build.VERSION.SDK_INT<33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==
            PackageManager.PERMISSION_GRANTED
    private fun foreground(title:String,detail:String,isStandby:Boolean):Boolean = try {
        val n=notification(title,detail,isStandby)
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION_ID,n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID,n)
        true
    }catch(e:Exception) {
        RecorderJournal.event(this,"FGS_START_FAILED",e.javaClass.simpleName)
        false
    }
    private fun refreshTile()=runCatching{
        TileService.requestListeningState(this,ComponentName(this,SessionTile::class.java))
    }.getOrNull()
    private fun stopServiceNow(){
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun disarmNow(){
        armed=false
        MonitorState.standby=false
        MonitorState.hudState=hud.hide()
        MonitorState.hudLocked=false
        MonitorState.clear("Standby closed")
        refreshTile()
        stopServiceNow()
    }
    private fun standby(){
        MonitorState.clear("HUD ready · tap REC in game")
        MonitorState.standby=true
        hud.mode(false)
        foreground("CoreMetrics · standby","No sensors sampled until REC",true)
        refreshTile()
    }
    private fun hudClick(){
        val i=Intent(this,RecordingService::class.java)
        if(MonitorState.running || MonitorState.starting) i.action=STOP
        else {
            i.action=START
            i.putExtra(LABEL,armedLabel)
            i.putExtra(CONFIG,armedConfig)
            i.putExtra(INTERVAL,armedInterval)
        }
        startService(i)
    }
    private fun attachHud():String {
        val result=hud.show(settings,
            onRecordClick={runCatching{hudClick()}.onFailure{
                RecorderJournal.event(this,"HUD_CLICK_ERROR",it.javaClass.simpleName)
            }},
            onLockClick={startService(Intent(this,RecordingService::class.java)
                .setAction(HUD_LOCK))},
            onDismissClick={startService(Intent(this,RecordingService::class.java)
                .setAction(DISARM))},
            onOptionsChanged={ settings=it })
        MonitorState.hudState=result
        MonitorState.hudLocked=hud.isLocked()
        RecorderJournal.event(this,"HUD_ATTACH",result)
        return result
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        RecorderJournal.event(this,"SERVICE_COMMAND",intent?.action?:"null")
        when(intent?.action){
            ARM -> {
                if(!notificationsAllowed() || !hud.permissionGranted()){
                    MonitorState.message=if(!hud.permissionGranted())
                        "Allow Display over other apps for standby HUD"
                    else "Allow notifications for standby"
                    RecorderJournal.event(this,"ARM_DENIED",MonitorState.message)
                    if(!armed && task?.isActive!=true)stopSelf(startId)
                    return START_NOT_STICKY
                }
                settings=HudOptions.from(this,intent)
                val prefs=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                armedLabel=(intent.getStringExtra(LABEL)
                    ?:prefs.getString("label","Gameplay")?:"Gameplay").take(64)
                armedConfig=(intent.getStringExtra(CONFIG)
                    ?:prefs.getString("configuration","Stock")?:"Stock").take(40)
                armedInterval=if(intent.getLongExtra(INTERVAL,
                    prefs.getLong("interval_ms",5000L))==2000L)2000L else 5000L
                if(task?.isActive!=true && !foreground("CoreMetrics · HUD standby",
                        "No telemetry until REC",true)){
                    MonitorState.message="Foreground standby failed"
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val status=attachHud()
                if(status.startsWith("HUD_ADD_FAILED") || status=="HUD_PERMISSION_MISSING"){
                    if(task?.isActive!=true)stopServiceNow()
                    return START_NOT_STICKY
                }
                armed=true
                if(task?.isActive!=true && !MonitorState.starting)standby()
                else hud.mode(true,MonitorState.startedAt)
                RecorderJournal.event(this,"ARMED_NO_SAMPLING")
                refreshTile()
                return START_NOT_STICKY
            }
            HUD_LOCK, HUD_UNLOCK -> {
                if(!armed && task?.isActive!=true) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val result=hud.setLocked(intent.action==HUD_LOCK)
                MonitorState.hudLocked=hud.isLocked()
                MonitorState.hudState=result
                RecorderJournal.event(this,"HUD_TOUCH_LOCK",result)
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,notification(
                        if(MonitorState.running) "● REC · CoreMetrics"
                            else "CoreMetrics · standby",
                        if(MonitorState.hudLocked) "HUD locked · use notification to unlock"
                            else "HUD unlocked",!MonitorState.running))
                return START_NOT_STICKY
            }
            HUD_CONFIG -> {
                // A stale UI status must not create an idle :monitor service.
                if(!armed && task?.isActive!=true) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                settings=HudOptions.from(this,intent)
                if(hud.attached()){
                    hud.configure(settings)
                    MonitorState.hudState="HUD_CONFIGURED"
                }
                return START_NOT_STICKY
            }
            DISARM -> {
                RecorderJournal.event(this,"DISARM_REQUESTED")
                armed=false
                disarmAfterStop=true
                MonitorState.standby=false
                val current=task
                if(current?.isActive==true || MonitorState.starting){
                    stopping=true
                    current?.cancel()
                } else disarmNow()
                return START_NOT_STICKY
            }
            STOP -> {
                RecorderJournal.event(this,"STOP_REQUESTED","id="+MonitorState.sessionId)
                stopping=true
                MonitorState.message="Stopping and saving…"
                val current=task
                if(current==null || current.isCompleted){
                    stopping=false
                    if(armed)standby()else disarmNow()
                }else current.cancel()
                refreshTile()
                return START_NOT_STICKY
            }
            START -> {
                if(task?.isActive==true || MonitorState.starting || stopping){
                    RecorderJournal.event(this,"START_IGNORED","already active")
                    return START_NOT_STICKY
                }
                if(!notificationsAllowed()){
                    MonitorState.message="Notifications permission required"
                    RecorderJournal.event(this,"START_DENIED","notification permission")
                    if(!armed)stopSelf(startId)
                    return START_NOT_STICKY
                }
                val p=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                val request=intent.getLongExtra(INTERVAL,p.getLong("interval_ms",5000L))
                val interval=if(request==2000L)2000L else 5000L
                val label=(intent.getStringExtra(LABEL)
                    ?:p.getString("label","Gameplay")?:"Gameplay").take(64)
                val config=(intent.getStringExtra(CONFIG)
                    ?:p.getString("configuration","Stock")?:"Stock").take(40)
                armedLabel=label
                armedConfig=config
                armedInterval=interval
                RecorderJournal.event(this,"START_REQUESTED","every="+interval)
                if(!foreground("CoreMetrics · starting",label,false)){
                    MonitorState.message="Foreground recorder unavailable"
                    if(!armed)stopSelf(startId)
                    return START_NOT_STICKY
                }
                MonitorState.starting=true
                MonitorState.standby=false
                val wantsHud=armed || p.getBoolean("hud_enabled",false)
                if(wantsHud && hud.permissionGranted()){
                    settings=HudOptions.from(this,intent)
                    val result=attachHud()
                    if(result=="HUD_ATTACHED" || result=="HUD_ALREADY_ATTACHED") {
                        armed=true
                        hud.mode(true,System.currentTimeMillis())
                    }
                }
                refreshTile()
                task=scope.launch {
                    var id=0L
                    var outcome="INTERRUPTED"
                    try{
                        id=withContext(Dispatchers.IO){
                            val recovered=db.recoverInterruptedSessions(System.currentTimeMillis())
                            if(recovered>0)RecorderJournal.event(this@RecordingService,
                                "RECOVERED_INTERRUPTED",recovered.toString())
                            db.begin(label,config,System.currentTimeMillis())
                        }
                        MonitorState.sessionId=id
                        MonitorState.startedAt=System.currentTimeMillis()
                        MonitorState.sampleCount=0L
                        MonitorState.starting=false
                        MonitorState.running=true
                        MonitorState.message=label+" · "+config
                        RecorderJournal.event(this@RecordingService,"SESSION_PERSISTED","id="+id)
                        withContext(Dispatchers.Main.immediate) {
                            if(armed)hud.mode(true,MonitorState.startedAt)
                            refreshTile()
                        }
                        val sampler=DirectSampler(application)
                        val detector=ForegroundAttribution(applicationContext)
                        val fps=FrameEstimate(null,null,0,
                            "SURFACEFLINGER_PERMISSION_DENIED_ON_PORTAL")
                        var next=SystemClock.elapsedRealtime()
                        var lastNotice=0L
                        while(isActive && !stopping){
                            val s=withContext(Dispatchers.IO){
                                sampler.sample(detector.current(System.currentTimeMillis()),fps)
                            }
                            withContext(Dispatchers.IO){db.add(id,s)}
                            MonitorState.latest=s
                            MonitorState.sampleCount++
                            if(armed && hud.attached())withContext(Dispatchers.Main.immediate){
                                runCatching{hud.update(s,MonitorState.sampleCount)}
                                    .onFailure{RecorderJournal.event(this@RecordingService,
                                        "HUD_UPDATE_ERROR",it.javaClass.simpleName)}
                            }
                            if(MonitorState.sampleCount==1L)RecorderJournal.event(
                                this@RecordingService,"FIRST_SAMPLE_WRITTEN","id="+id)
                            val elapsed=SystemClock.elapsedRealtime()
                            if(elapsed-lastNotice>12_000L){
                                lastNotice=elapsed
                                getSystemService(NotificationManager::class.java).notify(
                                    NOTIFICATION_ID,
                                    notification("● REC · "+MonitorState.sampleCount+
                                        " samples",label+" · "+config,false))
                            }
                            next+=interval
                            val delayMs=next-SystemClock.elapsedRealtime()
                            if(delayMs>0L)delay(delayMs)else next=SystemClock.elapsedRealtime()
                        }
                        outcome="COMPLETED"
                    }catch(_:CancellationException){
                        outcome=if(stopping) "COMPLETED" else "INTERRUPTED"
                    }catch(e:Exception){
                        outcome="FAILED"
                        RecorderJournal.event(this@RecordingService,"SAMPLING_FAILED",
                            e.javaClass.simpleName+":"+(e.message?:""))
                    }finally{
                        withContext(NonCancellable){
                            withContext(Dispatchers.IO){
                                if(id!=0L)runCatching{
                                    db.finish(id,System.currentTimeMillis(),outcome)
                                }
                            }
                            RecorderJournal.event(this@RecordingService,
                                "SESSION_FINALIZED","id="+id+" status="+outcome+
                                    " samples="+MonitorState.sampleCount)
                            MonitorState.clear(if(outcome=="COMPLETED")
                                "Recording saved" else "Recording "+outcome.lowercase())
                            withContext(Dispatchers.Main.immediate){
                                task=null
                                stopping=false
                                if(armed && !disarmAfterStop){
                                    standby()
                                }else{
                                    disarmAfterStop=false
                                    disarmNow()
                                }
                            }
                        }
                    }
                }
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }
    private fun notification(title:String,detail:String,standby:Boolean):Notification{
        val open=PendingIntent.getActivity(this,200,
            Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val exit=PendingIntent.getService(this,201,
            Intent(this,RecordingService::class.java)
                .setAction(if(standby)DISARM else STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this,CHANNEL)
            .setSmallIcon(com.locutus5382.coremetrics.R.drawable.ic_coremetrics_notification)
            .setContentTitle(title).setContentText(detail)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                if(standby) "Close standby" else "Stop and save",exit).build())
        if(hud.isLocked()) {
            val unlock=PendingIntent.getService(this,202,
                Intent(this,RecordingService::class.java).setAction(HUD_UNLOCK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_manage,"Unlock HUD",unlock).build())
        }
        return builder.build()
    }
    override fun onDestroy(){
        RecorderJournal.event(this,"SERVICE_DESTROYED","armed="+armed+
            " active="+(task?.isActive==true))
        stopping=true
        runCatching{hud.hide()}
        task?.cancel()
        scope.cancel()
        val active=task
        if(active!=null)active.invokeOnCompletion{db.close()}else db.close()
        super.onDestroy()
    }
}
