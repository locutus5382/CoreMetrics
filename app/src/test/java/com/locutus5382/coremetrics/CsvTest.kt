package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.Csv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun quotesAndBlocksSpreadsheetFormulas() {
        assertEquals("\"' =SUM(1,2)\"", Csv.escape(" =SUM(1,2)"))
        assertEquals("\"a\"\"b\"", Csv.escape("a\"b"))
        assertEquals("\"'-cmd\"", Csv.escape("-cmd"))
    }
    @Test fun negativeAndPositiveNumericMeasurementsRemainNumbers() {
        assertEquals("\"-13.5\"", Csv.escape("-13.5"))
        assertEquals("\"+4.3\"", Csv.escape("+4.3"))
        assertEquals("\"0.95\"", Csv.escape("0.95"))
    }
}
