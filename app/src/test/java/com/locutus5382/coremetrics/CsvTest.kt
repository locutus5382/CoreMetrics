package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.Csv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun escapesQuotesAndDangerousFormulas() {
        assertEquals("\"' =SUM(1,2)\"", Csv.escape(" =SUM(1,2)"))
        assertEquals("\"a\"\"b\"", Csv.escape("a\"b"))
    }
}
