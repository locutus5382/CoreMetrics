package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.data.SessionArchive
import com.locutus5382.coremetrics.data.SessionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionArchiveTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()

    private fun isolated(name:String):SessionStore {
        app.deleteDatabase(name)
        val sandbox=object:ContextWrapper(app){
            override fun getDatabasePath(ignored:String):java.io.File =
                app.getDatabasePath(name)
            override fun openOrCreateDatabase(ignored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory)
            override fun openOrCreateDatabase(ignored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                handler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory,handler)
        }
        return SessionStore(sandbox)
    }

    private fun makeCsv(id:Int):String=buildString {
        append("game_label,configuration,status,sample_epoch_ms,cpu_temp_c,gpu_temp_c,battery_net_w,battery_power_status,graphics_driver,profile_name\n")
        for(i in 0 until 16) {
            append("NFS,Stock,COMPLETED,")
            append(1_700_000_000_000L+id*500_000L+i*5_000L)
            append(",55.0,44.0,4.6,DISCHARGING; BATTERY_NET_ONLY,Custom: T30,T30\n")
        }
    }

    private fun archiveOfTwo():ByteArray {
        val store=isolated("coremetrics_v0983_archive_source.db")
        try {
            val a=store.importCsv(StringReader(makeCsv(1)))
            val b=store.importCsv(StringReader(makeCsv(2)))
            val preset=store.saveQuickProfile(name="T30",gameNote="NFS",
                graphicsDriver="Custom: T30",resolutionScale="Handheld 1x",
                performanceMode="Performance",fanMode="Smart",
                clusterTuneMode="Stock")
            assertTrue(store.applyQuickProfile(a,preset))
            assertTrue(store.applyQuickProfile(b,preset))
            store.linkSessions(a,b)
            val out=ByteArrayOutputStream()
            val summary=SessionArchive.export(store,out)
            assertEquals(2,summary.sessions)
            assertEquals(32L,summary.samples)
            assertEquals(1,summary.links)
            return out.toByteArray()
        }finally {
            store.close();app.deleteDatabase("coremetrics_v0983_archive_source.db")
        }
    }

    @Test fun archiveRoundTripsTagsProfilesLinksAndAllTelemetryWithoutOverwrites() {
        val bytes=archiveOfTwo()
        val store=isolated("coremetrics_v0983_restore_target.db")
        try {
            val existing=store.importCsv(StringReader(makeCsv(9)))
            val summary=SessionArchive.restore(store,ByteArrayInputStream(bytes))
            assertEquals(2,summary.sessions)
            assertEquals(32L,summary.samples)
            assertEquals(1,summary.profiles)
            assertEquals(1,summary.links)
            val sessions=store.list()
            assertEquals(3,sessions.size)
            assertEquals(16L,store.storedSampleCount(existing))
            assertEquals(2,sessions.count{it.profileName=="T30"})
            assertEquals(1,store.links().size)
            assertEquals(1,store.quickProfiles().size)
            // Second restore appends; existing data and links remain intact.
            SessionArchive.restore(store,ByteArrayInputStream(bytes))
            assertEquals(5,store.list().size)
            assertEquals(2,store.links().size)
            assertEquals(2,store.quickProfiles().size)
            assertEquals(16L,store.storedSampleCount(existing))
        }finally {
            store.close();app.deleteDatabase("coremetrics_v0983_restore_target.db")
        }
    }

    @Test fun missingSecondZipEntryRollsBackFirstImportedSessionAtomically() {
        val full=archiveOfTwo()
        val src=ZipInputStream(ByteArrayInputStream(full))
        val raw=ByteArrayOutputStream()
        ZipOutputStream(raw).use{broken->
            src.use{archive->
                repeat(2) {
                    val entry=archive.nextEntry ?:error("Missing expected source entry")
                    broken.putNextEntry(ZipEntry(entry.name))
                    archive.copyTo(broken)
                    broken.closeEntry()
                    archive.closeEntry()
                }
            }
        }
        val store=isolated("coremetrics_v0983_archive_rollback.db")
        try {
            val before=store.importCsv(StringReader(makeCsv(8)))
            assertTrue(runCatching{
                SessionArchive.restore(store,ByteArrayInputStream(raw.toByteArray()))
            }.isFailure)
            assertEquals(1,store.list().size)
            assertEquals(16L,store.storedSampleCount(before))
            assertEquals(0,store.links().size)
            assertEquals(0,store.quickProfiles().size)
        }finally {
            store.close();app.deleteDatabase("coremetrics_v0983_archive_rollback.db")
        }
    }
}
