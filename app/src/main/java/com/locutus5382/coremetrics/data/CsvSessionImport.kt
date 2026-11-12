package com.locutus5382.coremetrics.data

import java.io.PushbackReader
import java.io.Reader

/**
 * Bounded streaming RFC 4180 style CSV parser. Accepts escaped quotes,
 * commas and newlines inside quoted values. File content is never executed.
 */
internal class CsvRecordReader(reader: Reader) {
    private val input = PushbackReader(reader, 1)
    private var eof = false
    companion object {
        const val MAX_RECORD_CHARS = 24_000
    }
    fun next(): List<String>? {
        if (eof) return null
        val result = ArrayList<String>()
        val value = StringBuilder()
        var quoted = false
        var closedQuote = false
        var fieldStarted = false
        var length = 0
        while (true) {
            val code = input.read()
            if (code == -1) {
                eof = true
                if (quoted) error("Unterminated quoted CSV field")
                if (!fieldStarted && result.isEmpty() && value.isEmpty()) return null
                result.add(value.toString())
                return result
            }
            if (++length > MAX_RECORD_CHARS) error("CSV record exceeds size limit")
            val ch = code.toChar()
            if (quoted) {
                if (ch == '"') {
                    val lookAhead=input.read()
                    if (lookAhead == '"'.code) {
                        value.append('"')
                    } else {
                        quoted = false
                        closedQuote = true
                        if (lookAhead != -1) input.unread(lookAhead)
                    }
                } else {
                    value.append(ch)
                }
                continue
            }
            when (ch) {
                '"' -> {
                    require(!fieldStarted && !closedQuote) { "Malformed quoted CSV field" }
                    quoted = true; fieldStarted = true
                }
                ',' -> {
                    result.add(value.toString());value.setLength(0)
                    fieldStarted = false;closedQuote = false
                }
                '\r','\n' -> {
                    if(ch=='\r') {
                        val next=input.read()
                        if(next != '\n'.code && next != -1)input.unread(next)
                    }
                    result.add(value.toString())
                    return result
                }
                else -> {
                    require(!closedQuote) { "Unexpected character after quoted CSV field" }
                    value.append(ch);fieldStarted=true
                }
            }
        }
    }
}

/** Maps the CoreMetrics CSV header (not positional guesses) to typed rows. */
internal class CsvSessionColumns(header:List<String>) {
    private val indices=header.mapIndexed { index,name ->
        name.trim().removePrefix("\uFEFF").lowercase() to index
    }.toMap()
    init {
        require(indices.containsKey("sample_epoch_ms")) {
            "Not a CoreMetrics CSV: missing sample_epoch_ms header"
        }
        require(indices.containsKey("cpu_temp_c") || indices.containsKey("gpu_temp_c")) {
            "Not a CoreMetrics telemetry CSV"
        }
    }
    fun field(row:List<String>,key:String):String? =
        indices[key]?.let { row.getOrNull(it)?.ifBlank { null } }
    fun number(row:List<String>,key:String):Double? =
        field(row,key)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    fun integer(row:List<String>,key:String):Long? =
        field(row,key)?.toLongOrNull()
    fun time(row:List<String>):Long =
        (integer(row,"sample_epoch_ms") ?: error("Invalid timestamp"))
            .also { require(it in 1_000_000_000_000L..4_102_444_800_000L) { "Timestamp out of range" } }
    val width = header.size
}

/**
 * Keep original recording outcome when importing historical CSVs. Using only
 * "IMPORTED" erased whether a source recording was killed/interrupted.
 * This is metadata only: no SQLite migration and no trust in arbitrary values.
 */
internal object ImportedSessionStatus {
    private val recognized=setOf(
        "COMPLETED","INTERRUPTED_RECOVERED","INTERRUPTED","FAILED","RECORDING")
    fun store(value:String?):String {
        val source=value?.trim()?.uppercase(java.util.Locale.US)
            ?.removePrefix("IMPORTED_") ?: "UNKNOWN"
        return "IMPORTED_"+source.takeIf { it in recognized }.orEmpty()
            .ifBlank { "UNKNOWN" }
    }
}
