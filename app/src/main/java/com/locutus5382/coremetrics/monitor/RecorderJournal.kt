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
    private fun file(context: Context): File =
        File(context.filesDir, "coremetrics-recorder-journal.txt")
    private fun previous(context: Context): File =
        File(context.filesDir, "coremetrics-recorder-journal.old")

    @Synchronized fun event(context: Context, name: String, details: String = "") {
        runCatching {
            val f=file(context)
            // Rename instead of rereading/rewriting 64 KB on the service
            // main thread. If rotation fails, preserve the current log.
            if(f.exists() && f.length()>48_000L) {
                val old=previous(context)
                if(!old.exists() || old.delete())f.renameTo(old)
            }
            f.appendText(System.currentTimeMillis().toString()+" | "+
                name.replace('|','/').take(48)+" | "+
                details.take(160).replace('\n',' ').replace('\r',' ')+"\n")
        }
    }
    @Synchronized fun read(context: Context): String = runCatching {
        val lines=buildList {
            for(f in listOf(previous(context),file(context))) {
                if(f.exists())addAll(f.readLines().takeLast(160))
            }
        }
        lines.takeLast(160).joinToString("\n").ifEmpty{"No lifecycle events yet"}
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
                        " | lastPssMiB=" + record.pss / 1024 +
                        " (Android last sampled, not size at termination)"
                }
        } catch (e: Exception) { "Process exit history unavailable: " + e.javaClass.simpleName }
        val liveMemory=try {
            val mgr=context.getSystemService(ActivityManager::class.java)
            val info=ActivityManager.MemoryInfo()
            mgr.getMemoryInfo(info)
            "System RAM at report time: available="+(info.availMem/(1024*1024))+
                " MiB / total="+(info.totalMem/(1024*1024))+
                " MiB, lowMemory="+info.lowMemory+"; historical exits may predate this report"
        }catch(e:Exception){"System memory unavailable: "+e.javaClass.simpleName}
        val version=runCatching {
            context.packageManager.getPackageInfo(context.packageName,0)
                .versionName ?: "unknown"
        }.getOrDefault("unknown")
        return "CoreMetrics "+version+" recorder diagnostics\n" +
            "Current UI PID: " + Process.myPid() + "\n\n" +
            "Recent lifecycle events:\n" + read(context) +
            "\n\n"+liveMemory+"\n\n"+DeviceCapabilityProbe.report(context)+
            "\nAndroid process exit history:\n" + exits + "\n"
    }
}
