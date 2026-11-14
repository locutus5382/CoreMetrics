package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.analysis.ScorecardExportRequest
import org.junit.Assert.*
import org.junit.Test

class ScorecardExportRequestTest {
    @Test fun persistsSessionSegmentAndShareChoiceAcrossRecreation() {
        val r=ScorecardExportRequest(41L,0L,1_795_000_000_000L,
            1_795_000_120_000L,"crystal_aero",true)
        assertEquals(r,ScorecardExportRequest.decode(r.encode()))
        val ab=ScorecardExportRequest(41L,42L,25_000L,95_000L,
            "cobalt_matrix",false)
        assertEquals(ab,ScorecardExportRequest.decode(ab.encode()))
    }
    @Test fun invalidOrTamperedPendingRequestsCannotExport() {
        assertNull(ScorecardExportRequest.decode(null))
        assertNull(ScorecardExportRequest.decode("not an export"))
        assertNull(ScorecardExportRequest.decode("1|1|1|0|60|frosted|1"))
        assertNull(ScorecardExportRequest.decode("1|1|0|60|20|frosted|1"))
        assertNull(ScorecardExportRequest.decode("1|1|0|0|60|unknown|1"))
        assertNull(ScorecardExportRequest.decode("1|1|0|0|60|frosted|yes"))
    }
}
