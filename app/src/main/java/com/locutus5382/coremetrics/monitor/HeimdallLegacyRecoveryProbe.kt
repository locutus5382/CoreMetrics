package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.locutus5382.coremetrics.domain.LegacyLeaseFields
import com.locutus5382.coremetrics.domain.LegacyRecoveryCandidate
import com.locutus5382.coremetrics.domain.LegacyRecoveryKind
import com.locutus5382.coremetrics.domain.LegacyRecoveryPolicy

/**
 * M2-C read-only recovery inspection. No service, DB helper, UI or quick tile
 * calls it yet. Uses OPEN_READONLY instead of SQLiteOpenHelper so it cannot
 * create a new DB or upgrade schema during an inspection.
 *
 * NOT a reconciler: only RecordingService may decide whether to resume or
 * finalize after validating lease freshness, process lifetime and row status.
 */
internal class HeimdallLegacyRecoveryProbe(private val context: Context) {
    fun inspect(): LegacyRecoveryCandidate {
        val lease = RecorderLease.recover(context)
            ?: return LegacyRecoveryCandidate(LegacyRecoveryKind.NO_VALID_LEASE)
        val fields = LegacyLeaseFields(lease.recording, lease.id, lease.stopRequested)
        if (!fields.recording) return LegacyRecoveryPolicy.interpret(fields, null)
        val databaseFile = context.getDatabasePath("coremetrics.db")
        if (!databaseFile.isFile) return LegacyRecoveryCandidate(LegacyRecoveryKind.UNAVAILABLE)
        return try {
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY).use { db ->
                val status = db.rawQuery("SELECT status FROM sessions WHERE id=?",
                    arrayOf(fields.sessionId.toString())).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                LegacyRecoveryPolicy.interpret(fields, status)
            }
        } catch (_: SQLiteException) {
            LegacyRecoveryCandidate(LegacyRecoveryKind.UNAVAILABLE)
        } catch (_: SecurityException) {
            LegacyRecoveryCandidate(LegacyRecoveryKind.UNAVAILABLE)
        }
    }
}
