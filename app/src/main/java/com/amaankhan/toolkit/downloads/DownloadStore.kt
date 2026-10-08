package com.amaankhan.toolkit.downloads

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

enum class DownloadKind(val label: String) { VIDEO("Video & audio"), DIRECT("Direct link"), TORRENT("Torrent / magnet"), TELEGRAM("Telegram") }
enum class TransferState { QUEUED, RUNNING, SELECT_FILES, SAVING, PAUSED, COMPLETE, FAILED, CANCELED }

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: String, val source: String, val name: String, val destination: String,
    val options: String = "{}", val state: String = TransferState.QUEUED.name,
    val created: Long = System.currentTimeMillis(), val bytes: Long = 0, val total: Long = 0,
    val speed: Long = 0, val progress: Int = -1, val detail: String = "Waiting", val error: String = "",
    val ready: Boolean = false, val files: String = "[]", val exports: String = "{}", val pendingUri: String = "",
)

@Dao interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY created DESC") fun observe(): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads ORDER BY created") suspend fun all(): List<DownloadEntry>
    @Query("SELECT * FROM downloads WHERE id=:id") suspend fun get(id: String): DownloadEntry?
    @Insert suspend fun insert(entry: DownloadEntry)
    @Update suspend fun update(entry: DownloadEntry)
    @Query("UPDATE downloads SET state=:state, detail=:detail, speed=0, error=:error WHERE id=:id")
    suspend fun state(id: String, state: String, detail: String, error: String = "")
    @Query("UPDATE downloads SET bytes=:bytes,total=:total,speed=:speed,progress=:progress,detail=:detail WHERE id=:id")
    suspend fun progress(id: String, bytes: Long, total: Long, speed: Long, progress: Int, detail: String)
    @Query("UPDATE downloads SET state='PAUSED',speed=0,detail='Interrupted. Resume to continue.' WHERE state IN ('RUNNING','SAVING')")
    suspend fun recover()
    @Query("DELETE FROM downloads WHERE id=:id") suspend fun remove(id: String)
}
@Database(entities = [DownloadEntry::class], version = 1, exportSchema = true)
abstract class DownloadDatabase : RoomDatabase() { abstract fun queue(): DownloadDao }

class DownloadStore(val context: Context) {
    val database = Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db").build()
    val dao = database.queue()
    private val prefs = context.getSharedPreferences("download-settings", Context.MODE_PRIVATE)
    var defaultFolder: String
        get() = prefs.getString("folder", "").orEmpty()
        set(value) { prefs.edit().putString("folder", value).apply() }
    var totalLimitKiB: Long
        get() = prefs.getLong("limit", 0)
        set(value) { prefs.edit().putLong("limit", value.coerceIn(0, 1_000_000)).apply() }
    // Persistent private staging allows resumption after activity or process recreation.
    fun directory(id: String): File {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "Invalid job" }
        return File(context.noBackupFilesDir, "downloads/$id").apply { mkdirs() }
    }
    suspend fun enqueue(kind: DownloadKind, source: String, name: String, destination: String, options: JSONObject = JSONObject()): DownloadEntry {
        require(destination.startsWith("content://")) { "Choose a download folder first." }
        if (kind == DownloadKind.VIDEO || kind == DownloadKind.DIRECT) validateDownloadUrl(source)
        if (kind == DownloadKind.TORRENT) require(source.startsWith("magnet:?") || source == "local-torrent") { "Use a magnet link or choose a .torrent file." }
        val entry = DownloadEntry(kind = kind.name, source = source, name = safeDownloadName(name), destination = destination, options = options.toString(),
            state = if (source == "local-torrent") "PAUSED" else "QUEUED")
        dao.insert(entry)
        defaultFolder = destination
        return entry
    }
}

fun validateDownloadUrl(value: String) {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
    require(uri != null && uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter a valid HTTP or HTTPS link without login credentials." }
    require(value.length <= 8192 && value.none { it == '\n' || it == '\r' || it == '\u0000' }) { "Invalid link." }
}
fun safeDownloadName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(180).ifBlank { "Download" }
fun jsonObjects(array: String): List<JSONObject> = JSONArray(array).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
