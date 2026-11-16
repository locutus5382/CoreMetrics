package com.locutus5382.coremetrics

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.monitor.HudOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HudBatteryOptionsTest {
    @Test fun optionalBatteryTemperatureDefaultsOffAndPersists() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val dbName="coremetrics_v0982_hud_settings_test.db"
        app.deleteDatabase(dbName)
        val sandbox=object:ContextWrapper(app){
            override fun getDatabasePath(ignored:String):java.io.File =
                app.getDatabasePath(dbName)
            override fun openOrCreateDatabase(ignored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(ignored:String,mode:Int,
                factory:SQLiteDatabase.CursorFactory?,
                handler:DatabaseErrorHandler?):SQLiteDatabase =
                app.openOrCreateDatabase(dbName,mode,factory,handler)
        }
        val prefs=app.getSharedPreferences("coremetrics_settings",Context.MODE_PRIVATE)
        val old=prefs.all.toMap()
        try {
            prefs.edit().clear().commit()
            val defaults=HudOptions.from(sandbox)
            assertFalse(defaults.showBatteryTemp)
            val chosen=defaults.copy(showBatteryTemp=true)
            assertEquals(defaults.selectedCount()+1,chosen.selectedCount())
            assertTrue(chosen.save(sandbox))
            assertTrue(HudOptions.from(sandbox).showBatteryTemp)
            val override=HudOptions.addExtras(Intent(),chosen.copy(showBatteryTemp=false))
            assertFalse(HudOptions.from(sandbox,override).showBatteryTemp)
        }finally {
            val editor=prefs.edit().clear()
            old.forEach{(key,value)->
                when(value) {
                    is Boolean->editor.putBoolean(key,value)
                    is Int->editor.putInt(key,value)
                    is Long->editor.putLong(key,value)
                    is Float->editor.putFloat(key,value)
                    is String->editor.putString(key,value)
                }
            }
            editor.commit()
            app.deleteDatabase(dbName)
        }
    }
}
