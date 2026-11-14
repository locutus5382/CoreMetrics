package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.DocumentWindowRequest as W
import org.junit.Assert.*
import org.junit.Test

class DocumentWindowRequestTest {
    @Test fun segmentSurvivesConfigurationAndProcessSavedState(){
        val x=W.Segment(42L,1_794_500_001_000L,1_794_500_200_000L)
        assertEquals(x,W.parseSegment(W.segment(x)))
    }
    @Test fun abPreservesBothIdsAndOriginalElapsedWindow(){
        val x=W.Ab(8L,1L,25_000L,860_000L)
        assertEquals(x,W.parseAb(W.ab(x)))
    }
    @Test fun invalidOrStaleTokensAreNotAccepted(){
        assertNull(W.parseSegment(null))
        assertNull(W.parseSegment("segment|7|100|50"))
        assertNull(W.parseSegment("segment|0|1|100"))
        assertNull(W.parseAb("ab|1|1|1|100"))
        assertNull(W.parseAb("ab|1|2|-10|100"))
        assertNull(W.parseAb("ab|999|two|1|100"))
        assertNull(W.parseAb("segment|1|0|200"))
    }
}
