package com.locutus5382.coremetrics.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.database.Cursor
import com.locutus5382.coremetrics.monitor.RecorderLease
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.locutus5382.coremetrics.telemetry.Snapshot
import java.io.Writer

/**
 * :monitor owns writes, UI reads through WAL. Data never leaves this database
 * without a user-selected Android document URI.
 */
class SessionStore(context: Context) : SQLiteOpenHelper(context, "coremetrics.db", null, 9) {
    private val leaseContext = context.applicationContext

    data class Session(val id: Long, val startedAt: Long, val endedAt: Long?,
        val label: String, val configuration: String, val status: String, val count: Int,
        val note: String? = null,
        val graphicsDriver: String? = null, val resolutionScale: String? = null,
        val hasExternalPower:Boolean=false, val hasDischargePower:Boolean=false,
        val performanceMode:String?=null,val fanMode:String?=null,
        val clusterTuneMode:String?=null,val profileName:String?=null,
        val deviceManufacturer:String?=null,val deviceModel:String?=null,
        val deviceCode:String?=null,val androidApi:Int?=null)
    /**
     * Fields genuinely used by full-recording DUKO analytics. Keeping this
     * interface separate from Point avoids allocating Point's 25 fields and
     * loading unused strings from SQLite for every report sample.
     */
    interface ReportRow {
        val timeMs:Long
        val cpu:Double?
        val gpu:Double?
        val cpuLoad:Double?
        val gpuLoad:Double?
        val batteryWatts:Double?
        val observedCpuLimits:String?
        val busiestCorePct:Double?
        val batteryPowerStatus:String?
        val batteryTemp:Double?
        val gpuMHz:Double?
        val thermalStatus:Int?
        val memoryPsiSomeAvg10:Double?
        val batteryPowerSource:String?
        val gpuTelemetrySource:String?
    }

    /**
     * ONE instance is reused across the entire report cursor. Callback
     * consumers must not retain this mutable object. The DUKO accumulator
     * snapshots its prior interval into a separate reusable instance.
     */
    class ReportScratch:ReportRow {
        override var timeMs:Long=0L
        override var cpu:Double?=null
        override var gpu:Double?=null
        override var cpuLoad:Double?=null
        override var gpuLoad:Double?=null
        override var batteryWatts:Double?=null
        override var observedCpuLimits:String?=null
        override var busiestCorePct:Double?=null
        override var batteryPowerStatus:String?=null
        override var batteryTemp:Double?=null
        override var gpuMHz:Double?=null
        override var thermalStatus:Int?=null
        override var memoryPsiSomeAvg10:Double?=null
        override var batteryPowerSource:String?=null
        override var gpuTelemetrySource:String?=null
        fun copyFrom(src:ReportRow) {
            timeMs=src.timeMs;cpu=src.cpu;gpu=src.gpu
            cpuLoad=src.cpuLoad;gpuLoad=src.gpuLoad
            batteryWatts=src.batteryWatts
            observedCpuLimits=src.observedCpuLimits
            busiestCorePct=src.busiestCorePct
            batteryPowerStatus=src.batteryPowerStatus
            batteryTemp=src.batteryTemp;gpuMHz=src.gpuMHz
            thermalStatus=src.thermalStatus
            memoryPsiSomeAvg10=src.memoryPsiSomeAvg10
            batteryPowerSource=src.batteryPowerSource
            gpuTelemetrySource=src.gpuTelemetrySource
        }
    }

    data class Point(override val timeMs: Long, override val cpu: Double?, override val gpu: Double?,
        override val cpuLoad: Double?, override val gpuLoad: Double?, val ram: Double?,
        override val batteryWatts: Double?, val sfFps: Double?, val appPackage: String?,
        override val observedCpuLimits: String? = null, override val busiestCorePct: Double? = null,
        override val batteryPowerStatus: String? = null, val busiestCore: String? = null,
        override val batteryTemp: Double? = null, val batteryPct: Int? = null,
        val cpuClocks: String? = null, override val gpuMHz: Double? = null,
        override val thermalStatus: Int? = null, val thermalHeadroom: Double? = null,
        val swapUsedGiB: Double? = null, override val memoryPsiSomeAvg10: Double? = null,
        val sampleDurationMs: Double? = null, val sampleElapsedMs: Long? = null,
        override val batteryPowerSource:String?=null,
        override val gpuTelemetrySource:String?=null):ReportRow

    /**
     * Single cross-process source for recording-critical QS settings.
     * User input is metadata, NOT proof that a device setting is active.
     */
    data class RecordingDefaults(
        val label:String="Gameplay",
        val configuration:String="Stock",
        val intervalMs:Long=5_000L,
        val hudEnabled:Boolean=false
    )

