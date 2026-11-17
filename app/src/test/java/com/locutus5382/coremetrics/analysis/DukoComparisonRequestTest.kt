package com.locutus5382.coremetrics.analysis

import org.junit.Assert.*
import org.junit.Test

class DukoComparisonRequestTest {
    @Test fun roundTripPreservesBaselineOrderAndUserDeclaredVariable() {
        val request=DukoComparisonRequest(
            "pdf",listOf(12,5,17),5,CompareStudioAnalysis.TestedVariable.DRIVER)
        assertEquals(request,DukoComparisonRequest.decode(request.encode()))
    }
    @Test fun malformedOrDuplicateRequestsCannotProceed() {
        assertNull(DukoComparisonRequest.decode(null))
        assertNull(DukoComparisonRequest.decode("pdf|1,1|1|DRIVER"))
        assertNull(DukoComparisonRequest.decode("pdf|1,2|3|DRIVER"))
        assertNull(DukoComparisonRequest.decode("txt|1,2|1|DRIVER"))
        assertNull(DukoComparisonRequest.decode("pdf|1,2|1|INVENTED"))
        assertNull(DukoComparisonRequest.decode("pdf|1,2,3,4,5,6|1|DRIVER"))
    }
}
