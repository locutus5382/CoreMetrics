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
import com.locutus5382.coremetrics.quick.HudTile
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
        const val RECOVER="com.locutus5382.coremetrics.RECOVER_LEASE"
        const val STOP="com.locutus5382.coremetrics.STOP"
        const val ARM="com.locutus5382.coremetrics.ARM_HUD"
        const val DISARM="com.locutus5382.coremetrics.DISARM_HUD"
        const val HUD_CONFIG="com.locutus5382.coremetrics.HUD_CONFIG"
        const val HUD_LOCK="com.locutus5382.coremetrics.HUD_LOCK"
        const val HUD_UNLOCK="com.locutus5382.coremetrics.HUD_UNLOCK"
        const val HUD_SHOW="com.locutus5382.coremetrics.HUD_SHOW"
        const val HUD_HIDE="com.locutus5382.coremetrics.HUD_HIDE"
        const val APP_FOREGROUND="com.locutus5382.coremetrics.APP_FOREGROUND"
        const val APP_BACKGROUND="com.locutus5382.coremetrics.APP_BACKGROUND"
        const val LABEL="label"
        const val CONFIG="configuration"
        const val INTERVAL="interval"
        private const val RECOVERY_ID="recovery_id"
        private const val RECOVERY_HUD="recovery_hud"
        private const val RECOVERY_LOCK="recovery_lock"
        private const val RESTORED="restarted_by_android"
        private const val CHANNEL="coremetrics_monitor"
        private const val NOTIFICATION_ID=4411
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private lateinit var db:SessionStore
    private lateinit var hud:RecorderHud
    private var task:Job?=null
    private var stopping=false
    private var explicitUserStop=false
    private var destroying=false
    private var armed=false
    private var disarmAfterStop=false
    private var appInForeground=false
    private var userWantsHud=true
    private var settings=HudOptions()
    private var armedLabel="Gameplay"
    private var armedConfig="Stock"
    private var armedInterval=5000L

    override fun onCreate() {
        super.onCreate()
        db=SessionStore(applicationContext)
        hud=MonitorHudOwner.get(applicationContext)
        settings=HudOptions.from(this)
        MonitorState.serviceCreated=true
        RecorderJournal.event(this,"SERVICE_CREATED",
            "pkg="+packageName+" pid="+android.os.Process.myPid()+
                " instance="+System.identityHashCode(this)+
                " "+hud.windowIdentity())
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
    /** The most recent onStartCommand result must remain STICKY while a user-
     * started recorder/standby is active, including after HUD_LOCK/CONFIG/HIDE.
     * This still cannot force OEMs or Android's low-memory manager to restart us.
     */
    private fun continuation(action:String?):Int {
        val active=armed || MonitorState.standby || MonitorState.running ||
            MonitorState.starting || task?.isActive==true
        return if(RecorderContinuation.sticky(action,active,stopping))
            START_STICKY else START_NOT_STICKY
    }
    /** Temporary in-app pause is independent of the user's HUD hide choice.
     * Never write HUD preference/lease merely because our Activity is visible.
     */
    private fun syncHudVisibility() {
        if(!hud.attached()) {
            MonitorState.hudVisible=false
            return
        }
        hud.setVisible(userWantsHud && !appInForeground)
        MonitorState.hudVisible=hud.visible()
        MonitorState.hudState=when {
            appInForeground && userWantsHud -> "HUD_PAUSED_IN_APP"
            MonitorState.hudVisible -> "HUD_VISIBLE"
            else -> "HUD_HIDDEN"
        }
    }
    private fun refreshTile() {
        // Keep BOTH independent Quick Settings tiles accurate when the HUD
        // changes from the app, a notification, or another tile.
        runCatching {
            TileService.requestListeningState(this,
                ComponentName(this,SessionTile::class.java))
        }
        runCatching {
            TileService.requestListeningState(this,
                ComponentName(this,HudTile::class.java))
        }
    }
    private fun stopServiceNow(){
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun disarmNow(){
        armed=false
        MonitorState.standby=false
        val removal=hud.hide()
        RecorderJournal.event(this,"HUD_REMOVE",removal+" "+hud.windowIdentity())
        MonitorState.hudState=removal
        MonitorState.hudVisible=hud.visible()
        MonitorState.hudLocked=hud.isLocked()
        MonitorState.clear("Standby closed")
        RecorderLease.clear(this)
        if(removal.startsWith("HUD_REMOVE_FAILED")){
            // Keep the foreground service user-accessible for a later retry.
            // Do NOT abandon an interactive window with a stale service owner.
            armed=true
            MonitorState.standby=true
            MonitorState.message="HUD cleanup failed; tap Close again"
            foreground("CoreMetrics · overlay cleanup",
                "HUD could not be removed; tap Close or revoke overlay permission",true)
            refreshTile()
            return
        }
        refreshTile()
        stopServiceNow()
    }
    private fun standby(recovered:Boolean=false){
        MonitorState.clear("HUD ready · tap REC in game")
        MonitorState.standby=true
        RecorderLease.standby(this,armedLabel,armedConfig,armedInterval,recovered)
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
            onUnlockClick={startService(Intent(this,RecordingService::class.java)
                .setAction(HUD_UNLOCK))},
            onDismissClick={startService(Intent(this,RecordingService::class.java)
                .setAction(DISARM))},
            onOptionsChanged={ changed ->
                settings=changed
                MonitorState.hudOptions=changed
                MonitorState.hudOptionRevision++
            })
        MonitorState.hudState=result
        MonitorState.hudLocked=hud.isLocked()
        syncHudVisibility()
        RecorderJournal.event(this,"HUD_ATTACH",
            "pkg="+packageName+" "+result+" "+hud.windowIdentity())
        return result
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // START_STICKY is only a *request* to Android; the OEM can still kill
        // this process. A null restart intent requires a fresh, explicitly
        // user-created lease and (for REC) the SAME active SQLite session ID.
        // Quick Settings can explicitly request the SAME persisted session
        // after an LMK kill. It must never allocate a fresh session instead.
        if(intent?.action==RECOVER &&
            (task?.isActive==true || MonitorState.starting || armed)) {
            RecorderJournal.event(this,"RECOVERY_IGNORED","service already active")
            return continuation(RECOVER)
        }
        val command=if(intent!=null && intent.action!=RECOVER)intent else {
            val old=RecorderLease.recover(this)
            if(old==null || (old.recording && !db.canResume(old.id)) ||
                !RecorderLease.markRecovery(this)) {
                RecorderJournal.event(this,"RESTART_DECLINED","missing/stale lease")
                RecorderLease.clear(this)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            RecorderJournal.event(this,"RESTART_ACCEPTED",
                if(old.recording)"REC id="+old.id else "STANDBY")
            Intent(this,RecordingService::class.java)
                .setAction(if(old.recording)START else ARM)
                .putExtra(LABEL,old.label)
                .putExtra(CONFIG,old.configuration)
                .putExtra(INTERVAL,old.intervalMs)
                .putExtra(RECOVERY_HUD,old.hudEnabled)
                .putExtra(RECOVERY_LOCK,old.hudLocked)
                .putExtra(RECOVERY_ID,if(old.recording)old.id else 0L)
                .putExtra(RESTORED,true)
        }
        RecorderJournal.event(this,"SERVICE_COMMAND",command.action?:"null")
        when(command.action){
            ARM -> {
                // Idempotent while REC is active. A stale app or QS ARM must
                // never clear/replace a recording lease, even if HUD attach
                // fails. Route to HUD_SHOW, which only changes overlay state.
                if(task?.isActive==true || MonitorState.running ||
                    MonitorState.starting) {
                    RecorderJournal.event(this,"ARM_DURING_REC",
                        "redirected to HUD_SHOW")
                    return onStartCommand(
                        Intent(this,RecordingService::class.java)
                            .setAction(HUD_SHOW),flags,startId)
                }
                // A stale in-app "Launch standby" tap after LMK must not
                // overwrite an unfinished recording lease with a new standby
                // lease. Resume the same user-started session instead.
                if(task?.isActive!=true && !MonitorState.starting){
                    val previous=RecorderLease.recover(this)
                    if(previous?.recording==true && db.canResume(previous.id)){
                        RecorderJournal.event(this,"ARM_REDIRECTED_TO_RECOVERY",
                            "existing id="+previous.id)
                        return onStartCommand(
                            Intent(this,RecordingService::class.java)
                                .setAction(RECOVER),flags,startId)
                    }
                }
                if(!notificationsAllowed() || !hud.permissionGranted()){
                    MonitorState.message=if(!hud.permissionGranted())
                        "Allow Display over other apps for standby HUD"
                    else "Allow notifications for standby"
                    RecorderJournal.event(this,"ARM_DENIED",MonitorState.message)
                    if(!armed && task?.isActive!=true)stopSelf(startId)
                    return START_NOT_STICKY
                }
                settings=HudOptions.from(this,command)
                userWantsHud=true
                val prefs=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                armedLabel=(command.getStringExtra(LABEL)
                    ?:prefs.getString("label","Gameplay")?:"Gameplay").take(64)
                armedConfig=(command.getStringExtra(CONFIG)
                    ?:prefs.getString("configuration","Stock")?:"Stock").take(40)
                armedInterval=if(command.getLongExtra(INTERVAL,
                    prefs.getLong("interval_ms",5000L))==2000L)2000L else 5000L
                if(task?.isActive!=true && !foreground("CoreMetrics · HUD standby",
                        "No telemetry until REC",true)){
                    MonitorState.message="Foreground standby failed"
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val status=attachHud()
                if(command.getBooleanExtra(RESTORED,false) &&
                    command.getBooleanExtra(RECOVERY_LOCK,false) && hud.attached()){
                    val restoredLock=hud.setLocked(true)
                    MonitorState.hudLocked=hud.isLocked()
                    RecorderJournal.event(this,"HUD_LOCK_RECOVERY",restoredLock)
                }
                if(status.startsWith("HUD_ADD_FAILED") ||
                    status.startsWith("HUD_OWNERSHIP_UNRESOLVED") ||
                    status.startsWith("HUD_REBIND_FAILED") ||
                    status=="HUD_PERMISSION_MISSING"){
                    RecorderLease.clear(this)
                    if(task?.isActive!=true)stopServiceNow()
                    return START_NOT_STICKY
                }
                armed=true
                if(task?.isActive!=true && !MonitorState.starting)
                    standby(command.getBooleanExtra(RESTORED,false))
                else hud.mode(true,MonitorState.startedAt)
                RecorderJournal.event(this,"ARMED_NO_SAMPLING")
                refreshTile()
                return START_STICKY
            }
            APP_FOREGROUND, APP_BACKGROUND -> {
                // The Activity sends these ONLY while recording/standby is
                // already active; never spawn telemetry just to show the UI.
                if(!armed && !MonitorState.running && !MonitorState.starting &&
                    !MonitorState.standby) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                appInForeground=command.action==APP_FOREGROUND
                syncHudVisibility()
                RecorderJournal.event(this,"APP_VISIBILITY",
                    if(appInForeground)"HUD_PAUSED_FOR_APP"
                    else "HUD_RESTORED_IF_USER_ENABLED")
                refreshTile()
                return continuation(command.action)
            }
            HUD_SHOW, HUD_HIDE -> {
                // Do not start or stop telemetry when the HUD is hidden.
                // Hiding an idle standby fully releases the FGS to save RAM.
                if(command.action==HUD_HIDE && !MonitorState.running &&
                    !MonitorState.starting) {
                    disarmNow()
                    return START_NOT_STICKY
                }
                if(!armed && !MonitorState.running && !MonitorState.starting) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                userWantsHud=command.action==HUD_SHOW
                val result=if(userWantsHud) {
                    // Even an already-attached root might belong to a previous
                    // Service generation. Always rebind callbacks through the
                    // process-scoped owner; simply returning ATTACHED would
                    // leave old/unbound REC and close buttons unresponsive.
                    val attached=attachHud()
                    if(hud.attached()) {
                        armed=true
                        hud.mode(MonitorState.running,MonitorState.startedAt)
                        MonitorState.latest?.let{
                            if(MonitorState.running)
                                hud.update(it,MonitorState.sampleCount)
                        }
                    }
                    attached
                }else "HUD_REQUESTED_HIDDEN"
                syncHudVisibility()
                if(result.startsWith("HUD_ADD_FAILED") ||
                    result.startsWith("HUD_OWNERSHIP_UNRESOLVED") ||
                    result.startsWith("HUD_REBIND_FAILED") ||
                    result=="HUD_PERMISSION_MISSING") {
                    MonitorState.message="HUD cannot be shown: "+result
                }
                // Keep the USER preference even while CoreMetrics itself
                // temporarily pauses the overlay on its own activity.
                if(MonitorState.running)
                    RecorderLease.setHudEnabled(this,userWantsHud)
                RecorderJournal.event(this,"HUD_VISIBILITY",
                    MonitorState.hudState+" recording="+MonitorState.running)
                refreshTile()
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,notification(
                        if(MonitorState.running) "● REC · CoreMetrics" else
                            "CoreMetrics · standby",
                        if(MonitorState.hudVisible)"HUD visible" else
                            "HUD hidden · recording continues",
                        !MonitorState.running))
                return continuation(command.action)
            }
            HUD_LOCK, HUD_UNLOCK -> {
                if(!armed && task?.isActive!=true) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val result=hud.setLocked(command.action==HUD_LOCK)
                MonitorState.hudLocked=hud.isLocked()
                RecorderLease.setHudLocked(this,MonitorState.hudLocked)
                MonitorState.hudState=result
                RecorderJournal.event(this,"HUD_TOUCH_LOCK",result)
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,notification(
                        if(MonitorState.running) "● REC · CoreMetrics"
                            else "CoreMetrics · standby",
                        if(MonitorState.hudLocked) "HUD locked · use notification to unlock"
                            else "HUD unlocked",!MonitorState.running))
                return continuation(command.action)
            }
            HUD_CONFIG -> {
                // A stale UI status must not create an idle :monitor service.
                if(!armed && task?.isActive!=true) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                settings=HudOptions.from(this,command)
                if(hud.attached()){
                    hud.configure(settings)
                    MonitorState.hudState="HUD_CONFIGURED"
                    MonitorState.hudVisible=hud.visible()
                }
                return continuation(command.action)
            }
            DISARM -> {
                RecorderJournal.event(this,"DISARM_REQUESTED")
                RecorderLease.clear(this)
                armed=false
                disarmAfterStop=true
                MonitorState.standby=false
                val current=task
                if(current?.isActive==true || MonitorState.starting){
                    explicitUserStop=true
                    stopping=true
                    current?.cancel()
                } else disarmNow()
                return START_NOT_STICKY
            }
            STOP -> {
                RecorderJournal.event(this,"STOP_REQUESTED","id="+MonitorState.sessionId)
                RecorderLease.clear(this)
                explicitUserStop=true
                stopping=true
                MonitorState.message="Stopping and saving…"
                val current=task
                if(current==null || current.isCompleted){
                    stopping=false
                    if(armed && userWantsHud)standby()else disarmNow()
                }else current.cancel()
                refreshTile()
                return START_NOT_STICKY
            }
            START -> {
                if(task?.isActive==true || MonitorState.starting || stopping){
                    RecorderJournal.event(this,"START_IGNORED","already active")
                    return continuation(command.action)
                }
                // After LMK a stale QS query may say idle while a fresh
                // user-started session lease remains. Never replace that
                // session with a second recording. Use validated recovery.
                if(!command.getBooleanExtra(RESTORED,false)){
                    val previous=RecorderLease.recover(this)
                    if(previous!=null && previous.recording &&
                        db.canResume(previous.id)){
                        RecorderJournal.event(this,"START_REDIRECTED_TO_RECOVERY",
                            "existing id="+previous.id)
                        return onStartCommand(
                            Intent(this,RecordingService::class.java)
                                .setAction(RECOVER),flags,startId)
                    }
                }
                if(!notificationsAllowed()){
                    MonitorState.message="Notifications permission required"
                    RecorderJournal.event(this,"START_DENIED","notification permission")
                    if(!armed)stopSelf(startId)
                    return START_NOT_STICKY
                }
                explicitUserStop=false
                val p=getSharedPreferences("coremetrics_settings",MODE_PRIVATE)
                val request=command.getLongExtra(INTERVAL,p.getLong("interval_ms",5000L))
                val interval=if(request==2000L)2000L else 5000L
                val label=(command.getStringExtra(LABEL)
                    ?:p.getString("label","Gameplay")?:"Gameplay").take(64)
                val config=(command.getStringExtra(CONFIG)
                    ?:p.getString("configuration","Stock")?:"Stock").take(40)
                armedLabel=label
                armedConfig=config
                armedInterval=interval
                val restoredId=command.getLongExtra(RECOVERY_ID,0L)
                val restored=command.getBooleanExtra(RESTORED,false)
                RecorderJournal.event(this,"START_REQUESTED","every="+interval)
                if(!foreground("CoreMetrics · starting",label,false)){
                    MonitorState.message="Foreground recorder unavailable"
                    if(!armed)stopSelf(startId)
                    return START_NOT_STICKY
                }
                MonitorState.starting=true
                MonitorState.standby=false
                val wantsHud=if(restored)command.getBooleanExtra(RECOVERY_HUD,false)
                    else armed || p.getBoolean("hud_enabled",false)
                userWantsHud=wantsHud
                if(wantsHud && hud.permissionGranted()){
                    settings=HudOptions.from(this,command)
                    val result=attachHud()
                    if(result=="HUD_ATTACHED" || result=="HUD_ALREADY_ATTACHED") {
                        armed=true
                        hud.mode(true,System.currentTimeMillis())
                        if(restored && command.getBooleanExtra(RECOVERY_LOCK,false)){
                            val lockResult=hud.setLocked(true)
                            MonitorState.hudLocked=hud.isLocked()
                            RecorderJournal.event(this,"HUD_LOCK_RECOVERY",lockResult)
                        }
                    }
                }
                refreshTile()
                task=scope.launch {
                    var id=0L
                    var outcome="INTERRUPTED"
                    try{
                        id=withContext(Dispatchers.IO){
                            if(restoredId>0L) {
                                check(db.canResume(restoredId)) {
                                    "Previously recording session is no longer active"
                                }
                                restoredId
                            } else {
                                val recovered=db.recoverInterruptedSessions(
                                    System.currentTimeMillis())
                                if(recovered>0)RecorderJournal.event(this@RecordingService,
                                    "RECOVERED_INTERRUPTED",recovered.toString())
                                db.begin(label,config,System.currentTimeMillis())
                            }
                        }
                        withContext(Dispatchers.IO){
                            RecorderLease.start(this@RecordingService,id,label,config,
                                interval,wantsHud,restored)
                        }
                        MonitorState.sessionId=id
                        MonitorState.startedAt=if(restoredId>0L)
                            withContext(Dispatchers.IO){
                                db.originalStartMs(id)
                            } ?: System.currentTimeMillis()
                        else System.currentTimeMillis()
                        MonitorState.sampleCount=if(restoredId>0L)
                            withContext(Dispatchers.IO){db.storedSampleCount(id)}
                        else 0L
                        MonitorState.starting=false
                        MonitorState.running=true
                        MonitorState.message=label+" · "+config
                        RecorderJournal.event(this@RecordingService,
                            if(restoredId>0L)"SESSION_RESUMED" else "SESSION_PERSISTED",
                            "id="+id+" persisted_count="+MonitorState.sampleCount)
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
                            if(armed && hud.visible())withContext(Dispatchers.Main.immediate){
                                runCatching{hud.update(s,MonitorState.sampleCount)}
                                    .onFailure{RecorderJournal.event(this@RecordingService,
                                        "HUD_UPDATE_ERROR",it.javaClass.simpleName)}
                            }
                            if(MonitorState.sampleCount==1L)RecorderJournal.event(
                                this@RecordingService,"FIRST_SAMPLE_WRITTEN","id="+id)
                            val elapsed=SystemClock.elapsedRealtime()
                            if(elapsed-lastNotice>12_000L){
                                lastNotice=elapsed
                                withContext(Dispatchers.IO){
                                    RecorderLease.heartbeat(this@RecordingService)
                                }
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
                        outcome=RecorderContinuation.cancellationOutcome(explicitUserStop)
                    }catch(e:Exception){
                        outcome="FAILED"
                        RecorderJournal.event(this@RecordingService,"SAMPLING_FAILED",
                            e.javaClass.simpleName+":"+(e.message?:""))
                    }finally{
                        withContext(NonCancellable){
                            // Finish the persisted row BEFORE removing its
                            // recovery lease. An LMK in this narrow window
                            // must not strand a RECORDING row without any
                            // chance of resuming its original session ID.
                            withContext(Dispatchers.IO){
                                if(id!=0L)runCatching{
                                    db.finish(id,System.currentTimeMillis(),outcome)
                                }.onFailure{
                                    RecorderJournal.event(this@RecordingService,
                                        "SESSION_FINISH_DB_ERROR",it.javaClass.simpleName)
                                }
                            }
                            // Explicit Stop remains terminal even if the DB
                            // write failed: no unsolicited new recording.
                            RecorderLease.clear(this@RecordingService)
                            RecorderJournal.event(this@RecordingService,
                                "SESSION_FINALIZED","id="+id+" status="+outcome+
                                    " samples="+MonitorState.sampleCount)
                            MonitorState.clear(if(outcome=="COMPLETED")
                                "Recording saved" else "Recording "+outcome.lowercase())
                            withContext(Dispatchers.Main.immediate){
                                task=null
                                stopping=false
                                if(destroying) {
                                    armed=false
                                    MonitorState.standby=false
                                } else if(armed && userWantsHud && !disarmAfterStop) {
                                    standby()
                                } else {
                                    disarmAfterStop=false
                                    disarmNow()
                                }
                            }
                        }
                    }
                }
                return START_STICKY
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
        if(MonitorState.running && !hud.visible()) {
            val show=PendingIntent.getService(this,203,
                Intent(this,RecordingService::class.java).setAction(HUD_SHOW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_view,"Show HUD",show).build())
        }
        if(hud.isLocked() && hud.visible()) {
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
        destroying=true
        stopping=true
        val remove=runCatching{hud.hide()}
            .getOrElse{"HUD_HIDE_EXCEPTION_"+it.javaClass.simpleName}
        RecorderJournal.event(this,"HUD_REMOVE_ON_DESTROY",
            remove+" "+hud.windowIdentity())
        hud.unbindCallbacks()
        MonitorState.serviceCreated=false
        MonitorState.standby=false
        MonitorState.hudVisible=false
        task?.cancel()
        scope.cancel()
        val active=task
        if(active!=null)active.invokeOnCompletion{db.close()}else db.close()
        super.onDestroy()
    }
}
