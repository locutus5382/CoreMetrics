package com.locutus5382.coremetrics.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.locutus5382.coremetrics.telemetry.Snapshot
import java.io.Writer

/**
 * :monitor owns writes, UI reads through WAL. Data never leaves this database
 * without a user-selected Android document URI.
 */
class SessionStore(context: Context) : SQLiteOpenHelper(context, "coremetrics.db", null, 2) {
    data class Session(val id: Long, val startedAt: Long, val endedAt: Long?,
        val label: String, val configuration: String, val status: String, val count: Int)
    data class Point(val timeMs: Long, val cpu: Double?, val gpu: Double?,
        val cpuLoad: Double?, val gpuLoad: Double?, val ram: Double?,
        val batteryWatts: Double?, val sfFps: Double?, val appPackage: String?,
        val observedCpuLimits: String? = null, val busiestCorePct: Double? = null,
        val batteryPowerStatus: String? = null)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            started_at INTEGER NOT NULL, ended_at INTEGER,
            label TEXT NOT NULL, configuration TEXT NOT NULL,
            status TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE samples (
            session_id INTEGER NOT NULL, time_ms INTEGER NOT NULL,
            cpu_temp REAL, gpu_temp REAL, battery_temp REAL, battery_pct INTEGER,
            cpu_pct INTEGER, busiest_pct INTEGER, busiest_core TEXT,
            gpu_pct INTEGER, gpu_mhz REAL, ram_gib REAL,
            cpu_clocks TEXT, app_package TEXT,
            battery_net_w REAL, battery_ua INTEGER, battery_mv INTEGER,
            battery_source TEXT, battery_status TEXT,
            sf_layer_fps REAL, sf_frame_ms REAL, sf_status TEXT,
            observed_cpu_limits TEXT)""")
        db.execSQL("CREATE INDEX samples_by_time ON samples(session_id,time_ms)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            for ((name, type) in listOf(
                "battery_net_w" to "REAL", "battery_ua" to "INTEGER",
                "battery_mv" to "INTEGER", "battery_source" to "TEXT",
                "battery_status" to "TEXT", "sf_layer_fps" to "REAL",
                "sf_frame_ms" to "REAL", "sf_status" to "TEXT",
                "observed_cpu_limits" to "TEXT"
            )) db.execSQL("ALTER TABLE samples ADD COLUMN " + name + " " + type)
        } else error("Unsupported schema migration: " + oldVersion + " → " + newVersion)
    }

    @Synchronized fun begin(label: String, config: String, time: Long): Long {
        val db = writableDatabase
        db.execSQL("UPDATE sessions SET status='INTERRUPTED', ended_at=? WHERE status='RECORDING'",
            arrayOf(time))
        return db.insertOrThrow("sessions", null, ContentValues().apply {
            put("started_at", time)
            put("label", label.take(64))
            put("configuration", config.take(40))
            put("status", "RECORDING")
        })
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
            put("battery_status", s.batteryPowerStatus)
            put("sf_layer_fps", s.surfaceFps)
            put("sf_frame_ms", s.surfaceFrameMs)
            put("sf_status", s.surfaceFpsStatus)
            put("observed_cpu_limits", s.observedCpuLimits)
        })
    }

    @Synchronized fun finish(id: Long, time: Long, status: String) {
        writableDatabase.update("sessions", ContentValues().apply {
            put("ended_at", time); put("status", status)
        }, "id=?", arrayOf(id.toString()))
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

    fun list(limit: Int = 80): List<Session> = readableDatabase.rawQuery("""
        SELECT s.id,s.started_at,s.ended_at,s.label,s.configuration,s.status,
        (SELECT COUNT(*) FROM samples x WHERE x.session_id=s.id)
        FROM sessions s ORDER BY s.id DESC LIMIT ?
    """.trimIndent(), arrayOf(limit.coerceIn(1,200).toString())).use { c ->
        buildList {
            while (c.moveToNext()) add(Session(
                c.getLong(0),c.getLong(1),
                if (c.isNull(2)) null else c.getLong(2),
                c.getString(3) ?: "",c.getString(4) ?: "",
                c.getString(5) ?: "UNKNOWN",c.getInt(6)
            ))
        }
    }

    fun points(id: Long, max: Int = 12000): List<Point> = readableDatabase.rawQuery(
        """SELECT time_ms,cpu_temp,gpu_temp,cpu_pct,gpu_pct,ram_gib,
        battery_net_w,sf_layer_fps,app_package,
        observed_cpu_limits,busiest_pct,battery_status
        FROM samples WHERE session_id=? ORDER BY time_ms ASC LIMIT ?""",
        arrayOf(id.toString(), max.coerceIn(1,12000).toString())
    ).use { c ->
        fun value(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
        buildList {
            while (c.moveToNext()) add(Point(c.getLong(0),value(1),value(2),
                value(3),value(4),value(5),value(6),value(7),
                if (c.isNull(8)) null else c.getString(8),
                if (c.isNull(9)) null else c.getString(9),
                value(10), if (c.isNull(11)) null else c.getString(11)))
        }
    }

    /**
     * SAF file import is atomic and never touches an existing recording.
     * Maximum 12k rows; one import becomes a new independently selectable session.
     * The existing schema and recorder process remain unchanged.
     */
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
                if(rowCount>=12_000) error("CSV exceeds 12,000 sample import limit")
                val now=columns.time(row)
                if(rowCount>0 && now<lastTime) error("CSV timestamps out of order")
                if(rowCount==0) {
                    firstTime=now
                    val label=columns.field(row,"game_label")?.take(60) ?: "Imported session"
                    val config=columns.field(row,"configuration")?.take(38) ?: "Unknown profile"
                    sessionId=db.insertOrThrow("sessions",null,ContentValues().apply {
                        put("started_at",firstTime)
                        put("label","CSV · "+label)
                        put("configuration",config)
                        put("status","IMPORTED")
                        put("ended_at",firstTime)
                    })
                }
                val values=ContentValues().apply {
                    put("session_id",sessionId)
                    put("time_ms",now)
                    fun decimal(column:String,key:String) {
                        columns.number(row,key)?.let { put(column,it) }
                    }
                    fun count(column:String,key:String) {
                        columns.integer(row,key)?.let { if(it in Int.MIN_VALUE..Int.MAX_VALUE)
                            put(column,it.toInt()) }
                    }
                    fun text(column:String,key:String) {
                        columns.field(row,key)?.let { put(column,it.take(1024)) }
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
                    text("battery_status","battery_power_status")
                    decimal("sf_layer_fps","sf_layer_fps")
                    decimal("sf_frame_ms","sf_frame_time_ms")
                    text("sf_status","sf_status")
                    text("observed_cpu_limits","observed_cpu_limits")
                }
                db.insertOrThrow("samples",null,values)
                rowCount++
                lastTime=now
            }
            require(rowCount>0) { "CSV contains no samples" }
            db.update("sessions",ContentValues().apply { put("ended_at",lastTime) },
                "id=?", arrayOf(sessionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return sessionId
    }

    /** Caller opens the Storage Access Framework URI; avoid Binder-sized EXTRA_TEXT. */
    fun exportCsv(id: Long, writer: Writer) {
        val session = list(200).firstOrNull { it.id == id }
            ?: error("Session was not found")
        val headers = listOf("session_id","game_label","configuration","status",
            "sample_epoch_ms","cpu_temp_c","gpu_temp_c","battery_temp_c",
            "battery_pct","cpu_load_pct","busiest_core_pct","busiest_core",
            "gpu_load_pct","gpu_mhz","system_ram_used_gib","cpu_clocks","android_app_package",
            "battery_net_w","battery_current_ua","battery_voltage_mv",
            "battery_power_source","battery_power_status",
            "sf_layer_fps","sf_frame_time_ms","sf_status","observed_cpu_limits")
        writer.append(headers.joinToString(",")).append('\n')
        readableDatabase.rawQuery(
            """SELECT time_ms,cpu_temp,gpu_temp,battery_temp,battery_pct,cpu_pct,
            busiest_pct,busiest_core,gpu_pct,gpu_mhz,ram_gib,cpu_clocks,app_package,
            battery_net_w,battery_ua,battery_mv,battery_source,battery_status,
            sf_layer_fps,sf_frame_ms,sf_status,observed_cpu_limits
            FROM samples WHERE session_id=? ORDER BY time_ms""",
            arrayOf(id.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val values = listOf(id.toString(),session.label,session.configuration,session.status) +
                    (0 until c.columnCount).map { if (c.isNull(it)) "" else c.getString(it) ?: "" }
                writer.append(values.joinToString(",") { Csv.escape(it) }).append('\n')
            }
        }
        writer.flush()
    }
}
