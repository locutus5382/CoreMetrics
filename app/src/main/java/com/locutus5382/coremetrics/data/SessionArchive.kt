package com.locutus5382.coremetrics.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * User-initiated, portable, additive ZIP archive. Stores CSV telemetry in
 * original form plus manifest metadata, manual tags, test profiles and links.
 * Never writes to an arbitrary ZIP path and NEVER replaces existing sessions.
 *
 * Restoration is a single outer SQL transaction: a failed CRC, invalid row,
 * missing entry or link aborts every imported record. Archive restore uses
 * the existing transactional CSV importer as a nested SQLite transaction.
 */
object SessionArchive {
    private const val MAGIC="CoreMetrics-Portable-Archive"
    private const val FORMAT=1
    private const val MAX_SESSIONS=200
    private const val MAX_PROFILES=200
    private const val MAX_LINKS=2500
    private const val MAX_TOTAL_SAMPLES=250_000L
    private const val MAX_MANIFEST_BYTES=800_000

    data class Summary(val sessions:Int,val samples:Long,
        val profiles:Int,val links:Int)

    private fun nullable(raw:JSONObject,key:String):String? =
        if(raw.isNull(key) || !raw.has(key))null
        else raw.optString(key).takeIf{it.isNotBlank()}

    private fun noActiveRecording(store:SessionStore) {
        val count=store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM sessions WHERE status='RECORDING'",null
        ).use { c->if(c.moveToFirst())c.getInt(0)else 0 }
        require(count==0) {
            "Stop active recording before archiving or restoring sessions"
        }
    }

    fun export(store:SessionStore,out:OutputStream):Summary {
        noActiveRecording(store)
        val number=store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM sessions",null
        ).use{c->if(c.moveToFirst())c.getInt(0)else 0}
        require(number<=MAX_SESSIONS) {
            "Archive currently supports at most $MAX_SESSIONS sessions; no partial export"
        }
        val sessions=store.list(MAX_SESSIONS).sortedBy{it.id}
        require(sessions.size==number) {"Session list changed during archive preparation"}
        val profiles=store.quickProfiles()
        val links=store.links()
        require(profiles.size<=MAX_PROFILES && links.size<=MAX_LINKS){
            "Too many presets/links for this archive format"
        }
        val sampleTotal=sessions.sumOf{it.count.toLong()}
        require(sampleTotal<=MAX_TOTAL_SAMPLES &&
            sessions.all{it.count<=SessionStore.MAX_CSV_IMPORT_SAMPLES}) {
            "Archive sample safety limit exceeded; export individual CSVs instead"
        }
        val manifest=JSONObject()
            .put("magic",MAGIC).put("format",FORMAT)
            .put("exported_at_ms",System.currentTimeMillis())
            .put("sessions",JSONArray().also{arr->
                sessions.forEach{s->arr.put(JSONObject()
                    .put("id",s.id).put("count",s.count)
                    .put("started_at",s.startedAt)
                    .put("ended_at",s.endedAt?:JSONObject.NULL)
                    .put("label",s.label).put("status",s.status)
                    .put("note",s.note?:JSONObject.NULL)
                    .put("configuration",s.configuration)
                    .put("graphics_driver",s.graphicsDriver?:JSONObject.NULL)
                    .put("resolution_scale",s.resolutionScale?:JSONObject.NULL)
                    .put("performance_mode",s.performanceMode?:JSONObject.NULL)
                    .put("fan_mode",s.fanMode?:JSONObject.NULL)
                    .put("cluster_tune_mode",s.clusterTuneMode?:JSONObject.NULL)
                    .put("profile_name",s.profileName?:JSONObject.NULL)
                )}
            }).put("profiles",JSONArray().also{arr->
                profiles.forEach{p->arr.put(JSONObject()
                    .put("name",p.name).put("game_note",p.gameNote?:JSONObject.NULL)
                    .put("graphics_driver",p.graphicsDriver?:JSONObject.NULL)
                    .put("resolution_scale",p.resolutionScale?:JSONObject.NULL)
                    .put("performance_mode",p.performanceMode?:JSONObject.NULL)
                    .put("fan_mode",p.fanMode?:JSONObject.NULL)
                    .put("cluster_tune_mode",p.clusterTuneMode?:JSONObject.NULL)
                )}
            }).put("links",JSONArray().also{arr->
                links.forEach{l->arr.put(JSONObject()
                    .put("a",l.firstId).put("b",l.secondId)
                    .put("created_at",l.createdAt))}
            }).put("device_settings",JSONObject().apply {
                val defaults=store.recordingDefaults()
                put("label",defaults.label)
                put("configuration",defaults.configuration)
                put("interval_ms",defaults.intervalMs)
                put("hud_enabled",defaults.hudEnabled)
                put("hud_options_json",store.hudOptionsJson()?:JSONObject.NULL)
                put("active_profile_id",store.activeProfileId())
            })
        val header=manifest.toString().toByteArray(StandardCharsets.UTF_8)
        require(header.size<=MAX_MANIFEST_BYTES) {
            "Archive metadata exceeds size limit; nothing was written"
        }
        ZipOutputStream(out.buffered()).use{zip->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(header)
            zip.closeEntry()
            for(s in sessions) {
                if(s.count==0)continue
                zip.putNextEntry(ZipEntry("sessions/${s.id}.csv"))
                val writer=OutputStreamWriter(zip,StandardCharsets.UTF_8)
                store.exportCsv(s.id,writer)
                writer.flush()
                zip.closeEntry()
            }
        }
        return Summary(sessions.size,sampleTotal,profiles.size,links.size)
    }

    private fun readManifest(zip:ZipInputStream):JSONObject {
        val first=zip.nextEntry ?:error("Empty archive")
        require(first.name=="manifest.json" && !first.isDirectory) {
            "Not a CoreMetrics archive (missing first manifest)"
        }
        val buffer=ByteArray(8192)
        val output=java.io.ByteArrayOutputStream()
        while(true) {
            val n=zip.read(buffer)
            if(n<0)break
            require(output.size()+n<=MAX_MANIFEST_BYTES) {
                "Manifest exceeds the permitted size"
            }
            output.write(buffer,0,n)
        }
        zip.closeEntry()
        return JSONObject(output.toString("UTF-8")).also {
            require(it.optString("magic")==MAGIC && it.optInt("format")==FORMAT) {
                "Unsupported or invalid archive format"
            }
        }
    }

    /** Additive restore. Existing recordings are never overwritten or deleted.
     * Historical session IDs and association IDs are remapped atomically.
     * Device/HUD settings are archived for reference, but deliberately NOT
     * applied to an unrelated phone/ROM during restore.
     */
    fun restore(store:SessionStore,input:InputStream):Summary {
        noActiveRecording(store)
        ZipInputStream(input.buffered()).use{zip->
            val manifest=readManifest(zip)
            val sessions=manifest.getJSONArray("sessions")
            val profiles=manifest.getJSONArray("profiles")
            val links=manifest.getJSONArray("links")
            require(sessions.length()<=MAX_SESSIONS &&
                profiles.length()<=MAX_PROFILES && links.length()<=MAX_LINKS){
                "Archive exceeds safe entry limits"
            }
            val seen=HashSet<Long>()
            var declared=0L
            for(i in 0 until sessions.length()) {
                val s=sessions.getJSONObject(i)
                val oldId=s.getLong("id")
                val count=s.getInt("count")
                require(oldId>0 && seen.add(oldId) &&
                    count in 0..SessionStore.MAX_CSV_IMPORT_SAMPLES) {
                    "Duplicate/invalid archive session"
                }
                declared+=count
            }
            require(declared<=MAX_TOTAL_SAMPLES) {
                "Archive exceeds total-sample safety limit"
            }
            val db=store.writableDatabase
            val newIds=HashMap<Long,Long>()
            db.beginTransaction()
            try {
                for(i in 0 until sessions.length()) {
                    val source=sessions.getJSONObject(i)
                    val originalId=source.getLong("id")
                    val count=source.getInt("count")
                    val newId=if(count>0) {
                        val entry=zip.nextEntry ?:error(
                            "Missing session CSV for archived #"+originalId)
                        require(!entry.isDirectory &&
                            entry.name=="sessions/${originalId}.csv") {
                            "Unexpected archive entry; restore cancelled"
                        }
                        val imported=store.importCsv(
                            InputStreamReader(zip,StandardCharsets.UTF_8))
                        zip.closeEntry() // verifies ZIP CRC of consumed entry
                        require(store.storedSampleCount(imported)==count.toLong()) {
                            "Session sample count mismatch; restore cancelled"
                        }
                        imported
                    }else {
                        db.insertOrThrow("sessions",null,ContentValues().apply {
                            put("started_at",source.getLong("started_at"))
                            put("label","Restored empty session")
                            put("configuration","Custom")
                            put("status","IMPORTED_UNKNOWN")
                        })
                    }
                    val originalStatus=source.optString("status","UNKNOWN")
                    val safeStatus=ImportedSessionStatus.store(originalStatus)
                    val label=source.optString("label","Restored session")
                        .take(64)
                    val note=SessionMetadata.note(nullable(source,"note"))
                    val metadata=ContentValues().apply{
                        put("label",label)
                        put("game_note",note)
                        put("status",safeStatus)
                        put("started_at",source.getLong("started_at"))
                        if(source.isNull("ended_at"))putNull("ended_at")
                        else put("ended_at",source.getLong("ended_at"))
                        put("configuration",source.optString(
                            "configuration","Custom").take(40))
                        put("graphics_driver",SessionMetadata.driver(
                            nullable(source,"graphics_driver")))
                        put("resolution_scale",SessionMetadata.resolution(
                            nullable(source,"resolution_scale")))
                        put("performance_mode",nullable(source,"performance_mode"))
                        put("fan_mode",nullable(source,"fan_mode"))
                        put("cluster_tune_mode",nullable(source,"cluster_tune_mode"))
                        put("profile_name",nullable(source,"profile_name")?.take(48))
                    }
                    require(db.update("sessions",metadata,"id=?",
                        arrayOf(newId.toString()))==1) {
                        "Unable to restore original session metadata"
                    }
                    newIds[originalId]=newId
                }
                require(zip.nextEntry==null) {
                    "Unexpected trailing archive data"
                }
                // Import presets as NEW shortcuts. Name conflicts never
                // overwrite the user's existing profile settings.
                val occupied=store.quickProfiles().map{it.name}.toMutableSet()
                for(i in 0 until profiles.length()) {
                    val p=profiles.getJSONObject(i)
                    val original=p.getString("name")
                    require(original.isNotBlank() && original.length<=48) {
                        "Invalid archived profile name"
                    }
                    var candidate=original
                    var suffix=1
                    while(candidate in occupied) {
                        val extra=" (restore "+suffix++ +")"
                        candidate=original.take(48-extra.length)+extra
                        require(suffix<1000){"Too many conflicting profile names"}
                    }
                    store.saveQuickProfile(name=candidate,
                        gameNote=nullable(p,"game_note"),
                        graphicsDriver=nullable(p,"graphics_driver"),
                        resolutionScale=nullable(p,"resolution_scale"),
                        performanceMode=nullable(p,"performance_mode"),
                        fanMode=nullable(p,"fan_mode"),
                        clusterTuneMode=nullable(p,"cluster_tune_mode"))
                    occupied.add(candidate)
                }
                for(i in 0 until links.length()) {
                    val link=links.getJSONObject(i)
                    val a=newIds[link.getLong("a")]
                        ?:error("Association references missing archived session")
                    val b=newIds[link.getLong("b")]
                        ?:error("Association references missing archived session")
                    require(a!=b){"Invalid self-referencing association"}
                    db.insertOrThrow("session_links",null,ContentValues().apply{
                        put("first_id",minOf(a,b))
                        put("second_id",maxOf(a,b))
                        put("created_at",link.getLong("created_at"))
                    })
                }
                db.setTransactionSuccessful()
            }finally {db.endTransaction()}
            return Summary(newIds.size,declared,profiles.length(),links.length())
        }
    }
}
