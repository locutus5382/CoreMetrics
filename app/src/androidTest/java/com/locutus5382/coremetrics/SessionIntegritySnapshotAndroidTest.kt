package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.data.SessionIntegritySnapshot
import com.locutus5382.coremetrics.data.SessionStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.StringReader

@RunWith(AndroidJUnit4::class)
class SessionIntegritySnapshotAndroidTest {
    @Test fun oneShotSnapshotReadsPersistedCountsWithoutChangingSessions() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val name="coremetrics_0994_integrity_test.db"
        app.deleteDatabase(name)
        val isolated=object:ContextWrapper(app) {
            override fun getDatabasePath(n:String):File=app.getDatabasePath(name)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory,errorHandler)
        }
        val db=SessionStore(isolated)
        try {
            val csv=buildString {
                append("game_label,configuration,status,sample_epoch_ms,cpu_temp_c\n")
                for(i in 0..10) {
                    append("PrivateGame,Stock,COMPLETED,")
                    append(1_700_000_000_000L+i*5_000L)
                    append(",48.0\n")
                }
            }
            val id=db.importCsv(StringReader(csv))
            val before=db.storedSampleCount(id)
            assertEquals(11L,before)
            val report=SessionIntegritySnapshot.report(db)
            assertTrue(report.contains("Session #"+id))
            assertTrue(report.contains("11 saved samples"))
            assertFalse(report.contains("PrivateGame"))
            assertTrue(report.contains("no inconsistency detected"))
            assertEquals(before,db.storedSampleCount(id))
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
