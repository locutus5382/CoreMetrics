package com.locutus5382.coremetrics.analysis

/** Report-format version, shared by offline PNG/TXT exporters.
 * Remains independent of Android runtime and BuildConfig for JVM unit tests.
 */
internal object ExportVersion {
    /** Report format compatibility stays stable across app updates. */
    const val VERSION="0.9.9"
    const val APP_VERSION="0.9.9.3"
    fun sourceLabel()="CoreMetrics app v"+APP_VERSION+
        " · report format v"+VERSION
}
