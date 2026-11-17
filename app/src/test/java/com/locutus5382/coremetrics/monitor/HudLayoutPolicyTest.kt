package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class HudLayoutPolicyTest {
    @Test fun allFiveThemesAreDistinctAndDefaultMigratesToStriker() {
        assertEquals(5,HudLayoutPolicy.themes.distinct().size)
        assertEquals(HudLayoutPolicy.STRIKER,HudLayoutPolicy.normalize(null))
        assertEquals(HudLayoutPolicy.STRIKER,HudLayoutPolicy.normalize("unknown"))
        assertEquals(HudLayoutPolicy.STRIKER,HudLayoutPolicy.normalize("pill"))
        HudLayoutPolicy.themes.forEach {
            assertEquals(it,HudLayoutPolicy.normalize(it))
            assertTrue(HudLayoutPolicy.label(it).isNotBlank())
            assertTrue(HudLayoutPolicy.description(it).isNotBlank())
        }
    }

    @Test fun previousCardAndRibbonIdsMigrateWithoutDroppingSettings() {
        assertEquals(HudLayoutPolicy.TACTICAL,HudLayoutPolicy.normalize("pro"))
        assertEquals(HudLayoutPolicy.TACTICAL,
            HudLayoutPolicy.normalize("glass_card"))
        assertEquals(HudLayoutPolicy.STRIP,HudLayoutPolicy.normalize("ribbon"))
        assertTrue(HudLayoutPolicy.card("glass_card"))
        assertTrue(HudLayoutPolicy.ribbon("ribbon"))
        assertTrue(HudLayoutPolicy.lens("duko_lens"))
    }

    @Test fun compactFormattingAndReadabilityArePredictable() {
        val formats=HudLayoutPolicy.themes
        for(theme in formats){
            assertTrue(HudLayoutPolicy.nominalTextSp(theme,"small")<
                HudLayoutPolicy.nominalTextSp(theme,"regular"))
            assertTrue(HudLayoutPolicy.nominalTextSp(theme,"regular")<
                HudLayoutPolicy.nominalTextSp(theme,"large"))
        }
        val l=buildString {
            repeat(7){i->
                append(HudLayoutPolicy.divider(HudLayoutPolicy.STRIP,i,7,true))
                append("M").append(i)
            }
        }
        assertEquals(7,Regex("M[0-6]").findAll(l).count())
        assertTrue(l.contains('\n'))
        assertTrue(HudLayoutPolicy.ribbonTwoRows(7,450))
        assertFalse(HudLayoutPolicy.ribbonTwoRows(4,450))
    }

    @Test fun swipeToEdgeNeedsBothDirectionAndDeliberateTravel() {
        val fn:(Int,Int,Int,Int,Float,Float,Int,Int)->Boolean =
            HudLayoutPolicy::shouldCollapseOnRelease
        assertTrue(fn(0,100,250,1200,-150f,5f,65,26))
        assertTrue(fn(950,100,250,1200,160f,1f,65,26))
        assertFalse(fn(0,100,250,1200,-30f,1f,65,26))
        assertFalse(fn(300,100,250,1200,-200f,2f,65,26))
        assertFalse(fn(950,100,250,1200,25f,200f,65,26))
        assertFalse(fn(0,100,250,1200,100f,1f,65,26))
    }

    @Test fun cpuAdvisoryOnlyNotCertification() {
        assertEquals(0,HudLayoutPolicy.advisoryBand(null))
        assertEquals(0,HudLayoutPolicy.advisoryBand(Double.NaN))
        assertEquals(0,HudLayoutPolicy.advisoryBand(41.99))
        assertEquals(1,HudLayoutPolicy.advisoryBand(42.0))
        assertEquals(1,HudLayoutPolicy.advisoryBand(80.0))
        assertEquals(2,HudLayoutPolicy.advisoryBand(80.1))
    }
}
