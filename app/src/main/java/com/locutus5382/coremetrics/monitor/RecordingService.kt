package com.locutus5382.coremetrics.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.database.sqlite.SQLiteDatabaseLockedException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.service.quicksettings.TileService
import com.locutus5382.coremetrics.BuildConfig
import com.locutus5382.coremetrics.domain.LegacyAuditObservation
import com.locutus5382.coremetrics.domain.EngineState
import com.locutus5382.coremetrics.domain.RecordingState
import com.locutus5382.coremetrics.engine.DebugEngineAdmissionEvidence
import com.locutus5382.coremetrics.engine.DebugEngineDenial
import com.locutus5382.coremetrics.engine.HeimdallDebugOwnershipPolicy
import com.locutus5382.coremetrics.engine.HeimdallDebugEngineRuntime
import com.locutus5382.coremetrics.engine.HeimdallRecorderStatusPolicy
import com.locutus5382.coremetrics.engine.HeimdallNativeRecoveryPolicy
import com.locutus5382.coremetrics.engine.NativeRecoveryEvidence
import com.locutus5382.coremetrics.engine.NativeRecoveryDecision
import com.locutus5382.coremetrics.engine.NativeRecoveryHandoffEvidence
import com.locutus5382.coremetrics.engine.HeimdallLeaseRestartGuard
import com.locutus5382.coremetrics.engine.LeaseOwnerRoute
import com.locutus5382.coremetrics.engine.LegacyHandoffEvidence
import com.locutus5382.coremetrics.engine.ReservedCommandRoute
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.data.ProcessSessionStoreOwner
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
import kotlinx.coroutines.CancellationException as CoroutineCancelled

/**
 * User-started :monitor foreground service. ARMED = one visible overlay and
 * notification but NO telemetry, polling task or SQLite writes. REC = existing
 * session collector. HUD click switches modes without an extra service.
 */
