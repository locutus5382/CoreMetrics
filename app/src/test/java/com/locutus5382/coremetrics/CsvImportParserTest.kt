package com.locutus5382.coremetrics

import com.locutus5382.coremetrics.data.CsvRecordReader
import com.locutus5382.coremetrics.data.CsvSessionColumns
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class CsvImportParserTest {
    @Test fun escapedCommasQuotesCrLfAndNewline() {
        val parser=CsvRecordReader(StringReader(
            "sample_epoch_ms,cpu_temp_c,game_label\r\n"+
            "1794481200000,49.2,\"NFS, \"\"Deluxe\"\"\"\r\n"+
            "1794481202000,50.5,\"Hades\nphase\"\n"))
        val h=CsvSessionColumns(parser.next()!!)
        val a=parser.next()!!
        assertEquals("NFS, \"Deluxe\"",h.field(a,"game_label"))
        assertEquals(49.2,h.number(a,"cpu_temp_c")!!,0.0001)
        assertEquals(1794481200000L,h.time(a))
        assertEquals("Hades\nphase",h.field(parser.next()!!,"game_label"))
        assertNull(parser.next())
    }
    @Test fun noFabricationAndRejectWrongFile() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvSessionColumns(listOf("temperature","watts"))
        }
        val h=CsvSessionColumns(listOf("sample_epoch_ms","cpu_temp_c"))
        assertNull(h.number(listOf("1794481200000",""),"cpu_temp_c"))
        assertNull(h.number(listOf("1794481200000","not-a-number"),"cpu_temp_c"))
    }
    @Test fun untrustedCsvDoesNotExecuteOrSilentlyAcceptMalformedQuote() {
        val p=CsvRecordReader(StringReader("sample_epoch_ms,cpu_temp_c\n"+
            "1794481200000,\"fake\"extra\n"))
        p.next()
        assertThrows(IllegalArgumentException::class.java){p.next()}
    }
}
