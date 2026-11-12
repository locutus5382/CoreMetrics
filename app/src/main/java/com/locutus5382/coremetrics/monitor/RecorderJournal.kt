package com.locutus5382.coremetrics.monitor

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-private bounded recorder journal; survives :monitor process death.
 * Only the monitor service writes; UI may read even when service is absent.
 * Logs transition metadata, never user-entered game labels.
 */
object RecorderJournal {
    private fun file(context: Context): File = File(context.filesDir, "coremetrics-recorder-journal.txt")

    @Synchronized fun event(context: Context, name: String, details: String = "") {
        runCatching {
            val f = file(context)
            if (f.exists() && f.length() > 32_000) {
                f.writeText(f.readLines().takeLast(75).joinToString("\n") + "\n")
            }
            f.appendText(System.currentTimeMillis().toString() + " | " +
                name.replace('|','/') + " | " + details.take(160).replace('\n',' ') + "\n")
        }
    }
    fun read(context: Context): String = runCatching {
        file(context).takeIf { it.exists() }?.readLines()?.takeLast(80)
            ?.joinToString("\n") ?: "No lifecycle events yet"
    }.getOrDefault("Journal unavailable")

    fun report(context: Context): String {
        val exits = try {
            val mgr = context.getSystemService(ActivityManager::class.java)
            mgr.getHistoricalProcessExitReasons(context.packageName,0,16)
                .joinToString("\n") { record ->
                    val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US)
                        .format(Date(record.timestamp))
                    val reason = when (record.reason) {
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                        ApplicationExitInfo.REASON_ANR -> "ANR"
                        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                        else -> "OTHER_" + record.reason
                    }
                    t + " | " + record.processName + " | pid=" + record.pid +
                        " | " + reason + " | importance=" + record.importance +
                        " | pssKiB=" + record.pss / 1024
                }
        } catch (e: Exception) { "Process exit history unavailable: " + e.javaClass.simpleName }
        return "CoreMetrics v0.2 recorder diagnostics\n" +
            "Current UI PID: " + Process.myPid() + "\n\n" +
            "Recent lifecycle events:\n" + read(context) +
            "\n\nAndroid process exit history:\n" + exits + "\n"
    }
}
