package com.locutus5382.coremetrics.data

/** Manually supplied benchmark context; empty is unknown, not System/1x. */
object SessionMetadata {
    private fun cleaned(s:String?):String? {
        val trimmed=s?.trim()?.filter{it.code>=32 && it.code!=127}
            ?.take(48).orEmpty()
        if(trimmed.isBlank())return null
        // Never pass CSV formula-like custom strings through as an executable
        // spreadsheet expression when an export is opened externally.
        return trimmed.takeIf{it.firstOrNull() !in listOf('=','+','-','@')}
    }
    /** Manually entered game/track labels: preserve Unicode and punctuation,
     * remove all control characters consistently with the CSV importer. */
    fun note(s:String?):String?=s?.trim()
        ?.filter{it.code>=32 && it.code!=127 && it.code !in 0x80..0x9F}
        ?.take(64)?.trim()?.takeIf{it.isNotBlank()}

    fun driver(s:String?):String? {
        val value=cleaned(s)?:return null
        return if(value=="System" || value=="Turnip" ||
            value.startsWith("Custom: ") && value.length>8)value else null
    }
    fun resolution(s:String?):String? {
        val value=cleaned(s)?:return null
        return if(value=="Handheld 1x" || value=="Docked" ||
            value.startsWith("Custom: ") && value.length>8)value else null
    }
}
