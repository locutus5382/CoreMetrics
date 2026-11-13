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
            assertTrue(store.updateConfiguration(a,"Underclocking"))
            val saved=store.sessionById(a)
            assertNotNull(saved)
            assertEquals("Hades · T30",saved!!.note)
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
