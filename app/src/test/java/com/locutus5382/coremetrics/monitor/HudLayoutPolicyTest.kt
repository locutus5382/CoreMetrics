package com.locutus5382.coremetrics.monitor

import org.junit.Assert.*
import org.junit.Test

class HudLayoutPolicyTest {
    @Test fun originalPillRemainsTheDefaultAndUnknownValuesAreSafe() {
        assertEquals(HudLayoutPolicy.PILL,HudLayoutPolicy.normalize(null))
        assertEquals(HudLayoutPolicy.PILL,HudLayoutPolicy.normalize(""))
        assertEquals(HudLayoutPolicy.PILL,HudLayoutPolicy.normalize("unknown"))
        assertEquals(HudLayoutPolicy.PILL,HudLayoutPolicy.normalize("pill"))
    }

    @Test fun historicalRoundedProLayoutMigratesToTheGlassCard() {
        assertEquals(HudLayoutPolicy.GLASS_CARD,HudLayoutPolicy.normalize("pro"))
        assertEquals(HudLayoutPolicy.GLASS_CARD,
            HudLayoutPolicy.normalize("glass_card"))
        assertTrue(HudLayoutPolicy.card("pro"))
        assertFalse(HudLayoutPolicy.ribbon("pro"))
        assertTrue(HudLayoutPolicy.ribbon("ribbon"))
    }

    @Test fun layoutSeparatorsKeepEverySelectedMetricInOrder() {
        val readings=(1..7).map{"M$it"}
        for(layout in listOf("pill","glass_card","ribbon")) {
            val text=buildString {
                readings.forEachIndexed { index,value ->
                    append(HudLayoutPolicy.divider(layout,index,readings.size))
                    append(value)
                }
            }
            readings.forEach { assertTrue(text.contains(it)) }
            assertEquals(7,Regex("M[1-7]").findAll(text).count())
            assertTrue(text.indexOf("M1")<text.indexOf("M7"))
        }
        assertTrue(HudLayoutPolicy.divider("glass_card",2,7).contains('\n'))
        assertTrue(HudLayoutPolicy.divider("pill",2,7).contains('\n'))
        assertTrue(HudLayoutPolicy.divider("ribbon",2,7).contains('│'))
    }

    @Test fun ribbonWrapsInsteadOfHidingMetricsOnNarrowDisplays() {
        assertTrue(HudLayoutPolicy.ribbonTwoRows(7,450))
        assertFalse(HudLayoutPolicy.ribbonTwoRows(4,450))
        assertFalse(HudLayoutPolicy.ribbonTwoRows(7,850))
        val fallback=buildString {
            repeat(7) { i ->
                append(HudLayoutPolicy.divider("ribbon",i,7,true))
                append("M").append(i)
            }
        }
        assertTrue(fallback.count{it=='\n'}>=3)
        assertTrue(fallback.contains("M6"))
    }

    @Test fun textScalePreservesFamiliarSizeOrdering() {
        for(layout in listOf("pill","glass_card","ribbon")) {
            assertTrue(HudLayoutPolicy.nominalTextSp(layout,"small")<
                HudLayoutPolicy.nominalTextSp(layout,"regular"))
            assertTrue(HudLayoutPolicy.nominalTextSp(layout,"regular")<
                HudLayoutPolicy.nominalTextSp(layout,"large"))
        }
        assertEquals(12f,HudLayoutPolicy.nominalTextSp("pill","regular"),0.001f)
    }
}