    data class Link(val id:Long,val firstId:Long,val secondId:Long,val createdAt:Long)
    data class QuickProfile(
        val id:Long,val name:String,val gameNote:String?,
        val graphicsDriver:String?,val resolutionScale:String?,
        val performanceMode:String?,val fanMode:String?,
        val clusterTuneMode:String?)
    private val performanceModes=setOf("Standard","Performance","High Performance")
    private val fanModes=setOf("Off","Quiet","Smart","Sport","Custom")
    private val clusterModes=setOf("Stock","Small","Medium","Large","Custom")
    private fun cleanLabel(value:String?,max:Int=64):String? =
        value?.trim()?.takeIf{it.isNotEmpty() && it.length<=max &&
            it.none{c->c.code<32 || c.code==127 || c.code in 0x80..0x9f}}
    private fun createProfileTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS test_profiles(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            game_note TEXT, graphics_driver TEXT, resolution_scale TEXT,
            performance_mode TEXT, fan_mode TEXT, cluster_tune_mode TEXT)""")
    }


    private fun createSettingsTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recording_settings(
            id INTEGER PRIMARY KEY CHECK(id=1),
            label TEXT NOT NULL,
            configuration TEXT NOT NULL,
            interval_ms INTEGER NOT NULL CHECK(interval_ms IN (2000,5000)),
            hud_enabled INTEGER NOT NULL CHECK(hud_enabled IN (0,1)),
            active_profile_id INTEGER NOT NULL DEFAULT 0,
            hud_options_json TEXT)""")
        // One-time legacy migration. Thereafter the database, rather than
        // per-process SharedPreferences caches, is authoritative for QS.
        val p=leaseContext.getSharedPreferences("coremetrics_settings",Context.MODE_PRIVATE)
        val values=ContentValues().apply {
            put("id",1)
            put("label",(p.getString("label","Gameplay")?:"Gameplay")
                .trim().ifBlank{"Gameplay"}.take(64))
            put("configuration",(p.getString("configuration","Stock")?:"Stock")
                .take(40))
            put("interval_ms",if(p.getLong("interval_ms",5000L)==2000L)2000L else 5000L)
            put("hud_enabled",if(p.getBoolean("hud_enabled",false))1 else 0)
            put("active_profile_id",p.getLong("active_test_profile_id",0L).coerceAtLeast(0L))
        }
        db.insertWithOnConflict("recording_settings",null,values,
            SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized fun recordingDefaults():RecordingDefaults =
        readableDatabase.rawQuery("""SELECT label,configuration,interval_ms,hud_enabled
            FROM recording_settings WHERE id=1""",null).use {c->
            if(c.moveToFirst())RecordingDefaults(
                c.getString(0),c.getString(1),c.getLong(2),c.getInt(3)!=0)
            else RecordingDefaults()
        }

    /** Update exactly the desired defaults, not the selected profile ID.
     * A single SQL update is visible to both UI and :monitor immediately.
     */
    @Synchronized fun saveRecordingDefaults(value:RecordingDefaults):Boolean {
        val label=cleanLabel(value.label,64) ?: "Gameplay"
        require(value.configuration.length<=40 && value.configuration.isNotBlank())
        require(value.intervalMs in setOf(2000L,5000L))
        return writableDatabase.update("recording_settings",ContentValues().apply {
            put("label",label)
            put("configuration",value.configuration)
            put("interval_ms",value.intervalMs)
            put("hud_enabled",if(value.hudEnabled)1 else 0)
        },"id=1",null)==1
    }
    @Synchronized fun saveHudEnabled(enabled:Boolean):Boolean =
        writableDatabase.update("recording_settings",ContentValues().apply {
            put("hud_enabled",if(enabled)1 else 0)
        },"id=1",null)==1

    @Synchronized fun hudOptionsJson():String? =
        readableDatabase.rawQuery(
            "SELECT hud_options_json FROM recording_settings WHERE id=1",null
        ).use {c->if(c.moveToFirst()&&!c.isNull(0))c.getString(0)else null}

    /** Cosmetic settings are one small JSON snapshot, independent from REC. */
    @Synchronized fun saveHudOptionsJson(json:String):Boolean {
        require(json.length<=4096)
        return writableDatabase.update("recording_settings",ContentValues().apply {
            put("hud_options_json",json)
        },"id=1",null)==1
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            started_at INTEGER NOT NULL, ended_at INTEGER,
            label TEXT NOT NULL, configuration TEXT NOT NULL,
            status TEXT NOT NULL, game_note TEXT,
            graphics_driver TEXT, resolution_scale TEXT,
            performance_mode TEXT, fan_mode TEXT, cluster_tune_mode TEXT,
            profile_name TEXT,
            device_manufacturer TEXT, device_model TEXT, device_code TEXT,
            android_api INTEGER)""")
        db.execSQL("""CREATE TABLE samples (
            session_id INTEGER NOT NULL, time_ms INTEGER NOT NULL,
            cpu_temp REAL, gpu_temp REAL, battery_temp REAL, battery_pct INTEGER,
            cpu_pct INTEGER, busiest_pct INTEGER, busiest_core TEXT,
            gpu_pct INTEGER, gpu_mhz REAL, ram_gib REAL,
            cpu_clocks TEXT, app_package TEXT,
            battery_net_w REAL, battery_ua INTEGER, battery_mv INTEGER,
            battery_source TEXT, battery_status TEXT,
            sf_layer_fps REAL, sf_frame_ms REAL, sf_status TEXT,
            observed_cpu_limits TEXT,
            thermal_status INTEGER, thermal_headroom REAL, swap_used_gib REAL,
            mem_psi_some10 REAL, sample_duration_ms REAL,
            sample_elapsed_ms INTEGER, gpu_source TEXT)""")
        db.execSQL("CREATE INDEX samples_by_time ON samples(session_id,time_ms)")
        // Cover battery status predicates without visiting large sample rows.
        db.execSQL("CREATE INDEX samples_by_session_power ON samples(session_id,battery_status)")
        createLinkTable(db)
        createProfileTable(db)
        createSettingsTable(db)
    }
    private fun createLinkTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS session_links (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            first_id INTEGER NOT NULL, second_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(first_id, second_id),
            CHECK(first_id < second_id))""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..8 && newVersion==9) {
            "Unsupported database migration $oldVersion -> $newVersion"
        }
        if(oldVersion<2) {
            for((name,type) in listOf(
                "battery_net_w" to "REAL","battery_ua" to "INTEGER",
                "battery_mv" to "INTEGER","battery_source" to "TEXT",
                "battery_status" to "TEXT","sf_layer_fps" to "REAL",
                "sf_frame_ms" to "REAL","sf_status" to "TEXT",
                "observed_cpu_limits" to "TEXT"
            ))db.execSQL("ALTER TABLE samples ADD COLUMN "+name+" "+type)
        }
        if(oldVersion<3) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN game_note TEXT")
            createLinkTable(db)
        }
        if(oldVersion<4) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN graphics_driver TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN resolution_scale TEXT")
        }
        if(oldVersion<5) {
            db.execSQL("CREATE INDEX IF NOT EXISTS samples_by_session_power "+
                "ON samples(session_id,battery_status)")
        }
        if(oldVersion<6) {
            for((name,type) in listOf(
                "thermal_status" to "INTEGER", "thermal_headroom" to "REAL",
                "swap_used_gib" to "REAL", "mem_psi_some10" to "REAL",
                "sample_duration_ms" to "REAL", "sample_elapsed_ms" to "INTEGER"
            )) db.execSQL("ALTER TABLE samples ADD COLUMN "+name+" "+type)
        }
        if(oldVersion<7) {
            for(name in listOf("performance_mode","fan_mode",
                "cluster_tune_mode","profile_name"))
                db.execSQL("ALTER TABLE sessions ADD COLUMN "+name+" TEXT")
            createProfileTable(db)
        }
        if(oldVersion<8)createSettingsTable(db)
        if(oldVersion<9) {
            for((name,type) in listOf(
                "device_manufacturer" to "TEXT",
                "device_model" to "TEXT",
                "device_code" to "TEXT",
                "android_api" to "INTEGER"))
                db.execSQL("ALTER TABLE sessions ADD COLUMN "+name+" "+type)
            db.execSQL("ALTER TABLE samples ADD COLUMN gpu_source TEXT")
        }
    }

    /** Optional, explicitly user-entered test settings; never inferred from load. */
    @Synchronized fun updateMetadata(
        sessionId:Long, graphicsDriver:String?, resolutionScale:String?
    ):Boolean {
        if(sessionId<=0L)return false
        val driver=SessionMetadata.driver(graphicsDriver)
        val scale=SessionMetadata.resolution(resolutionScale)
        if(graphicsDriver!=null && driver==null ||
            resolutionScale!=null && scale==null)return false
        val values=ContentValues().apply {
            put("graphics_driver",driver)
            put("resolution_scale",scale)
        }
        return writableDatabase.update("sessions",values,
            "id=? AND status!='RECORDING'",arrayOf(sessionId.toString()))==1
    }

    /** Each chip edits one column atomically: fast two-tap changes cannot
     * overwrite a different tag from a stale UI snapshot. */
    @Synchronized fun updateGraphicsDriver(sessionId:Long,tag:String?):Boolean {
        if(sessionId<=0L)return false
        val accepted=SessionMetadata.driver(tag)
        // An invalid non-null string MUST NOT silently overwrite a valid
        // driver with NULL and then report the SQLite update as success.
        if(tag!=null && accepted==null)return false
        return writableDatabase.update("sessions",ContentValues().apply {
            put("graphics_driver",accepted)
        },"id=? AND status!='RECORDING'",arrayOf(sessionId.toString()))==1
    }
    @Synchronized fun updateResolutionScale(sessionId:Long,tag:String?):Boolean {
        if(sessionId<=0L)return false
        val accepted=SessionMetadata.resolution(tag)
        if(tag!=null && accepted==null)return false
        return writableDatabase.update("sessions",ContentValues().apply {
            put("resolution_scale",accepted)
        },"id=? AND status!='RECORDING'",arrayOf(sessionId.toString()))==1
    }

    /** Named reusable presets; historical sessions copy values, not IDs. */
    @Synchronized fun quickProfiles():List<QuickProfile> =
        readableDatabase.rawQuery("""SELECT id,name,game_note,graphics_driver,
            resolution_scale,performance_mode,fan_mode,cluster_tune_mode
            FROM test_profiles ORDER BY name COLLATE NOCASE""",null).use {c->
            buildList {while(c.moveToNext())add(decodeProfile(c))}
        }
    private fun decodeProfile(c:Cursor)=QuickProfile(
        c.getLong(0),c.getString(1),
        if(c.isNull(2))null else c.getString(2),
        if(c.isNull(3))null else c.getString(3),
        if(c.isNull(4))null else c.getString(4),
        if(c.isNull(5))null else c.getString(5),
        if(c.isNull(6))null else c.getString(6),
        if(c.isNull(7))null else c.getString(7))
    private fun profileById(db:SQLiteDatabase,id:Long):QuickProfile? {
        if(id<=0L)return null
        return db.rawQuery("""SELECT id,name,game_note,graphics_driver,
            resolution_scale,performance_mode,fan_mode,cluster_tune_mode
            FROM test_profiles WHERE id=? LIMIT 1""",arrayOf(id.toString())).use {c->
            if(c.moveToFirst())decodeProfile(c)else null
        }
    }
    private fun activeProfileIn(db:SQLiteDatabase):QuickProfile? {
        val id=db.rawQuery(
            "SELECT active_profile_id FROM recording_settings WHERE id=1",null
        ).use { c ->if(c.moveToFirst())c.getLong(0)else 0L }
        return profileById(db,id)
    }
    @Synchronized fun activeProfileId():Long =
        readableDatabase.rawQuery(
            "SELECT active_profile_id FROM recording_settings WHERE id=1",null
        ).use { c ->if(c.moveToFirst())c.getLong(0)else 0L }

    /** Reject stale/deleted selection instead of silently choosing another.
     * This database row is shared by UI, tiles and the monitor process.
     */
    @Synchronized fun selectActiveProfile(id:Long):Boolean {
        if(id<0L)return false
        val db=writableDatabase
        db.beginTransaction()
        return try {
            if(id>0L && profileById(db,id)==null)false
            else {
                val saved=db.update("recording_settings",ContentValues().apply {
                    put("active_profile_id",id)
                },"id=1",null)==1
                if(saved)db.setTransactionSuccessful()
                saved
            }
        }finally { db.endTransaction() }
    }
    @Synchronized fun saveQuickProfile(
        id:Long=0L,name:String,gameNote:String?,graphicsDriver:String?,
        resolutionScale:String?,performanceMode:String?,fanMode:String?,
        clusterTuneMode:String?
    ):Long {
        val clean=cleanLabel(name,48) ?:error("Profile name is required (up to 48 characters)")
        require((performanceMode==null || performanceMode in performanceModes) &&
            (fanMode==null || fanMode in fanModes) &&
            (clusterTuneMode==null || clusterTuneMode in clusterModes)) {
            "Unknown manually entered test condition"
        }
        val driver=SessionMetadata.driver(graphicsDriver)
        val scale=SessionMetadata.resolution(resolutionScale)
        require((graphicsDriver==null || driver!=null) &&
            (resolutionScale==null || scale!=null)){"Invalid driver or resolution"}
        val values=ContentValues().apply {
            put("name",clean)
            put("game_note",SessionMetadata.note(gameNote))
            put("graphics_driver",driver);put("resolution_scale",scale)
            put("performance_mode",performanceMode);put("fan_mode",fanMode)
            put("cluster_tune_mode",clusterTuneMode)
        }
        val db=writableDatabase
        if(id>0L) {
            check(db.update("test_profiles",values,"id=?",
                arrayOf(id.toString()))==1){"Profile no longer exists"}
            return id
        }
        return db.insertOrThrow("test_profiles",null,values)
    }
    @Synchronized fun deleteQuickProfile(id:Long):Boolean {
        if(id<=0L)return false
        val db=writableDatabase
        db.beginTransaction()
        return try {
            val removed=db.delete("test_profiles","id=?",
                arrayOf(id.toString()))==1
            if(removed) {
                db.execSQL("""UPDATE recording_settings SET active_profile_id=0
                    WHERE id=1 AND active_profile_id=?""",arrayOf(id))
                db.setTransactionSuccessful()
            }
            removed
        }finally{db.endTransaction()}
    }
    /** Atomic, manual metadata application to completed sessions only.
     * Presets may be renamed/deleted without rewriting previously saved tags.
     */
    @Synchronized fun applyQuickProfile(sessionId:Long,profileId:Long):Boolean {
        if(sessionId<=0L)return false
        val profile=profileById(readableDatabase,profileId) ?:return false
        val values=ContentValues().apply {
            put("profile_name",profile.name)
            put("graphics_driver",profile.graphicsDriver)
            put("resolution_scale",profile.resolutionScale)
            put("performance_mode",profile.performanceMode)
            put("fan_mode",profile.fanMode)
            put("cluster_tune_mode",profile.clusterTuneMode)
            if(profile.gameNote!=null)put("game_note",profile.gameNote)
        }
        return writableDatabase.update("sessions",values,
            "id=? AND status!='RECORDING'",arrayOf(sessionId.toString()))==1
    }

    /** User-controlled associations are non-destructive; never merge raw data. */
    @Synchronized fun links():List<Link> =
        readableDatabase.rawQuery("""
            SELECT l.id,l.first_id,l.second_id,l.created_at
            FROM session_links l
            INNER JOIN sessions a ON a.id=l.first_id
            INNER JOIN sessions b ON b.id=l.second_id
            ORDER BY l.created_at DESC
        """.trimIndent(),null).use { cursor ->
            buildList {
                while(cursor.moveToNext())add(Link(
                    cursor.getLong(0),cursor.getLong(1),
                    cursor.getLong(2),cursor.getLong(3)))
            }
        }

    @Synchronized fun linkSessions(a:Long,b:Long):Long {
        require(a>0 && b>0 && a!=b){"Select two distinct recordings"}
        val first=minOf(a,b);val second=maxOf(a,b)
        val db=writableDatabase
        db.rawQuery("SELECT COUNT(*) FROM sessions WHERE id IN (?,?)",
            arrayOf(first.toString(),second.toString())).use {
            require(it.moveToFirst() && it.getInt(0)==2){"Recording no longer exists"}
        }
        db.insertWithOnConflict("session_links",null,ContentValues().apply {
            put("first_id",first);put("second_id",second)
            put("created_at",System.currentTimeMillis())
        },SQLiteDatabase.CONFLICT_IGNORE)
        return db.rawQuery(
            "SELECT id FROM session_links WHERE first_id=? AND second_id=?",
            arrayOf(first.toString(),second.toString())
        ).use { c -> check(c.moveToFirst());c.getLong(0) }
    }

    @Synchronized fun unlinkSessions(a:Long,b:Long):Boolean {
        val first=minOf(a,b);val second=maxOf(a,b)
        return writableDatabase.delete("session_links",
            "first_id=? AND second_id=?",
            arrayOf(first.toString(),second.toString()))>0
    }

    /** Automatically numbered session title; game/track is optional metadata. */
    @Synchronized fun begin(label:String,config:String,time:Long):Long {
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE sessions SET status='INTERRUPTED', ended_at=? "+
                "WHERE status='RECORDING'",arrayOf(time))
            val id=db.insertOrThrow("sessions",null,ContentValues().apply {
                put("started_at",time)
                put("label","Session")
                put("game_note",SessionMetadata.note(label).takeIf {
                    it!=null && it!="Gameplay" && !it.startsWith("Session ")
                })
                put("configuration",when(config) {
                    "Stock","Underclocking","Custom" -> config
                    else -> "Custom"
                })
                put("status","RECORDING")
                put("device_manufacturer",cleanLabel(Build.MANUFACTURER,64))
                put("device_model",cleanLabel(Build.MODEL,64))
                put("device_code",cleanLabel(Build.DEVICE,64))
                put("android_api",Build.VERSION.SDK_INT)
                val preset=activeProfileIn(db)
                if(preset!=null) {
                    put("profile_name",preset.name)
                    put("graphics_driver",preset.graphicsDriver)
                    put("resolution_scale",preset.resolutionScale)
                    put("performance_mode",preset.performanceMode)
                    put("fan_mode",preset.fanMode)
                    put("cluster_tune_mode",preset.clusterTuneMode)
                    if(preset.gameNote!=null)put("game_note",preset.gameNote)
                }
            })
            check(id>0)
            db.update("sessions",ContentValues().apply {
                put("label","Session $id")
            },"id=?",arrayOf(id.toString()))
            db.setTransactionSuccessful()
            return id
        } finally {db.endTransaction()}
    }

    @Synchronized fun add(id: Long, s: Snapshot) {
        writableDatabase.insertOrThrow("samples", null, ContentValues().apply {
            put("session_id", id); put("time_ms", s.timeMs)
            put("cpu_temp", s.cpuTempC); put("gpu_temp", s.gpuTempC)
            put("battery_temp", s.batteryTempC); put("battery_pct", s.batteryPercent)
            put("cpu_pct", s.cpuPercent); put("busiest_pct", s.busiestPercent)
            put("busiest_core", s.busiestCore); put("gpu_pct", s.gpuPercent)
            put("gpu_mhz", s.gpuMHz); put("ram_gib", s.ramGiB)
            put("cpu_clocks", s.cpuClockSummary); put("app_package", s.appPackage)
            put("battery_net_w", s.signedBatteryWatts)
            put("battery_ua", s.batteryCurrentMicroAmps)
            put("battery_mv", s.batteryVoltageMilliVolts)
            put("battery_source", s.batteryPowerSource)
            put("gpu_source", s.gpuTelemetrySource?.take(128))
            put("battery_status", s.batteryPowerStatus)
            put("sf_layer_fps", s.surfaceFps)
            put("sf_frame_ms", s.surfaceFrameMs)
            put("sf_status", s.surfaceFpsStatus)
            put("observed_cpu_limits", s.observedCpuLimits)
            put("thermal_status", s.thermalStatus)
            put("thermal_headroom", s.thermalHeadroom)
            put("swap_used_gib", s.swapUsedGiB)
            put("mem_psi_some10", s.memoryPsiSomeAvg10)
            put("sample_duration_ms", s.sampleDurationMs)
            put("sample_elapsed_ms", s.sampleElapsedMs)
        })
    }

    @Synchronized fun finish(id: Long, time: Long, status: String):Boolean =
        writableDatabase.update("sessions", ContentValues().apply {
            put("ended_at", time); put("status", status)
        }, "id=?", arrayOf(id.toString()))==1

    /** Total range is read from persisted samples, never from guessed game times.
     * This metadata query is bounded and does not load raw points into RAM.
     */
    data class ReportBounds(val count:Long,val firstMs:Long,val lastMs:Long)
    @Synchronized fun reportBounds(id:Long):ReportBounds? {
        require(id>0L) { "Invalid session ID" }
        return readableDatabase.rawQuery(
            "SELECT COUNT(*),MIN(time_ms),MAX(time_ms) FROM samples WHERE session_id=?",
            arrayOf(id.toString())).use { c ->
            if(!c.moveToFirst() || c.isNull(1) || c.isNull(2))null
            else ReportBounds(c.getLong(0),c.getLong(1),c.getLong(2))
        }
    }

    /** Median of early valid sample intervals; no hardcoded 2s/5s
     * assumption. Only 160 timestamp scalars are loaded.
     */
    @Synchronized fun reportCadenceMs(id:Long):Long {
        val intervals=ArrayList<Long>(159)
        var previous:Long?=null
        readableDatabase.rawQuery(
            "SELECT time_ms FROM samples WHERE session_id=? "+
                "ORDER BY time_ms ASC LIMIT 160",
            arrayOf(id.toString())).use{c->
            while(c.moveToNext()) {
                val now=c.getLong(0)
                val step=previous?.let{now-it}
                if(step!=null && step in 500L..30_000L)intervals+=step
                previous=now
            }
        }
        if(intervals.isEmpty())return 5_000L
        intervals.sort()
        return intervals[intervals.size/2]
    }

    /** Full chronological streaming read for DUKO reports. The standard
     * 12k-point UI preview remains unchanged. Only one cursor row and
     * temporary Point are retained per callback. Caller runs on IO.
     * A hard safety cap makes unusually large exports explicit rather
     * than silently truncating a recording.
     */
    @Synchronized fun forEachReportPoint(id:Long,onPoint:(Point)->Unit):Long =
        forEachReportPoint(id,null,onPoint)

    /** Reuse already validated COUNT/MIN/MAX from a report loader. Avoid
     * a second full index count when scanning five 250k-row recordings.
     * The callback's exact visited-row count is still verified against
     * that earlier snapshot, so mutation cannot silently truncate output.
     */
    @Synchronized fun forEachReportPoint(
        id:Long,expectedBounds:ReportBounds?,onPoint:(Point)->Unit
    ):Long {
        val bounds=expectedBounds ?: reportBounds(id)
            ?:error("Recording contains no samples")
        require(bounds.count in 2..250_000) {
            "DUKO Report supports 2–250,000 samples. Recording has "+
                bounds.count+"; no partial report generated"
        }
        val projection="""SELECT time_ms,cpu_temp,gpu_temp,cpu_pct,gpu_pct,ram_gib,
            battery_net_w,sf_layer_fps,app_package,
            observed_cpu_limits,busiest_pct,battery_status,busiest_core,
            battery_temp,battery_pct,cpu_clocks,gpu_mhz,
            thermal_status,thermal_headroom,swap_used_gib,mem_psi_some10,
            sample_duration_ms,sample_elapsed_ms,battery_source,gpu_source
            FROM samples WHERE session_id=? ORDER BY time_ms ASC"""
        var read=0L
        readableDatabase.rawQuery(projection,arrayOf(id.toString())).use{ c ->
            fun value(i:Int):Double?=if(c.isNull(i))null else c.getDouble(i)
            while(c.moveToNext()) {
                onPoint(Point(c.getLong(0),value(1),value(2),
                    value(3),value(4),value(5),value(6),value(7),
                    if(c.isNull(8))null else c.getString(8),
                    if(c.isNull(9))null else c.getString(9),
                    value(10),if(c.isNull(11))null else c.getString(11),
                    if(c.isNull(12))null else c.getString(12),value(13),
                    if(c.isNull(14))null else c.getInt(14),
                    if(c.isNull(15))null else c.getString(15),value(16),
                    if(c.isNull(17))null else c.getInt(17),
                    value(18),value(19),value(20),value(21),
                    if(c.isNull(22))null else c.getLong(22),
                    if(c.isNull(23))null else c.getString(23),
                    if(c.isNull(24))null else c.getString(24)))
                read++
            }
        }
        require(read==bounds.count) {
            "Recording changed while its report was generated; export cancelled"
        }
        return read
    }

    /**
     * Full-session report optimized cursor. The callback sees the same ONE
     * mutable ReportScratch every time; never retain it beyond the callback.
     * It selects only 15 report-relevant SQLite fields, and never constructs
     * a large immutable Point per sample. Other APIs retain Point semantics.
     */
    @Synchronized fun forEachReportSample(
        id:Long,bounds:ReportBounds,onSample:(ReportScratch)->Unit
    ):Long {
        require(bounds.count in 6L..250_000L) {
            "DUKO Report expects 6–250,000 saved samples"
        }
        val columns="""SELECT time_ms,cpu_temp,gpu_temp,cpu_pct,gpu_pct,
            battery_net_w,observed_cpu_limits,busiest_pct,battery_status,
            battery_temp,gpu_mhz,thermal_status,mem_psi_some10,
            battery_source,gpu_source
            FROM samples WHERE session_id=? ORDER BY time_ms ASC"""
        val scratch=ReportScratch()
        var read=0L
        readableDatabase.rawQuery(columns,arrayOf(id.toString())).use {c->
            fun dbl(i:Int):Double?=if(c.isNull(i))null else c.getDouble(i)
            fun str(i:Int):String?=if(c.isNull(i))null else c.getString(i)
            while(c.moveToNext()) {
                scratch.timeMs=c.getLong(0)
                scratch.cpu=dbl(1);scratch.gpu=dbl(2)
                scratch.cpuLoad=dbl(3);scratch.gpuLoad=dbl(4)
                scratch.batteryWatts=dbl(5)
                scratch.observedCpuLimits=str(6)
                scratch.busiestCorePct=dbl(7)
                scratch.batteryPowerStatus=str(8)
                scratch.batteryTemp=dbl(9)
                scratch.gpuMHz=dbl(10)
                scratch.thermalStatus=if(c.isNull(11))null else c.getInt(11)
                scratch.memoryPsiSomeAvg10=dbl(12)
                scratch.batteryPowerSource=str(13)
                scratch.gpuTelemetrySource=str(14)
                onSample(scratch)
                read++
            }
        }
        check(read==bounds.count) {
            "Report dataset changed during export; refusing truncated output"
        }
        return read
    }

    /** Count already persisted points when recovering the same SQLite ID. */
    @Synchronized fun storedSampleCount(id:Long):Long =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM samples WHERE session_id=?",
            arrayOf(id.toString())
        ).use { c -> if(c.moveToFirst())c.getLong(0) else 0L }

    /** Preserve the original elapsed session timer across an LMK restart. */
    @Synchronized fun originalStartMs(id:Long):Long? =
        readableDatabase.rawQuery("SELECT started_at FROM sessions WHERE id=?",
            arrayOf(id.toString())
        ).use { c -> if(c.moveToFirst() && !c.isNull(0))c.getLong(0) else null }

    /** Verify the old ID is an unfinished user-started recording. */
    @Synchronized fun canResume(id:Long):Boolean =
        readableDatabase.rawQuery(
            "SELECT status FROM sessions WHERE id=?",
            arrayOf(id.toString())
        ).use { c -> c.moveToFirst() && c.getString(0)=="RECORDING" }

    /** Reconcile a previous process death without deleting its persisted samples. */
    @Synchronized fun recoverInterruptedSessions(time: Long): Int {
        val values = ContentValues().apply {
            put("status", "INTERRUPTED_RECOVERED")
            put("ended_at", time)
        }
        return writableDatabase.update("sessions",values,"status='RECORDING'",null)
    }

    // Read exactly one selected session without first constructing 200 full
    // summaries. Shared projection keeps summary/export metadata consistent.
    private val sessionProjection="""
        SELECT s.id,s.started_at,s.ended_at,s.label,s.configuration,s.status,
        (SELECT COUNT(*) FROM samples x WHERE x.session_id=s.id),
        s.game_note,s.graphics_driver,s.resolution_scale,
        s.performance_mode,s.fan_mode,s.cluster_tune_mode,s.profile_name,
        EXISTS(SELECT 1 FROM samples x WHERE x.session_id=s.id AND
            (x.battery_status LIKE 'CHARGING_OR_EXTERNAL_POWER%'
             OR x.battery_status LIKE 'FULL%' OR x.battery_status LIKE 'PLUGGED%')),
        EXISTS(SELECT 1 FROM samples x WHERE x.session_id=s.id AND
            x.battery_status LIKE 'DISCHARGING%'),
        s.device_manufacturer,s.device_model,s.device_code,s.android_api
        FROM sessions s
    """.trimIndent()

    private fun decodeSession(c:Cursor)=Session(
        c.getLong(0),c.getLong(1),
        if(c.isNull(2))null else c.getLong(2),
        c.getString(3)?:"",c.getString(4)?:"",
        c.getString(5)?:"UNKNOWN",c.getInt(6),
        if(c.isNull(7))null else c.getString(7),
        if(c.isNull(8))null else c.getString(8),
        if(c.isNull(9))null else c.getString(9),
        c.getInt(14)!=0,c.getInt(15)!=0,
        if(c.isNull(10))null else c.getString(10),
        if(c.isNull(11))null else c.getString(11),
        if(c.isNull(12))null else c.getString(12),
        if(c.isNull(13))null else c.getString(13),
        if(c.isNull(16))null else c.getString(16),
        if(c.isNull(17))null else c.getString(17),
        if(c.isNull(18))null else c.getString(18),
        if(c.isNull(19))null else c.getInt(19)
    )

    @Synchronized fun list(limit:Int=80):List<Session> =
        readableDatabase.rawQuery(sessionProjection+
            " ORDER BY s.id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1,200).toString())).use { c ->
            buildList { while(c.moveToNext())add(decodeSession(c)) }
        }

    @Synchronized fun sessionById(id:Long):Session? {
        if(id<=0L)return null
        return readableDatabase.rawQuery(sessionProjection+
            " WHERE s.id=? LIMIT 1",arrayOf(id.toString())).use { c ->
            if(c.moveToFirst())decodeSession(c) else null
        }
    }

    /** Explicit manual context. Updating a label cannot modify source samples. */
    @Synchronized fun updateGameNote(id:Long,note:String?):Boolean {
        if(id<=0L)return false
        return writableDatabase.update("sessions",ContentValues().apply {
            put("game_note",SessionMetadata.note(note))
        },"id=? AND status!='RECORDING'",arrayOf(id.toString()))==1
    }

    @Synchronized fun updateConfiguration(id:Long,config:String):Boolean {
        if(id<=0L || config !in setOf("Stock","Underclocking","Custom"))
            return false
        return writableDatabase.update("sessions",ContentValues().apply {
            put("configuration",config)
        },"id=? AND status!='RECORDING'",arrayOf(id.toString()))==1
    }

    /** Atomic removal of one completed/interrupted session and both A/B
     * associations. Active/unreconciled RECORDING rows are NEVER deleted.
     * An overlapping monitor write is serialized by SQLite's write transaction.
     */
    @Synchronized fun deleteSession(id:Long):Boolean {
        if(id<=0L)return false
        // A process-death recovery lease may still refer to a recording that
        // appears completed/interrupted in a stale UI. Refuse destruction.
        if(RecorderLease.recover(leaseContext)?.let{it.recording && it.id==id}==true)
            return false
        val db=writableDatabase
        db.beginTransaction()
        try {
            if(RecorderLease.recover(leaseContext)?.let{
                    it.recording && it.id==id}==true)return false
            val deletable=db.rawQuery(
                "SELECT status FROM sessions WHERE id=? LIMIT 1",
                arrayOf(id.toString())).use { c ->
                c.moveToFirst() && c.getString(0)!="RECORDING"
            }
            if(!deletable)return false
            val arg=arrayOf(id.toString())
            db.delete("session_links","first_id=? OR second_id=?",arrayOf(id.toString(),id.toString()))
            db.delete("samples","session_id=?",arg)
            val deleted=db.delete("sessions","id=? AND status!='RECORDING'",arg)==1
            if(deleted)db.setTransactionSuccessful()
            return deleted
        }finally{
            db.endTransaction()
        }
    }

    fun points(id: Long, max: Int = 12000): List<Point> = readableDatabase.rawQuery(
        """SELECT time_ms,cpu_temp,gpu_temp,cpu_pct,gpu_pct,ram_gib,
        battery_net_w,sf_layer_fps,app_package,
        observed_cpu_limits,busiest_pct,battery_status,busiest_core,
        battery_temp,battery_pct,cpu_clocks,gpu_mhz,
        thermal_status,thermal_headroom,swap_used_gib,mem_psi_some10,
        sample_duration_ms,sample_elapsed_ms,battery_source,gpu_source
        FROM samples WHERE session_id=? ORDER BY time_ms ASC LIMIT ?""",
        arrayOf(id.toString(), max.coerceIn(1,12000).toString())
    ).use { c ->
        fun value(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
        buildList {
            while (c.moveToNext()) add(Point(c.getLong(0),value(1),value(2),
                value(3),value(4),value(5),value(6),value(7),
                if (c.isNull(8)) null else c.getString(8),
                if (c.isNull(9)) null else c.getString(9),
                value(10), if (c.isNull(11)) null else c.getString(11),
                if(c.isNull(12))null else c.getString(12),value(13),
                if(c.isNull(14))null else c.getInt(14),
                if(c.isNull(15))null else c.getString(15),value(16),
                if(c.isNull(17)) null else c.getInt(17),
                value(18),value(19),value(20),value(21),
                if(c.isNull(22)) null else c.getLong(22),
                if(c.isNull(23)) null else c.getString(23),
                if(c.isNull(24)) null else c.getString(24)))
        }
    }

    /**
     * Streaming, bounded and atomic SAF import. Unlike the on-screen analysis
     * preview, this no longer stops at the 12,000-sample display limit.
     * CSV parsing is record-bounded and each row goes straight to SQLite;
     * partial imports are rolled back on parse, limit or storage failure.
     */
    companion object {
        const val MAX_CSV_IMPORT_SAMPLES = 100_000
    }
    @Synchronized fun importCsv(reader: java.io.Reader): Long {
        val csv=CsvRecordReader(reader)
        val header=csv.next() ?: error("CSV file is empty")
        val columns=CsvSessionColumns(header)
        val db=writableDatabase
        var sessionId=0L
        var rowCount=0
        var lastTime=0L
        db.beginTransaction()
        try {
            var firstTime=0L
            while(true) {
                val row=csv.next() ?: break
                if(row.size!=columns.width) error("CSV row has unexpected column count")
                if(rowCount>=MAX_CSV_IMPORT_SAMPLES)
                    error("CSV exceeds the explicit "+MAX_CSV_IMPORT_SAMPLES+
                        "-sample import safety limit; nothing was imported")
                val now=columns.time(row)
                TelemetrySanity.requireIncreasing(
                    if(rowCount==0)null else lastTime,now)
                if(rowCount==0) {
                    firstTime=now
                    val label=columns.field(row,"game_label")
                        ?.filter { it.code>=32 && it.code!=127 && it.code !in 0x80..0x9F }
                        ?.take(60) ?: "Imported session"
                    val config=columns.field(row,"configuration")
                        ?.filter { it.code>=32 && it.code!=127 && it.code !in 0x80..0x9F }
                        ?.take(38) ?: "Unknown profile"
                    sessionId=db.insertOrThrow("sessions",null,ContentValues().apply {
                        put("started_at",firstTime)
                        put("label","Imported session")
                        put("game_note",label)
                        put("graphics_driver",SessionMetadata.driver(
                            columns.field(row,"graphics_driver")))
                        put("resolution_scale",SessionMetadata.resolution(
                            columns.field(row,"resolution_scale")))
                        put("performance_mode",columns.field(row,"performance_mode")
                            ?.takeIf{it in performanceModes})
                        put("fan_mode",columns.field(row,"fan_mode")
                            ?.takeIf{it in fanModes})
                        put("cluster_tune_mode",columns.field(row,"cluster_tune_mode")
                            ?.takeIf{it in clusterModes})
                        put("profile_name",cleanLabel(columns.field(row,"profile_name")))
                        put("device_manufacturer",cleanLabel(
                            columns.field(row,"device_manufacturer"),64))
                        put("device_model",cleanLabel(
                            columns.field(row,"device_model"),64))
                        put("device_code",cleanLabel(
                            columns.field(row,"device_code"),64))
                        put("android_api",columns.field(row,"android_api")
                            ?.toIntOrNull()?.takeIf{it in 1..50})
                        put("configuration",config)
                        put("status",ImportedSessionStatus.store(
                            columns.field(row,"status")))
                        put("ended_at",firstTime)
                    })
                }
                val values=ContentValues().apply {
                    put("session_id",sessionId)
                    put("time_ms",now)
                    fun decimal(column:String,key:String) {
                        val source=columns.field(row,key) ?: return
                        val v=source.toDoubleOrNull()
                        require(v!=null && TelemetrySanity.valid(key,v)) {
                            "Invalid $key reading on CSV row ${rowCount+2}"
                        }
                        put(column,v)
                    }
                    fun count(column:String,key:String) {
                        val source=columns.field(row,key) ?: return
                        val v=source.toLongOrNull()
                        require(v!=null && v in 0L..100L &&
                            TelemetrySanity.valid(key,v.toDouble())) {
                            "Invalid $key reading on CSV row ${rowCount+2}"
                        }
                        put(column,v.toInt())
                    }
                    fun text(column:String,key:String) {
                        val max=when(key) {
                            "android_app_package"->256
                            "cpu_clocks","observed_cpu_limits"->256
                            else->128
                        }
                        columns.field(row,key)?.let { raw ->
                            // Preserve international scripts; reject control
                            // characters which could spoof log/UI boundaries.
                            val cleaned=raw.asSequence().filter { ch ->
                                ch.code>=32 && ch.code!=127 &&
                                    ch.code !in 0x80..0x9F
                            }.take(max).joinToString("")
                            put(column,cleaned)
                        }
                    }
                    decimal("cpu_temp","cpu_temp_c")
                    decimal("gpu_temp","gpu_temp_c")
                    decimal("battery_temp","battery_temp_c")
                    count("battery_pct","battery_pct")
                    count("cpu_pct","cpu_load_pct")
                    count("busiest_pct","busiest_core_pct")
                    text("busiest_core","busiest_core")
                    count("gpu_pct","gpu_load_pct")
                    decimal("gpu_mhz","gpu_mhz")
                    decimal("ram_gib","system_ram_used_gib")
                    text("cpu_clocks","cpu_clocks")
                    text("app_package","android_app_package")
                    decimal("battery_net_w","battery_net_w")
                    columns.integer(row,"battery_current_ua")?.let{put("battery_ua",it)}
                    columns.integer(row,"battery_voltage_mv")?.let{put("battery_mv",it)}
                    text("battery_source","battery_power_source")
                    text("gpu_source","gpu_telemetry_source")
                    text("battery_status","battery_power_status")
                    decimal("sf_layer_fps","sf_layer_fps")
                    decimal("sf_frame_ms","sf_frame_time_ms")
                    text("sf_status","sf_status")
                    text("observed_cpu_limits","observed_cpu_limits")
                    columns.field(row,"thermal_status")?.toIntOrNull()
                        ?.takeIf{it in 0..6}?.let{put("thermal_status",it)}
                    decimal("thermal_headroom","thermal_headroom")
                    decimal("swap_used_gib","swap_used_gib")
                    decimal("mem_psi_some10","mem_psi_some10")
                    decimal("sample_duration_ms","sample_duration_ms")
                    columns.field(row,"sample_elapsed_ms")?.toLongOrNull()
                        ?.takeIf{it>=0L}?.let{put("sample_elapsed_ms",it)}
                }
                db.insertOrThrow("samples",null,values)
                rowCount++
                lastTime=now
            }
            require(rowCount>0) { "CSV contains no samples" }
            db.update("sessions",ContentValues().apply {
                put("ended_at",lastTime)
                put("label","Session "+sessionId)
            },"id=?",arrayOf(sessionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return sessionId
    }

    /** Caller opens the Storage Access Framework URI; avoid Binder-sized EXTRA_TEXT. */
    fun exportCsv(id: Long, writer: Writer) =
        exportCsvInternal(id,writer,null,null)

    /** Importable CSV preserving the exact original schema and timestamps.
     * The selection is inclusive and only pre-existing database rows are read.
     */
    fun exportCsvRange(id:Long,startMs:Long,endMs:Long,writer:Writer) {
        require(startMs<endMs) { "Selection must have a positive duration" }
        exportCsvInternal(id,writer,startMs,endMs)
    }

    private fun exportCsvInternal(id:Long,writer:Writer,
        startMs:Long?,endMs:Long?) {
        val session = sessionById(id)
            ?: error("Session was not found")
        val headers = listOf("session_id","game_label","configuration","status",
            "sample_epoch_ms","cpu_temp_c","gpu_temp_c","battery_temp_c",
            "battery_pct","cpu_load_pct","busiest_core_pct","busiest_core",
            "gpu_load_pct","gpu_mhz","system_ram_used_gib","cpu_clocks","android_app_package",
            "battery_net_w","battery_current_ua","battery_voltage_mv",
            "battery_power_source","battery_power_status",
            "sf_layer_fps","sf_frame_time_ms","sf_status","observed_cpu_limits",
            "graphics_driver","resolution_scale",
            "thermal_status","thermal_headroom","swap_used_gib","mem_psi_some10",
            "sample_duration_ms","sample_elapsed_ms",
            "performance_mode","fan_mode","cluster_tune_mode","profile_name",
            "device_manufacturer","device_model","device_code","android_api",
            "gpu_telemetry_source")
        writer.append(headers.joinToString(",")).append('\n')
        readableDatabase.rawQuery(
            """SELECT time_ms,cpu_temp,gpu_temp,battery_temp,battery_pct,cpu_pct,
            busiest_pct,busiest_core,gpu_pct,gpu_mhz,ram_gib,cpu_clocks,app_package,
            battery_net_w,battery_ua,battery_mv,battery_source,battery_status,
            sf_layer_fps,sf_frame_ms,sf_status,observed_cpu_limits,
            thermal_status,thermal_headroom,swap_used_gib,mem_psi_some10,
            sample_duration_ms,sample_elapsed_ms,gpu_source
            FROM samples WHERE session_id=?
            """ + if(startMs!=null && endMs!=null)
                "AND time_ms>=? AND time_ms<=? ORDER BY time_ms LIMIT 12000"
            else "ORDER BY time_ms",
            if(startMs!=null && endMs!=null)
                arrayOf(id.toString(),startMs.toString(),endMs.toString())
            else arrayOf(id.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val values = listOf(id.toString(),session.note?.takeIf{it.isNotBlank()} ?: session.label,session.configuration,session.status) +
                    (0 until 22).map { if (c.isNull(it)) "" else c.getString(it) ?: "" } +
                    listOf(session.graphicsDriver.orEmpty(),session.resolutionScale.orEmpty()) +
                    (22 until c.columnCount-1).map {
                        if (c.isNull(it)) "" else c.getString(it) ?: ""
                    } +
                    listOf(session.performanceMode.orEmpty(),session.fanMode.orEmpty(),
                        session.clusterTuneMode.orEmpty(),session.profileName.orEmpty(),
                        session.deviceManufacturer.orEmpty(),session.deviceModel.orEmpty(),
                        session.deviceCode.orEmpty(),session.androidApi?.toString().orEmpty(),
                        if(c.isNull(c.columnCount-1))"" else c.getString(c.columnCount-1) ?: "")
                writer.append(values.joinToString(",") { Csv.escape(it) }).append('\n')
            }
        }
        writer.flush()
    }
}
