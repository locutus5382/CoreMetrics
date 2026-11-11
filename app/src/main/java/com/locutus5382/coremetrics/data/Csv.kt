package com.locutus5382.coremetrics.data

/** Prevent separator/quote corruption and spreadsheet formula execution on import. */
object Csv {
    fun escape(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@'))
            "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