class RecordingService:Service() {
    companion object {
        const val HEIMDALL_DEBUG_START="com.locutus5382.coremetrics.DEBUG_HEIMDALL_START"
        const val HEIMDALL_DEBUG_STOP="com.locutus5382.coremetrics.DEBUG_HEIMDALL_STOP"
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
        const val AUTO_HUD="hud_auto_enabled"
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
    // A restored READY HUD is intentionally non-sticky; REC remains sticky.
    private var standbyRecoveryEligible=false
    private var appInForeground=false
    private var userWantsHud=true
    private var settings=HudOptions()
    private var armedLabel="Gameplay"
    private var armedConfig="Stock"
    private var armedInterval=5000L

    /** Main-thread reservation. It stays held until service teardown, even after
     * STOP, so stale legacy intents cannot open another session mid-finalization. */
    private var heimdallDebugReserved=false
    @Volatile private var heimdallDebugStopPending=false
    private var heimdallDebugStarted=false
    private var heimdallDebugRuntime:HeimdallDebugEngineRuntime?=null

    /** Debug-only, read-only observation; never authorizes an effect. */
    private fun shadow(event: LegacyAuditObservation) {
        if (BuildConfig.DEBUG) HeimdallServiceShadowAudit.observe(event)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) HeimdallServiceShadowAudit.onServiceCreated()
        db=ProcessSessionStoreOwner.get(applicationContext)
        hud=MonitorHudOwner.get(applicationContext)
        // HudOptions.from opens the shared SQLite settings database.
        // Defer that read until AFTER the foreground-service deadline.
        MonitorState.serviceCreated=true
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
        val nonRecoverableStandby=MonitorState.standby &&
            !MonitorState.running && !MonitorState.starting &&
            !standbyRecoveryEligible
        return if(RecorderContinuation.sticky(action,active,stopping,
                nonRecoverableStandby))
            START_STICKY else START_NOT_STICKY
    }
    /** Temporary in-app pause is independent of the user's HUD hide choice.
     * Never write HUD preference/lease merely because our Activity is visible.
     */
    private fun syncHudVisibility() {
        MonitorState.hudRequested=userWantsHud
        MonitorState.hudAttached=hud.attached()
        MonitorState.appPaused=appInForeground && userWantsHud
        if(!MonitorState.hudAttached) {
            MonitorState.hudVisible=false
            MonitorState.hudState=if(userWantsHud)"HUD_NOT_ATTACHED" else "HUD_OFF"
            return
        }
        hud.setVisible(userWantsHud && !appInForeground)
        MonitorState.hudVisible=hud.visible()
        MonitorState.hudState=when {
            MonitorState.appPaused -> "HUD_PAUSED_IN_APP"
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
        standbyRecoveryEligible=false
        MonitorState.standby=false
        val removal=hud.hide()
        RecorderJournal.event(this,"HUD_REMOVE",removal+" "+hud.windowIdentity())
        MonitorState.hudState=removal
        MonitorState.hudVisible=hud.visible()
        MonitorState.hudAttached=hud.attached()
        MonitorState.hudRequested=false
        MonitorState.appPaused=false
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
    private fun standby(recovered:Boolean=false,recoverable:Boolean=true){
        MonitorState.clear("HUD ready · tap REC in game")
        MonitorState.standby=true
        standbyRecoveryEligible=RecorderContinuation.standbyAutoRecoverable(
            recovered,recoverable)
        val persisted=RecorderLease.standby(this,armedLabel,armedConfig,
            armedInterval,recovered,standbyRecoveryEligible)
        if(recovered)RecorderJournal.event(this,"STANDBY_RECOVERED_ONCE",
            "no further automatic standby restarts until explicit ARM")
        if(!persisted)RecorderJournal.event(this,"STANDBY_LEASE_WRITE_FAILED",
            "standby remains active until process exit; no guaranteed recovery")
        hud.mode(false)
        foreground("CoreMetrics · standby","No sensors sampled until REC",true)
        refreshTile()
    }
    /** STOP must not silently ignore a failed SharedPreferences.commit.
     * Clearing the old lease on failure prevents unintended re-recording;
     * the original SQLite row is still finalized by the cancellation handler. */
    private fun persistTerminalIntent(command:String):Boolean {
        if(RecorderLease.requestStop(this))return true
        val cleared=RecorderLease.clear(this)
        RecorderJournal.event(this,"STOP_INTENT_WRITE_FAILED",
            "command="+command+" lease_cleared="+cleared)
        MonitorState.message=if(cleared)
            "Stopping; recovery disabled (lease storage failed)"
        else "Stopping; persistent storage unavailable"
        return false
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

    /**
     * Debug-only opt-in; no production UI/QS path chooses this action.
     * Called on the service main thread AFTER foreground promotion.
     */
    private fun requestHeimdallDebugStart(startId:Int):Int {
        val gate=HeimdallDebugOwnershipPolicy.denial(DebugEngineAdmissionEvidence(
            debugBuild=BuildConfig.DEBUG,
            explicitDebugStart=true,
            notificationsAllowed=notificationsAllowed(),
            legacyTaskPresent=task!=null,
            legacyStartingOrRunning=MonitorState.running || MonitorState.starting,
            legacyStandbyArmed=armed || MonitorState.standby,
            legacySaving=MonitorState.saving || stopping,
            legacyOverlayAttached=hud.attached(),
            unresolvedRawLease=RecorderLease.hasRawActiveLease(this),
            unfinishedSqliteSession=false, // checked on IO before ownership transfer
            debugSlotOccupied=heimdallDebugReserved,
        ))
        if(gate!=null) {
            RecorderJournal.event(this,"HEIMDALL_DEBUG_DENIED",gate.name)
            if(task==null && !armed && !MonitorState.starting &&
                !MonitorState.running && !MonitorState.standby) stopSelf(startId)
            return START_NOT_STICKY
        }
        heimdallDebugReserved=true
        MonitorState.starting=true
        MonitorState.message="Heimdall DEBUG · checking exclusive ownership"
        scope.launch(Dispatchers.IO) {
            try {
                // No legacy commands can begin while reserved. A stale raw
                // lease OR unfinished SQLite session forbids begin(), since
                // SessionStore.begin() marks prior unfinished rows interrupted.
                val defaults=db.recordingDefaults()
                val rowOpen=db.hasUnfinishedRecording()
                val leaseActive=RecorderLease.hasRawActiveLease(this@RecordingService)
                check(!rowOpen && !leaseActive) {
                    if(rowOpen) DebugEngineDenial.UNFINISHED_SQLITE_SESSION.name
                    else DebugEngineDenial.UNRESOLVED_LEASE.name
                }
                val runtime=HeimdallDebugEngineRuntime(
                    application,db,scope,defaults.intervalMs,
                    onState={ state ->
                        withContext(Dispatchers.Main.immediate) {
                            observeHeimdallDebugState(state)
                        }
                    })
                val accepted=runtime.start(LegacyHandoffEvidence(
                    runtime.epoch,true,true,true,true),db.activeProfileId().takeIf{it>0L})
                check(accepted) { "DEBUG_HANDOFF_REJECTED" }
                withContext(Dispatchers.Main.immediate) {
                    heimdallDebugRuntime=runtime
                    if(heimdallDebugStopPending) scope.launch { runtime.stop() }
                }
            } catch (cancel:CancellationException) {
                throw cancel
            } catch (error:Exception) {
                withContext(Dispatchers.Main.immediate) {
                    RecorderJournal.event(this@RecordingService,
                        "HEIMDALL_DEBUG_START_FAILED",error.javaClass.simpleName)
                    MonitorState.clear("Heimdall debug startup refused")
                    // Keep the reservation through stopSelf/onDestroy.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        return START_STICKY
    }

    private fun requestHeimdallDebugStop() {
        heimdallDebugStopPending=true
        heimdallDebugRuntime?.let { runtime ->
            scope.launch {
                if (runtime.stop()) heimdallDebugStopPending=false
            }
        }
    }

    private fun observeHeimdallDebugState(state:EngineState) {
        if(!heimdallDebugReserved || destroying) return
        val recording=state.recording
        if(recording !is RecordingState.Idle) heimdallDebugStarted=true
        val presented = HeimdallRecorderStatusPolicy.present(state)
        MonitorState.starting = presented.preparing
        // A durable STOP / failed finalizer is NOT active sample collection.
        MonitorState.running = presented.recording
        MonitorState.saving = presented.saving
        MonitorState.heimdallRecorderPhase = presented.phase.name
        MonitorState.heimdallNeedsRetry = presented.retryAvailable
        MonitorState.heimdallNeedsRepair = presented.repairRequired
        MonitorState.heimdallOriginalSessionId = presented.sessionId ?: 0L
        MonitorState.heimdallIssueCode = presented.issueCode
        MonitorState.sessionId=when(recording) {
            is RecordingState.Recording -> recording.sessionId
            is RecordingState.StopRequested -> recording.sessionId
            is RecordingState.Finalizing -> recording.sessionId
            is RecordingState.FinalizeFailed -> recording.sessionId
            is RecordingState.Recovering -> recording.sessionId
            else -> 0L
        }
        // The domain owns monotonic session elapsed time. The legacy private
        // StatusProvider exposes a wall-clock start, so convert without
        // blocking the service main thread on a SQLite query.
        val originalElapsed=when(recording) {
            is RecordingState.Recording -> recording.startedAtElapsedMs
            is RecordingState.Recovering -> recording.startedAtElapsedMs
            else -> null
        }
        if(originalElapsed!=null) {
            val since=(SystemClock.elapsedRealtime()-originalElapsed)
                .coerceAtLeast(0L)
            MonitorState.startedAt=(System.currentTimeMillis()-since)
                .coerceAtLeast(0L)
        }
        MonitorState.latest=null // no synthetic or idle telemetry
        MonitorState.message = presented.message
        if(heimdallDebugStopPending &&
            (recording is RecordingState.Recording ||
             recording is RecordingState.Starting)) {
            heimdallDebugRuntime?.let { runtime ->
                // Don't drop a STOP just because the new runtime has not
                // yet been assigned to the service on the main thread.
                heimdallDebugStopPending=false
                scope.launch { runtime.stop() }
            }
        }
        if(recording is RecordingState.Recording) {
            foreground("Heimdall · DEBUG recording",
                "Experimental engine · original SQLite session "+recording.sessionId,false)
        }
        refreshTile()
        if(heimdallDebugStarted && recording is RecordingState.Idle) {
            RecorderJournal.event(this,"HEIMDALL_DEBUG_COMPLETE",
                "Heimdall controller reached Idle")
            MonitorState.clear("Heimdall debug session saved")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if(recording is RecordingState.Failed) {
            RecorderJournal.event(this,"HEIMDALL_DEBUG_FAILED",recording.reason)
            MonitorState.clear("Heimdall debug engine failed")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * M2-I native restart. One :monitor foreground service is promoted
     * before this method touches preferences or SQLite. Invalid evidence
     * is preserved and quarantined, never handed to the legacy sampler.
     */
    private fun requestHeimdallNativeRecovery(
        startId:Int, stopRequestedNow:Boolean
    ):Int {
        if (!BuildConfig.DEBUG || heimdallDebugReserved || task!=null ||
            armed || MonitorState.running || MonitorState.starting ||
            MonitorState.standby || MonitorState.saving || hud.attached()) {
            RecorderJournal.event(this,"HEIMDALL_RECOVERY_DENIED",
                "service owner not clean")
            if(!heimdallDebugReserved) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Reserve on the main thread BEFORE async validation so no
        // later legacy START/ARM/RECOVER can slip through during IO.
        heimdallDebugReserved=true
        MonitorState.starting=true
        MonitorState.message="Heimdall DEBUG · validating original session"
        scope.launch(Dispatchers.IO) {
            try {
                val rawOwner=RecorderLease.rawOwner(this@RecordingService)
                val saved=RecorderLease.recover(this@RecordingService)
                val rowStatus=saved?.takeIf { it.id>0L && it.recording &&
                    it.owner==RecorderLease.Owner.HEIMDALL_DEBUG }
                    ?.let { db.sessionById(it.id)?.status }
                val plan=HeimdallNativeRecoveryPolicy.decide(NativeRecoveryEvidence(
                    rawOwner=rawOwner?.name,
                    leaseEligible=saved!=null &&
                        saved.owner==RecorderLease.Owner.HEIMDALL_DEBUG,
                    originalSessionId=saved?.id,
                    recordingLease=saved?.recording==true,
                    persistedStop=saved?.stopRequested==true,
                    originalRowStatus=rowStatus,
                    intervalMs=saved?.intervalMs,
                    userRequestedStop=stopRequestedNow ||
                        heimdallDebugStopPending,
                ))
                RecorderJournal.event(this@RecordingService,"HEIMDALL_RECOVERY_PLAN",
                    plan.name+" id="+(saved?.id ?: 0L))
                if(plan==NativeRecoveryDecision.QUARANTINE) {
                    error("NATIVE_RECOVERY_EVIDENCE_INVALID")
                }
                if(plan==NativeRecoveryDecision.RELEASE_ALREADY_FINALIZED_LEASE) {
                    check(saved!=null &&
                        RecorderLease.rawOwner(this@RecordingService)==
                        RecorderLease.Owner.HEIMDALL_DEBUG &&
                        db.sessionById(saved.id)?.status==rowStatus &&
                        rowStatus!="RECORDING") { "FINALIZED_LEASE_RECHECK_FAILED" }
                    check(RecorderLease.clear(this@RecordingService)) {
                        "FINALIZED_LEASE_CLEAR_FAILED"
                    }
                    withContext(Dispatchers.Main.immediate) {
                        RecorderJournal.event(this@RecordingService,
                            "HEIMDALL_RECOVERY_COMPLETED_LEASE", "same id")
                        MonitorState.clear("Previous Heimdall session already finalized")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                    return@launch
                }
                val recoveredId=saved?.id ?: error("MISSING_ORIGINAL_SESSION")
                val runtime=HeimdallDebugEngineRuntime(
                    application,db,scope,saved.intervalMs,
                    onState={ state ->
                        withContext(Dispatchers.Main.immediate) {
                            observeHeimdallDebugState(state)
                        }
                    })
                val handoff=withContext(Dispatchers.Main.immediate) {
                    // Recheck actual live service state, not caller-supplied
                    // ownership booleans. The only active "starting" flag is
                    // our own main-thread debug reservation.
                    NativeRecoveryHandoffEvidence(
                        serviceEpoch=runtime.epoch,
                        originalSessionId=recoveredId,
                        legacyTaskAbsent=task==null,
                        legacySessionInactive=!armed && !MonitorState.running &&
                            !MonitorState.standby && !stopping,
                        legacyOverlayDetached=!hud.attached(),
                    )
                }
                val accepted=runtime.recover(
                    handoff=handoff,
                    originalSessionId=recoveredId,
                    persistedStop=saved.stopRequested,
                    stopRequestedNow=plan==NativeRecoveryDecision.PERSIST_STOP_AND_FINALIZE ||
                        heimdallDebugStopPending,
                )
                check(accepted) { "NATIVE_RECOVERY_HANDOFF_REFUSED" }
                withContext(Dispatchers.Main.immediate) {
                    heimdallDebugRuntime=runtime
                    RecorderJournal.event(this@RecordingService,
                        "HEIMDALL_RECOVERY_ACCEPTED",
                        plan.name+" id="+recoveredId)
                    if(heimdallDebugStopPending) requestHeimdallDebugStop()
                }
            } catch (cancel:CancellationException) {
                throw cancel
            } catch (error:Exception) {
                withContext(Dispatchers.Main.immediate) {
                    RecorderJournal.event(this@RecordingService,
                        "HEIMDALL_RECOVERY_QUARANTINED",
                        error.javaClass.simpleName)
                    MonitorState.clear("Heimdall recovery evidence requires repair")
                    MonitorState.saving=true // original persisted row may remain
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        return START_STICKY
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // START / ARM / RECOVER and Android null restarts must satisfy the FGS
        // deadline BEFORE recovery-lease, SQLite settings or journal disk I/O.
        // The notification says PREPARING, never REC, until the persisted
        // session and recovery lease have been checked.
        val requestedAction=intent?.action
        val startupRequest=intent==null || requestedAction==START ||
            requestedAction==ARM || requestedAction==RECOVER ||
            (BuildConfig.DEBUG && (requestedAction==HEIMDALL_DEBUG_START ||
                requestedAction==HEIMDALL_DEBUG_STOP ||
                requestedAction==STOP || requestedAction==DISARM))
        val alreadyForeground=armed || MonitorState.running ||
            MonitorState.starting || MonitorState.standby || task?.isActive==true ||
            heimdallDebugReserved
        if(startupRequest && !alreadyForeground) {
            if(!notificationsAllowed() || !foreground(
                    "CoreMetrics · preparing",
                    "Checking the user-requested recorder or HUD standby",
                    requestedAction==ARM)) {
                MonitorState.message="Cannot start foreground recorder or standby"
                stopSelf(startId)
                return START_NOT_STICKY
            }
            RecorderJournal.event(this,"SERVICE_CREATED",
                "pkg="+packageName+" pid="+android.os.Process.myPid()+
                    " instance="+System.identityHashCode(this)+
                    " "+hud.windowIdentity())
        }
        if(BuildConfig.DEBUG && requestedAction==HEIMDALL_DEBUG_START) {
            if(heimdallDebugReserved) return START_STICKY // idempotent duplicate
            if(RecorderLease.rawOwner(this)==RecorderLease.Owner.HEIMDALL_DEBUG)
                return requestHeimdallNativeRecovery(startId,false)
            return requestHeimdallDebugStart(startId)
        }
        if(heimdallDebugReserved) {
            // Every legacy command is blocked while Heimdall owns this service.
            // Existing notification/QS STOP and DISARM are forwarded to the
            // active owner, not dropped or interpreted as new legacy sessions.
            if(HeimdallDebugOwnershipPolicy.routeWhileReserved(requestedAction)==
                ReservedCommandRoute.HEIMDALL_STOP) requestHeimdallDebugStop()
            else RecorderJournal.event(this,"HEIMDALL_BLOCK_LEGACY_INTENT",
                (requestedAction?:"NULL_RESTART").take(64))
            return START_STICKY
        }
        // An explicit STOP after monitor process death must be persisted
        // before the new engine may resume the original sampler.
        val coldNativeStop=BuildConfig.DEBUG &&
            (requestedAction==STOP || requestedAction==DISARM ||
             requestedAction==HEIMDALL_DEBUG_STOP)
        if(requestedAction==HEIMDALL_DEBUG_STOP &&
            RecorderLease.rawOwner(this)!=RecorderLease.Owner.HEIMDALL_DEBUG) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // M2-H: an in-progress Heimdall recording is tagged in the SAME
        // durable lease commit as its session ID. Never hand that session to
        // legacy RECOVER, START or ARM after Android restarts the service.
        // Even an expired/unknown lease cannot authorize SessionStore.begin().
        val rawOwner=RecorderLease.rawOwner(this)
        val recoveryRoute=HeimdallLeaseRestartGuard.route(
            rawOwner!=null,rawOwner?.name)
        if(recoveryRoute==LeaseOwnerRoute.NATIVE_RECOVERY_REQUIRED &&
            BuildConfig.DEBUG)
            return requestHeimdallNativeRecovery(startId,coldNativeStop)
        if(recoveryRoute!=LeaseOwnerRoute.LEGACY_ALLOWED) {
            RecorderJournal.event(this,"HEIMDALL_RECOVERY_QUARANTINED",
                recoveryRoute.name)
            MonitorState.clear("Heimdall debug recovery requires native owner")
            MonitorState.saving=true // a persisted original row may need repair
            refreshTile()
            stopSelf(startId)
            return START_NOT_STICKY
        }
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
            if(old.stopRequested) {
                // This process did not own the old sampler; retain its
                // terminal-recovery provenance separately from loop exit.
                if (BuildConfig.DEBUG && old.recording)
                    shadow(LegacyAuditObservation.RecoveredTerminal(old.id))
                RecorderJournal.event(this,"STOP_RECOVERY_FINALIZE",
                    "id="+old.id+" same-session terminal reconciliation")
                val finalized=if(old.recording)runCatching {
                    check(db.finish(old.id,System.currentTimeMillis(),
                        "INTERRUPTED_STOPPED")){"Session row missing during STOP recovery"}
                    true
                }.onFailure { RecorderJournal.event(this,
                    "STOP_RECOVERY_DB_ERROR",it.javaClass.simpleName) }
                    .getOrDefault(false) else true
                if (BuildConfig.DEBUG && old.recording)
                    shadow(LegacyAuditObservation.Finalized(old.id, finalized))
                if(finalized)RecorderLease.clear(this)
                // Failure retains the stop_requested lease. No sampling can
                // start; the next explicit recovery may retry finalization.
                MonitorState.clear(if(finalized)"Previous stop finalized"
                    else "Previous stop pending database recovery")
                MonitorState.saving=false
                refreshTile()
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
                    if(previous?.stopRequested==true ||
                        previous?.recording==true && db.canResume(previous.id)){
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
                // A SQL snapshot is shared across UI/:monitor/tile processes.
                // A command with explicit extras still takes precedence.
                val defaults=db.recordingDefaults()
                armedLabel=(command.getStringExtra(LABEL)
                    ?:defaults.label).take(64)
                armedConfig=(command.getStringExtra(CONFIG)
                    ?:defaults.configuration).take(40)
                armedInterval=if(command.getLongExtra(INTERVAL,
                    defaults.intervalMs)==2000L)2000L else 5000L
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
                return continuation(command.action)
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
                armed=false
                disarmAfterStop=true
                MonitorState.standby=false
                val current=task
                if(current!=null && !current.isCompleted || MonitorState.starting){
                    // Do not clear a fresh recovery lease until the recorder
                    // actually persisted its final database status.
                    val stopDurable = persistTerminalIntent("DISARM")
                    if (MonitorState.sessionId > 0L)
                        shadow(LegacyAuditObservation.TerminalIntent(
                            MonitorState.sessionId, stopDurable))
                    MonitorState.saving=true
                    explicitUserStop=true
                    stopping=true
                    current?.cancel()
                } else {
                    RecorderLease.clear(this)
                    disarmNow()
                }
                refreshTile()
                return START_NOT_STICKY
            }
            STOP -> {
                RecorderJournal.event(this,"STOP_REQUESTED","id="+MonitorState.sessionId)
                explicitUserStop=true
                stopping=true
                MonitorState.message="Stopping and saving…"
                val current=task
                if(current==null || current.isCompleted){
                    RecorderLease.clear(this)
                    stopping=false
                    MonitorState.saving=false
                    if(armed && userWantsHud)standby(recoverable=false)
                    else disarmNow()
                }else {
                    val stopDurable = persistTerminalIntent("STOP")
                    if (MonitorState.sessionId > 0L)
                        shadow(LegacyAuditObservation.TerminalIntent(
                            MonitorState.sessionId, stopDurable))
                    MonitorState.saving=true
                    current.cancel()
                }
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
                    if(previous?.stopRequested==true ||
                        previous!=null && previous.recording &&
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
                val defaults=db.recordingDefaults()
                val request=command.getLongExtra(INTERVAL,defaults.intervalMs)
                val interval=if(request==2000L)2000L else 5000L
                val label=(command.getStringExtra(LABEL)
                    ?:defaults.label).take(64)
                val config=(command.getStringExtra(CONFIG)
                    ?:defaults.configuration).take(40)
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
                    else armed || command.getBooleanExtra(AUTO_HUD,defaults.hudEnabled)
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
                    val timings=RecorderTimingStats()
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
                            check(RecorderLease.start(this@RecordingService,id,
                                label,config,interval,wantsHud,restored)) {
                                "Cannot persist recorder recovery lease"
                            }
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
                        shadow(LegacyAuditObservation.SessionAcquired(id))
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
                        var next=SystemClock.elapsedRealtime()
                        var lastNotice=0L
                        var consecutiveSampleFailures=0
                        while(isActive && !stopping){
                            val tickStartNanos=SystemClock.elapsedRealtimeNanos()
                            val startLateMs=(SystemClock.elapsedRealtime()-next)
                                .coerceAtLeast(0L).toDouble()
                            var collectedNanos=tickStartNanos
                            // A dropped tick is a real gap, never a fake row.
                            // One transient sensor or SQLite failure must not
                            // terminate an otherwise usable recording.
                            val s=try {
                                val snapshot=withContext(Dispatchers.IO){
                                    sampler.sample(detector.current(System.currentTimeMillis()))
                                }
                                collectedNanos=SystemClock.elapsedRealtimeNanos()
                                withContext(Dispatchers.IO) {
                                    var writeAttempts=0
                                    while(true) {
                                        try {
                                            db.add(id,snapshot)
                                            break
                                        }catch(e:SQLiteDatabaseLockedException){
                                            // Back off ONCE without blocking the main thread.
                                            if(++writeAttempts>=2)throw e
                                            delay(RecorderTickFailurePolicy.LOCK_RETRY_DELAY_MS)
                                        }
                                    }
                                }
                                if(consecutiveSampleFailures>0)
                                    RecorderJournal.event(this@RecordingService,
                                        "SAMPLE_RECOVERED",
                                        "after="+consecutiveSampleFailures+
                                            " missed ticks id="+id)
                                consecutiveSampleFailures=0
                                snapshot
                            }catch(e:CancellationException){
                                throw e
                            }catch(e:Exception){
                                consecutiveSampleFailures++
                                val type=e.javaClass.simpleName
                                RecorderJournal.event(this@RecordingService,
                                    "SAMPLE_DROPPED", "id="+id+" type="+type+
                                        " consecutive="+consecutiveSampleFailures)
                                if(RecorderTickFailurePolicy.shouldTerminate(
                                        type,consecutiveSampleFailures))throw e
                                null
                            }
                            if(s!=null) {
                            val writtenNanos=SystemClock.elapsedRealtimeNanos()
                            MonitorState.latest=s
                            MonitorState.sampleCount++
                            var hudEndNanos=writtenNanos
                            var updatedHud=false
                            if(armed && hud.visible()) {
                                updatedHud=true
                                withContext(Dispatchers.Main.immediate){
                                    runCatching{hud.update(s,MonitorState.sampleCount)}
                                        .onFailure{RecorderJournal.event(this@RecordingService,
                                            "HUD_UPDATE_ERROR",it.javaClass.simpleName)}
                                }
                                hudEndNanos=SystemClock.elapsedRealtimeNanos()
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
                            // Total elapsed tick includes notifications and
                            // periodic lease heartbeat as well as sensor/DB/HUD.
                            // The fixed-size ring adds NO per-sample disk write.
                            timings.record(
                                collectorMs=(collectedNanos-tickStartNanos)/1e6,
                                databaseMs=(writtenNanos-collectedNanos)/1e6,
                                hudMs=if(updatedHud)(hudEndNanos-writtenNanos)/1e6 else null,
                                totalMs=(SystemClock.elapsedRealtimeNanos()-tickStartNanos)/1e6,
                                startLateMs=startLateMs)
                            }
                            // Maintain cadence even after a dropped tick; no
                            // tight retry loop and no backfilled fake samples.
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
                        // The sampling loop has exited before the DB finalizer runs.
                        if (id > 0L) shadow(LegacyAuditObservation.SamplerExited(id))
                        withContext(NonCancellable){
                            if(timings.count>0L)RecorderJournal.event(
                                this@RecordingService,"RECORDER_TIMING",
                                "id="+id+" "+timings.report())
                            // Finish the persisted row BEFORE removing its
                            // recovery lease. An LMK in this narrow window
                            // must not strand a RECORDING row without any
                            // chance of resuming its original session ID.
                            val finalized=withContext(Dispatchers.IO){
                                if(id==0L)true else runCatching{
                                    check(db.finish(id,System.currentTimeMillis(),
                                        outcome)){"Session row missing during finalization"}
                                    true
                                }.onFailure{
                                    RecorderJournal.event(this@RecordingService,
                                        "SESSION_FINISH_DB_ERROR",it.javaClass.simpleName)
                                }.getOrDefault(false)
                            }
                            if (id > 0L) shadow(LegacyAuditObservation.Finalized(id, finalized))
                            if(finalized)RecorderLease.clear(this@RecordingService)
                            else {
                                val stopDurable = persistTerminalIntent("FINISH_FAILED")
                                if (id > 0L) shadow(LegacyAuditObservation.TerminalIntent(
                                    id, stopDurable))
                            }
                            // On failure a STOP-pending lease survives for a
                            // later explicit retry; never resume sampling.
                            RecorderJournal.event(this@RecordingService,
                                "SESSION_FINALIZED","id="+id+" status="+outcome+
                                    " samples="+MonitorState.sampleCount)
                            MonitorState.clear(if(!finalized)
                                "Stop pending database recovery"
                                else if(outcome=="COMPLETED")"Recording saved"
                                else "Recording "+outcome.lowercase())
                            withContext(Dispatchers.Main.immediate){
                                task=null
                                stopping=false
                                MonitorState.saving=!finalized
                                if(!finalized) {
                                    // Do not overwrite the stop-pending lease
                                    // with a fresh standby lease.
                                    armed=false
                                    disarmAfterStop=true
                                    stopServiceNow()
                                }else if(destroying) {
                                    armed=false
                                    MonitorState.standby=false
                                } else if(armed && userWantsHud && !disarmAfterStop) {
                                    standby(recoverable=false)
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
        if(MonitorState.running && !hud.visible() && !heimdallDebugReserved) {
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
        MonitorState.hudAttached=false
        MonitorState.hudRequested=false
        MonitorState.appPaused=false
        task?.cancel()
        heimdallDebugRuntime?.cancelWithoutImplicitStop()
        scope.cancel()
        if(heimdallDebugReserved) {
            // No premature DB close while the new sampler is still unwinding.
            // Neither an Android teardown nor cancellation implies user STOP.
            scope.coroutineContext[Job]?.invokeOnCompletion {
                db.close()
                shadow(LegacyAuditObservation.ServiceDestroyed)
            }
            super.onDestroy()
            return
        }
        val active=task
        if(active!=null)active.invokeOnCompletion {
            db.close()
            shadow(LegacyAuditObservation.ServiceDestroyed)
        } else {
            db.close()
            shadow(LegacyAuditObservation.ServiceDestroyed)
        }
        super.onDestroy()
    }
}
