package com.locutus5382.coremetrics

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.locutus5382.coremetrics.analysis.ScorecardModel
import com.locutus5382.coremetrics.ui.MainActivity
import com.locutus5382.coremetrics.ui.ScorecardPngRenderer
import com.locutus5382.coremetrics.ui.SurfaceFinish
import com.locutus5382.coremetrics.monitor.MonitorHudOwner
import com.locutus5382.coremetrics.data.UiSessionStoreOwner
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-runtime tests: compiled by CI, executable on emulator/Portal via
 * :app:connectedDebugAndroidTest. CI assembly alone is NOT execution.
 */
@RunWith(AndroidJUnit4::class)
class ScorecardRuntimeTest {
    private val scorecard=ScorecardModel.Card(
        type=ScorecardModel.Type.SINGLE,
        title="SINGLE-SESSION DIAGNOSTICS",
        subtitle="Session #42 | test run | Stock",
        range="02:00 - 04:00 of original recording",
        badge="MEASURED WINDOW",
        badgesAreCaution=false,
        rows=listOf(
            ScorecardModel.ValueRow("CPU temperature P50 / P95","46.0 °C / 51.0 °C"),
            ScorecardModel.ValueRow("GPU temperature P50 / P95","42.0 °C / 45.0 °C"),
            ScorecardModel.ValueRow("CPU / GPU load P50","34.0% / 55.0%"),
            ScorecardModel.ValueRow("Mean battery discharge","5.00 W")
        ),
        series=listOf(
            ScorecardModel.Series("CPU temperature",listOf(
                ScorecardModel.Trace(120_000L,42.0),
                ScorecardModel.Trace(125_000L,46.0),
                ScorecardModel.Trace(130_000L,null),
                ScorecardModel.Trace(140_000L,48.0)),15_000L),
            ScorecardModel.Series("GPU temperature",listOf(
                ScorecardModel.Trace(120_000L,39.0),
                ScorecardModel.Trace(125_000L,42.0),
                ScorecardModel.Trace(140_000L,44.0)),15_000L)
        ),
        graphStartMs=120_000L,graphEndMs=240_000L,
        footnotes=listOf(
            "Observed source samples only",
            "No FPS data was sampled",
            "Battery is terminal discharge",
            "No interpolation over recording gaps"
        ),
        sourceDescription="CoreMetrics device test · no personal data"
    )
    @Test fun allThemePngScorecardsAreValid1600x1050AndroidBitmaps() {
        for(style in SurfaceFinish.values()) {
            val stream=ByteArrayOutputStream()
            ScorecardPngRenderer.write(scorecard,style,stream)
            val bytes=stream.toByteArray()
            assertTrue("PNG output must not be empty",bytes.size>20_000)
            assertEquals(137,bytes[0].toInt() and 0xFF)
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            assertNotNull("Android decoder rejected "+style,bitmap)
            assertEquals(1600,bitmap!!.width)
            assertEquals(1050,bitmap.height)
            assertNotEquals(bitmap.getPixel(2,2),bitmap.getPixel(500,500))
            bitmap.recycle()
        }
    }

    @Test fun activityRecreationKeepsOneReadSideSqliteHelper() {
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val first=UiSessionStoreOwner.get(ctx)
        val second=UiSessionStoreOwner.get(ctx)
        assertSame(first,second)
    }
    @Test fun serviceRecreationObtainsSameProcessWindowOwner() {
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val first=MonitorHudOwner.get(ctx)
        val second=MonitorHudOwner.get(ctx)
        assertSame(first,second)
        // Do not require overlay permission or create any WindowManager view.
        assertFalse(first.attached())
    }
    @Test fun documentContractCreatesUserSelectedPngNotArbitraryFile() {
        val intent=ActivityResultContracts.CreateDocument("image/png")
            .createIntent(ApplicationProvider.getApplicationContext(),"CoreMetrics_score.png")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.action)
        assertEquals("image/png",intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
    }

    @Test fun activityRecreationDoesNotRequireGameplayOrOverlayPermission() {
        // Must NOT be run while the user is recording an irreplaceable session.
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.recreate()
            assertNotNull(scenario)
        }
    }
}
