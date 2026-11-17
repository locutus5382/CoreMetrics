package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
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
            assertEquals(12006L,db.storedSampleCount(session))
        }finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
