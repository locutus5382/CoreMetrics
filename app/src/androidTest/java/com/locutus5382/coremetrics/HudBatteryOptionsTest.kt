package com.locutus5382.coremetrics

import android.content.Context
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
        val prefs=app.getSharedPreferences("coremetrics_settings",Context.MODE_PRIVATE)
        val old=prefs.all.toMap()
        try {
            prefs.edit().clear().commit()
            val defaults=HudOptions.from(app)
            assertFalse(defaults.showBatteryTemp)
            val chosen=defaults.copy(showBatteryTemp=true)
            assertEquals(defaults.selectedCount()+1,chosen.selectedCount())
            chosen.save(app)
            assertTrue(HudOptions.from(app).showBatteryTemp)
            val override=HudOptions.addExtras(Intent(),chosen.copy(showBatteryTemp=false))
            assertFalse(HudOptions.from(app,override).showBatteryTemp)
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
        }
    }
}
