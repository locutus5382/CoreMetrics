package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.analysis.DukoReportLoader
import com.locutus5382.coremetrics.analysis.DukoSessionInsight
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.ui.DukoSessionRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader

@RunWith(AndroidJUnit4::class)
class DukoSessionReportTest {
    private fun store():Pair<SessionStore,String> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val name="coremetrics_v0991_duko_instrumentation.db"
        app.deleteDatabase(name)
        val context=object:ContextWrapper(app){
            override fun getDatabasePath(n:String):java.io.File =
                app.getDatabasePath(name)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory,errorHandler)
        }
        return SessionStore(context) to name
    }

    @Test fun cachedBoundsAndCancellationPreserveAllSourceRows() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val (db,name)=store()
        try {
            val csv=buildString {
                append("game_label,configuration,status,sample_epoch_ms,cpu_temp_c\n")
                for(i in 0..1024) {
                    append("NFS,Stock,COMPLETED,")
                    append(1_700_000_100_000L+i*2_000L)
                    append(",50.0\n")
                }
            }
            val id=db.importCsv(StringReader(csv))
            val bounds=db.reportBounds(id)!!
            var visited=0L
            assertEquals(bounds.count,db.forEachReportPoint(id,bounds) {
                visited++
            })
            assertEquals(1025L,visited)
            var scratchCount=0L
            var previous=-1L
            val ids=HashSet<Int>()
            assertEquals(bounds.count,db.forEachReportSample(id,bounds){row ->
                assertTrue(row.timeMs>previous)
                previous=row.timeMs
                // The scratch instance must be physically reused for all
                // rows, not secretly reallocated for each stored sample.
                ids+=System.identityHashCode(row)
                scratchCount++
            })
            assertEquals(1,ids.size)
            assertEquals(1025L,scratchCount)
            // Compare full Point-based analysis and optimized primitive
            // cursor analysis; numerical results must be identical.
            val optimized=DukoReportLoader.build(db,id)
            val reference=DukoSessionInsight.Accumulator(
                db.sessionById(id)!!,bounds,db.reportCadenceMs(id))
            db.forEachReportPoint(id,bounds){reference.accept(it)}
            val oldReport=reference.finish()
            for(metric in DukoSessionInsight.Metric.entries) {
                val x=optimized.stat(metric)
                val y=oldReport.stat(metric)
                assertEquals(y.count,x.count)
                assertEquals(y.p50,x.p50)
                assertEquals(y.p95,x.p95)
            }
            assertEquals(oldReport.measuredEnergyWh,optimized.measuredEnergyWh)
            assertTrue(runCatching {
                db.forEachReportPoint(id,bounds.copy(count=bounds.count+1)) { }
            }.isFailure)
            var checks=0
            assertTrue(runCatching {
                DukoReportLoader.build(db,id) {
                    if(++checks>=2)
                        throw java.util.concurrent.CancellationException(
                            "Synthetic Activity cancellation")
                }
            }.isFailure)
            assertTrue("Cancellation was observed during the cursor scan",
                checks>=2)
            assertEquals(1025L,db.storedSampleCount(id))
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }

    @Test fun fullCursorPNGAndVectorPDFUseSameSourceData() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val (db,name)=store()
        try {
            val csv=buildString {
                append("game_label,configuration,status,sample_epoch_ms,")
                append("cpu_temp_c,gpu_temp_c,battery_temp_c,cpu_load_pct,")
                append("gpu_load_pct,battery_net_w,battery_power_status,")
                append("device_manufacturer,device_model,android_api\n")
                for(i in 0..12005) {
                    append("NFS,Stock,COMPLETED,")
                    append(1_700_000_000_000L+i*2000L)
                    append(",")
                    append(if(i==12005)"109.0" else "58.0")
                    append(",45.0,38.0,35,48,4.5,")
                    append("DISCHARGING; BATTERY_NET_ONLY,AYN,Odin 2 Portal,33\n")
                }
            }
            val session=db.importCsv(StringReader(csv))
            val report=DukoReportLoader.build(db,session)
            assertEquals(12006,report.sampleCount)
            assertEquals(109.0,report.stat(DukoSessionInsight.Metric.CPU_TEMP)
                .peak!!,.001)
            assertEquals("Odin 2 Portal",report.session.deviceModel)
            val png=ByteArrayOutputStream()
            DukoSessionRenderer.png(report,png)
            val bitmap=BitmapFactory.decodeByteArray(
                png.toByteArray(),0,png.size())
            assertNotNull(bitmap)
            assertEquals(1600,bitmap.width)
            assertEquals(2200,bitmap.height)
            bitmap.recycle()
            val pdf=ByteArrayOutputStream()
            DukoSessionRenderer.pdf(report,pdf)
            assertTrue(pdf.toByteArray().copyOfRange(0,4)
                .toString(Charsets.US_ASCII)=="%PDF")
            assertTrue(pdf.size()>5000)
            // Decode real PDF pages, not just the %PDF signature. This catches
            // corrupt cross-page streams which would otherwise pass compilation.
            val file=File.createTempFile("duko_instrumented_", ".pdf", app.cacheDir)
            try {
                file.writeBytes(pdf.toByteArray())
                ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        assertTrue("Expected summary and evidence pages",
                            renderer.pageCount>=2)
                        renderer.openPage(0).use { page ->
                            val preview=Bitmap.createBitmap(595,842,
                                Bitmap.Config.ARGB_8888)
                            try {
                                page.render(preview,null,null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertEquals(595,preview.width)
                                assertEquals(842,preview.height)
                                // Rendered report must contain visible ink.
                                var nonWhite=0
                                for(y in 0 until preview.height step 24) {
                                    for(x in 0 until preview.width step 24) {
                                        if(preview.getPixel(x,y)!=-1)nonWhite++
                                    }
                                }
                                assertTrue("Rendered PDF page appears blank",
                                    nonWhite>15)
                            }finally { preview.recycle() }
                        }
                    }
                }
            }finally {file.delete()}
            assertEquals(12006L,db.storedSampleCount(session))
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
