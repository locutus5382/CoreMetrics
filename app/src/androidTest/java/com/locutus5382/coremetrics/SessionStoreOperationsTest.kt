package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs only with connectedDebugAndroidTest, never in normal app execution.
 * The ContextWrapper maps the hardcoded helper name to a test-only database,
 * preventing test deletions from touching ordinary CoreMetrics recordings.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreOperationsTest {
    @Test fun migratingVersionFourPreservesBatteryIndexAndAddsV6Fields() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val dbName="coremetrics_v0961_legacy_test.db"
        app.deleteDatabase(dbName)
        app.openOrCreateDatabase(dbName,Context.MODE_PRIVATE,null).use {legacy->
            legacy.execSQL("CREATE TABLE samples(session_id INTEGER, battery_status TEXT)")
            legacy.execSQL("""CREATE TABLE sessions(
                id INTEGER PRIMARY KEY,started_at INTEGER,ended_at INTEGER,
                label TEXT,configuration TEXT,status TEXT,game_note TEXT,
                graphics_driver TEXT,resolution_scale TEXT)""")
            legacy.version=4
        }
        val sandbox=object:ContextWrapper(app) {
            override fun getDatabasePath(name:String):java.io.File =
                app.getDatabasePath(dbName)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory,errorHandler)
        }
        val store=SessionStore(sandbox)
        try {
            val db=store.readableDatabase
            assertEquals(7,db.version)
            val names=mutableSetOf<String>()
            db.rawQuery("PRAGMA index_list(samples)",null).use { cursor->
                while(cursor.moveToNext()) names.add(cursor.getString(1))
            }
            assertTrue(names.contains("samples_by_session_power"))
        }finally{
            store.close()
            app.deleteDatabase(dbName)
        }
    }

    /**
     * A v5 database with existing recorded samples must migrate in-place to
     * v6 without losing rows. Instrumentation-only: CI compiles this test,
     * but a connected emulator/device must execute it before release.
     */
    @Test fun migratingVersionFivePreservesSamplesAndAddsNullableTelemetry() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "coremetrics_v097_migrate_v5_test.db"
        app.deleteDatabase(dbName)
        app.openOrCreateDatabase(dbName,Context.MODE_PRIVATE,null).use { legacy ->
            legacy.execSQL("""CREATE TABLE samples(
                session_id INTEGER NOT NULL,time_ms INTEGER NOT NULL,
                cpu_temp REAL,battery_status TEXT)""")
            legacy.execSQL("""CREATE TABLE sessions(
                id INTEGER PRIMARY KEY,started_at INTEGER,ended_at INTEGER,
                label TEXT,configuration TEXT,status TEXT,game_note TEXT,
                graphics_driver TEXT,resolution_scale TEXT)""")
            legacy.execSQL("""INSERT INTO samples(session_id,time_ms,cpu_temp)
                VALUES(77,123456,48.5)""")
            legacy.version=5
        }
        val sandbox=object:ContextWrapper(app) {
            override fun getDatabasePath(name:String):java.io.File =
                app.getDatabasePath(dbName)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory,errorHandler)
        }
        val store=SessionStore(sandbox)
        try {
            val db=store.readableDatabase
            assertEquals(7,db.version)
            val columns=mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(samples)",null).use { cursor ->
                while(cursor.moveToNext())columns.add(cursor.getString(1))
            }
            assertTrue(columns.containsAll(listOf("thermal_status","thermal_headroom",
                "swap_used_gib","mem_psi_some10","sample_duration_ms","sample_elapsed_ms")))
            db.rawQuery("""SELECT session_id,time_ms,cpu_temp,thermal_status,
                thermal_headroom FROM samples""",null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(77L,c.getLong(0))
                assertEquals(123456L,c.getLong(1))
                assertEquals(48.5,c.getDouble(2),0.00001)
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
                assertFalse(c.moveToNext())
            }
        }finally {
            store.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test fun migratingV6AddsProfileColumnsWithoutTouchingSavedSamples() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val name="coremetrics_v098_v6_to_v7_test.db"
        app.deleteDatabase(name)
        app.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use{legacy->
            legacy.execSQL("""CREATE TABLE sessions(
                id INTEGER PRIMARY KEY,started_at INTEGER,ended_at INTEGER,
                label TEXT,configuration TEXT,status TEXT,game_note TEXT,
                graphics_driver TEXT,resolution_scale TEXT)""")
            legacy.execSQL("""INSERT INTO sessions VALUES(
                9,1000,5000,'Session 9','Stock','COMPLETED',
                'NFS','Custom: T30','Handheld 1x')""")
            legacy.execSQL("CREATE TABLE samples(session_id INTEGER,time_ms INTEGER)")
            legacy.execSQL("INSERT INTO samples VALUES(9,2000)")
            legacy.version=6
        }
        val sandbox=object:ContextWrapper(app) {
            override fun getDatabasePath(nameIgnored:String):java.io.File =
                app.getDatabasePath(name)
            override fun openOrCreateDatabase(nameIgnored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory)
            override fun openOrCreateDatabase(nameIgnored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory,errorHandler)
        }
        val store=SessionStore(sandbox)
        try {
            assertEquals(7,store.readableDatabase.version)
            assertEquals(1L,store.storedSampleCount(9))
            assertEquals("Custom: T30",store.sessionById(9)?.graphicsDriver)
            assertNull(store.sessionById(9)?.fanMode)
            val preset=store.saveQuickProfile(name="R2 Warm",gameNote="NFS",
                graphicsDriver="Custom: Steven R2",resolutionScale="Handheld 1x",
                performanceMode="Performance",fanMode="Smart",clusterTuneMode="Stock")
            assertTrue(store.applyQuickProfile(9,preset))
            assertEquals("Smart",store.sessionById(9)?.fanMode)
            assertTrue(store.deleteQuickProfile(preset))
            assertEquals("R2 Warm",store.sessionById(9)?.profileName)
        }finally{store.close();app.deleteDatabase(name)}
    }

    @Test fun metadataAndDeletionAreAtomicAndActiveRecordingIsProtected() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val dbName="coremetrics_v096_instrumentation_test.db"
        app.deleteDatabase(dbName)
        val sandbox=object:ContextWrapper(app) {
            override fun getDatabasePath(name:String):java.io.File =
                app.getDatabasePath(dbName)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory,errorHandler)
        }
        val store=SessionStore(sandbox)
        try {
            val a=store.begin("Hades","Stock",1000L)
            assertFalse(store.deleteSession(a)) // currently recording
            store.writableDatabase.execSQL(
                "INSERT INTO samples(session_id,time_ms,busiest_pct) VALUES(?,?,?)",
                arrayOf(a,6000L,75))
            store.finish(a,36_000L,"COMPLETED")
            val b=store.begin("Other game","Custom",40_000L)
            store.writableDatabase.execSQL(
                "INSERT INTO samples(session_id,time_ms) VALUES(?,?)",
                arrayOf(b,45_000L))
            store.finish(b,70_000L,"COMPLETED")
            assertTrue(store.linkSessions(a,b)>0L)
            assertTrue(store.updateGameNote(a," Hades · T30 "))
            assertTrue(store.updateGameNote(a,"Hades\n· T30\u0085"))
            assertEquals("Hades· T30",store.sessionById(a)?.note)
            assertFalse(store.updateMetadata(b,"=INVALID","Handheld 1x"))
            assertFalse(store.updateMetadata(b,"Turnip",null))
            assertTrue(store.updateMetadata(a,"Turnip","Handheld 1x"))
            assertEquals("Turnip",store.sessionById(a)?.graphicsDriver)
            assertTrue(store.updateGraphicsDriver(a,"Custom: T30"))
            assertEquals("Custom: T30",store.sessionById(a)?.graphicsDriver)
            assertFalse(store.updateGraphicsDriver(a,"Unsupported driver!"))
            assertEquals("Custom: T30",store.sessionById(a)?.graphicsDriver)
            assertTrue(store.updateResolutionScale(a,"Custom: @200"))
            assertEquals("Custom: @200",store.sessionById(a)?.resolutionScale)
            assertFalse(store.updateResolutionScale(a,"=formula"))
            assertEquals("Custom: @200",store.sessionById(a)?.resolutionScale)
            assertTrue(store.updateConfiguration(a,"Underclocking"))
            val saved=store.sessionById(a)
            assertNotNull(saved)
            assertEquals("Hades· T30",saved!!.note)
            assertEquals("Underclocking",saved.configuration)
            assertEquals(1,saved.count)
            assertFalse(store.updateConfiguration(a,"Unverified"))
            assertFalse(store.deleteSession(-1L))
            assertTrue(store.deleteSession(a))
            assertNull(store.sessionById(a))
            assertFalse(store.links().any{it.firstId==a || it.secondId==a})
            assertEquals(0,store.storedSampleCount(a))
            assertEquals(1,store.storedSampleCount(b))
            assertNotNull(store.sessionById(b))
        }finally{
            store.close()
            app.deleteDatabase(dbName)
        }
    }
}
