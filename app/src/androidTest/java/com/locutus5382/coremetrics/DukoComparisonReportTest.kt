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
import com.locutus5382.coremetrics.analysis.CompareStudioAnalysis
import com.locutus5382.coremetrics.analysis.DukoComparisonLoader
import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.ui.DukoComparisonRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader

@RunWith(AndroidJUnit4::class)
class DukoComparisonReportTest {
    private fun store():Pair<SessionStore,String> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val name="coremetrics_duko_comparison_test.db"
        app.deleteDatabase(name)
        val wrapped=object:ContextWrapper(app) {
            override fun getDatabasePath(n:String):File=app.getDatabasePath(name)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory)
            override fun openOrCreateDatabase(n:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                errorHandler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(name,mode,factory,errorHandler)
        }
        return SessionStore(wrapped) to name
    }
    private fun csv(driver:String,start:Long):String=buildString {
        append("game_label,configuration,status,sample_epoch_ms,")
        append("cpu_temp_c,gpu_temp_c,cpu_load_pct,gpu_load_pct,")
        append("battery_net_w,battery_power_status,gpu_telemetry_source,")
        append("battery_power_source,device_manufacturer,device_model,")
        append("device_code,android_api,graphics_driver\n")
        for(i in 0..30) {
            append("Hades,Stock,COMPLETED,")
            append(start+i*5_000L)
            append(",54.0,43.0,40,60,4.0,DISCHARGING,")
            append("LOAD:KGSL_PERCENT VERIFIED,")
            append("CURRENT:BATTERY_PROPERTY_CURRENT_NOW,")
            append("AYN,Odin 2 Portal,odin2portal,33,")
            append(driver)
            append('\n')
        }
    }

    @Test fun fiveSessionPdfPaginatesMatricesAndReadinessWithoutChangingSources() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val (db,name)=store()
        try {
            val ids=(0 until 5).map{index->
                db.importCsv(StringReader(csv(
                    if(index==0)"T30" else "StevenMXZ_"+index,
                    1_700_000_000_000L+index*1_000_000L)))
            }
            val result=DukoComparisonLoader.build(db,ids,ids[2],
                CompareStudioAnalysis.TestedVariable.DRIVER)
            assertEquals(5,result.runs.size)
            assertEquals(5,result.ids.size)
            val buffer=ByteArrayOutputStream()
            DukoComparisonRenderer.pdf(result,buffer)
            val file=File.createTempFile("duko_five_",".pdf",app.cacheDir)
            try {
                file.writeBytes(buffer.toByteArray())
                ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                    PdfRenderer(fd).use{renderer->
                        // 2 summary matrices, original metadata, 3 readiness
                        // pages, method and evidence/warning pages.
                        assertTrue("Five-session matrix was not paginated",
                            renderer.pageCount>=8)
                        for(i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use{page->
                                assertEquals(595,page.width)
                                assertEquals(842,page.height)
                                val thumb=Bitmap.createBitmap(595,842,
                                    Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(thumb,null,null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }finally {thumb.recycle()}
                            }
                        }
                    }
                }
            }finally {file.delete()}
            ids.forEach {assertEquals(31L,db.storedSampleCount(it))}
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }

    @Test fun databaseToPNGAndMultiPagePdfUsingTwoFullRuns() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val (db,name)=store()
        try {
            val first=db.importCsv(StringReader(csv("T30",1_700_000_000_000L)))
            val second=db.importCsv(StringReader(csv("StevenMXZ",1_700_100_000_000L)))
            val report=DukoComparisonLoader.build(db,listOf(first,second),
                first,CompareStudioAnalysis.TestedVariable.DRIVER)
            assertEquals(2,report.runs.size)
            assertEquals(first,report.baselineId)
            assertEquals(9,report.rows.size)
            assertEquals(31L,db.storedSampleCount(first))
            assertEquals(31L,db.storedSampleCount(second))
            val png=ByteArrayOutputStream()
            DukoComparisonRenderer.png(report,png)
            val bmp=BitmapFactory.decodeByteArray(
                png.toByteArray(),0,png.size())
            assertNotNull(bmp)
            assertEquals(1600,bmp.width)
            assertEquals(2200,bmp.height)
            bmp.recycle()
            val pdf=ByteArrayOutputStream()
            DukoComparisonRenderer.pdf(report,pdf)
            assertEquals("%PDF",pdf.toByteArray().copyOfRange(0,4)
                .toString(Charsets.US_ASCII))
            val tmp=File.createTempFile("duko_comp_",".pdf",app.cacheDir)
            try {
                tmp.writeBytes(pdf.toByteArray())
                ParcelFileDescriptor.open(tmp,
                    ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                    PdfRenderer(fd).use{reader->
                        assertTrue(reader.pageCount>=3)
                        for(i in 0 until reader.pageCount) {
                            reader.openPage(i).use{page->
                                val image=Bitmap.createBitmap(595,842,
                                    Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(image,null,null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    assertEquals(595,image.width)
                                }finally {image.recycle()}
                            }
                        }
                    }
                }
            }finally {tmp.delete()}
            assertEquals(31L,db.storedSampleCount(first))
            assertEquals(31L,db.storedSampleCount(second))
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
