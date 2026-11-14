package com.locutus5382.coremetrics.data

/** Preserve numeric CSV values while stopping spreadsheet formula execution. */
object Csv {
    fun escape(value: String): String {
        val first = value.trimStart().firstOrNull()
        val dangerous = first == '=' || first == '@' ||
            ((first == '+' || first == '-') && value.trim().toDoubleOrNull() == null)
        val safe = if (dangerous) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
