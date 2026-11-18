package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.domain.StopReason
import com.locutus5382.coremetrics.engine.HeimdallTerminalFinalizer
import com.locutus5382.coremetrics.engine.TerminalCommitPort
import com.locutus5382.coremetrics.engine.TerminalLeaseProof
import com.locutus5382.coremetrics.monitor.RecorderLease
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real SQLite v9 + SharedPreferences assertions against an isolated test DB
 * and lease. Does NOT start a recorder, sample hardware or use user sessions.
 */
@RunWith(AndroidJUnit4::class)
class HeimdallTerminalCommitAndroidTest {
    private data class Sandbox(
        val context: Context,
        val store: SessionStore,
        val prefs: SharedPreferences,
        val dbName: String,
    )
    private fun sandbox(case: String): Sandbox {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val dbName="heimdall_m2j_terminal_${case}.db"
        val preferences=app.getSharedPreferences(
            "heimdall_m2j_terminal_${case}",Context.MODE_PRIVATE)
        check(preferences.edit().clear().commit())
        check(app.deleteDatabase(dbName) || !app.getDatabasePath(dbName).exists())
        val ctx=object:ContextWrapper(app) {
            override fun getDatabasePath(name:String):File=app.getDatabasePath(dbName)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory,errorHandler)
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences =
                preferences
        }
        return Sandbox(ctx,SessionStore(ctx),preferences,dbName)
    }
    private class Port(
        private val sandbox:Sandbox,
        var failClearOnce:Boolean = false,
    ):TerminalCommitPort {
        var writes=0
        override suspend fun lease(sessionId:Long):TerminalLeaseProof {
            val r=RecorderLease.rawSession(sandbox.context)
            return TerminalLeaseProof(r.active,
                r.recording && r.sessionId==sessionId &&
                    r.owner==RecorderLease.Owner.HEIMDALL_DEBUG,
                r.stopPersisted)
        }
        override suspend fun status(sessionId:Long):String? =
            sandbox.store.recordingStatus(sessionId)
        override suspend fun commitIfRecording(
            sessionId:Long,terminalStatus:String
        ):Boolean {
            val ok=sandbox.store.finishRecordingOnce(
                sessionId,System.currentTimeMillis(),terminalStatus)
            if(ok)writes++
            return ok
        }
        override suspend fun clearMatchingLease(sessionId:Long):Boolean {
            if(failClearOnce) {
                failClearOnce=false
                return false
            }
            return RecorderLease.clearHeimdallStoppedSession(
                sandbox.context,sessionId)
        }
    }

    @Test fun sqliteFinalizationIsIdempotentWhenLeaseClearFails() = runBlocking {
        val sb=sandbox("retry")
        try {
            val id=sb.store.begin("M2J","Stock",System.currentTimeMillis())
            assertTrue(RecorderLease.start(
                sb.context,id,"M2J","Stock",2_000,false,
                owner=RecorderLease.Owner.HEIMDALL_DEBUG))
            assertTrue(RecorderLease.requestStop(sb.context))
            val port=Port(sb,failClearOnce=true)
            assertFalse(HeimdallTerminalFinalizer.finalizeOriginal(
                id,StopReason.USER,port))
            val first=sb.store.sessionById(id)!!
            assertEquals("COMPLETED",first.status)
            assertNotNull(first.endedAt)
            assertEquals(1,port.writes)
            assertTrue(RecorderLease.hasRawActiveLease(sb.context))
            assertTrue(HeimdallTerminalFinalizer.finalizeOriginal(
                id,StopReason.USER,port))
            val second=sb.store.sessionById(id)!!
            assertEquals(first.endedAt,second.endedAt)
            assertEquals(1,port.writes)
            assertFalse(RecorderLease.hasRawActiveLease(sb.context))
        } finally {
            sb.store.close()
            RecorderLease.clear(sb.context)
            ApplicationProvider.getApplicationContext<Context>()
                .deleteDatabase(sb.dbName)
            sb.prefs.edit().clear().commit()
        }
    }

    @Test fun missingDurableStopCannotChangeSqliteRow() = runBlocking {
        val sb=sandbox("stop")
        try {
            val id=sb.store.begin("M2J","Stock",System.currentTimeMillis())
            assertTrue(RecorderLease.start(
                sb.context,id,"M2J","Stock",2_000,false,
                owner=RecorderLease.Owner.HEIMDALL_DEBUG))
            val port=Port(sb)
            assertFalse(HeimdallTerminalFinalizer.finalizeOriginal(
                id,StopReason.USER,port))
            assertEquals("RECORDING",sb.store.recordingStatus(id))
            assertEquals(0,port.writes)
            assertTrue(RecorderLease.hasRawActiveLease(sb.context))
        } finally {
            sb.store.close()
            RecorderLease.clear(sb.context)
            ApplicationProvider.getApplicationContext<Context>()
                .deleteDatabase(sb.dbName)
            sb.prefs.edit().clear().commit()
        }
    }

    @Test fun terminalCasCannotRewritePriorOutcomeOrEndTime() = runBlocking {
        val sb=sandbox("cas")
        try {
            val id=sb.store.begin("M2J","Stock",System.currentTimeMillis())
            assertTrue(RecorderLease.start(
                sb.context,id,"M2J","Stock",2_000,false,
                owner=RecorderLease.Owner.HEIMDALL_DEBUG))
            assertTrue(RecorderLease.requestStop(sb.context))
            assertTrue(sb.store.finishRecordingOnce(
                id,123456L,"FAILED"))
            assertFalse(sb.store.finishRecordingOnce(
                id,999999L,"COMPLETED"))
            assertEquals("FAILED",sb.store.recordingStatus(id))
            assertFalse(HeimdallTerminalFinalizer.finalizeOriginal(
                id,StopReason.USER,Port(sb)))
            assertEquals(123456L,sb.store.sessionById(id)?.endedAt)
            assertEquals("FAILED",sb.store.sessionById(id)?.status)
            assertTrue(RecorderLease.hasRawActiveLease(sb.context))
        } finally {
            sb.store.close()
            RecorderLease.clear(sb.context)
            ApplicationProvider.getApplicationContext<Context>()
                .deleteDatabase(sb.dbName)
            sb.prefs.edit().clear().commit()
        }
    }
}
