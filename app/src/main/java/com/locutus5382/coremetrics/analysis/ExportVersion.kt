package com.locutus5382.coremetrics.analysis

/** Report-format version, shared by offline PNG/TXT exporters.
 * Remains independent of Android runtime and BuildConfig for JVM unit tests.
 */
internal object ExportVersion {
    /** Report format compatibility stays stable across app updates. */
    const val VERSION="0.9.6"
    const val APP_VERSION="0.9.6.6"
    fun sourceLabel()="CoreMetrics app v"+APP_VERSION+
        " · report format v"+VERSION
}
