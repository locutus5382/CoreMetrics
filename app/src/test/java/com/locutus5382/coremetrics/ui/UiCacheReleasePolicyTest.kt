package com.locutus5382.coremetrics.ui

import org.junit.Assert.*
import org.junit.Test

class UiCacheReleasePolicyTest {
    @Test fun foregroundNeverDiscardsLoadedReadouts() {
        for(d in listOf(false,true))
            for(l in listOf(false,true))
                assertFalse(UiCacheReleasePolicy.permitted(true,d,l))
    }
    @Test fun backgroundFreesDataUnlessAnExportOrDocumentPickerIsPending() {
        assertTrue(UiCacheReleasePolicy.permitted(false,false,false))
        assertFalse(UiCacheReleasePolicy.permitted(false,true,false))
        assertFalse(UiCacheReleasePolicy.permitted(false,false,true))
        assertFalse(UiCacheReleasePolicy.permitted(false,true,true))
    }
}
