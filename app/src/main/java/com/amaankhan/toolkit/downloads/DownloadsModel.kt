package com.amaankhan.toolkit.downloads

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amaankhan.toolkit.ToolkitApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File

class DownloadsModel(app: Application) : AndroidViewModel(app) {
    val store = (app as ToolkitApplication).downloads
    val telegram = (app as ToolkitApplication).telegram
    val entries = store.dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val inspection = MutableStateFlow<JSONObject?>(null)
    fun action(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message?.replace(Regex("https?://\\S+"), "[source]")?.take(240) ?: "Unable to complete this action." }
    } }
    fun inspect(url: String) { if (busy.value) return; busy.value = true; inspection.value = null; action {
        try { inspection.value = DownloadEngines(store).inspectVideo(url) }
        catch (e: CancellationException) { throw e }
        catch (e: MediaFailure) { error.value = e.message }
        catch (_: Exception) { error.value = "Unable to inspect this link. Check that it is public and supported by yt-dlp." }
        finally { busy.value = false }
    } }
    fun saveErrorReport(id: String, uri: Uri) = action {
        try {
            MediaReports.export(store.context, id, uri)
            notice.value = "Error report saved. Attach the text file with your screenshot when reporting the problem."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error.value = "The error report could not be saved. It may have expired, or the selected location is unavailable. Choose another report or location." }
    }
    suspend fun recentErrorReports(): List<Pair<String, Long>> = withContext(Dispatchers.IO) { MediaReports.list(store.context) }
    fun updateMediaEngine() { if (busy.value) return; busy.value = true; action {
        try {
            require(store.dao.all().none { it.kind == "VIDEO" && it.state in setOf("RUNNING", "QUEUED") }) { "Pause media downloads before updating their engine." }
            val engine = com.yausername.youtubedl_android.YoutubeDL.getInstance()
            try {
                DownloadRuntime.initialize(store.context)
                engine.updateYoutubeDL(store.context)
                val version = engine.version(store.context)?.takeIf { it.matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}[A-Za-z0-9._-]*")) }
                notice.value = if (version == null) "Media engine update completed." else "Media engine is up to date ($version)."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = MediaReports.failure(store.context, e, MediaTrace(), "engine update", "").message }
        } finally { busy.value = false }
    } }
    fun queue(kind: DownloadKind, sources: List<Pair<String, String>>, destination: String, options: JSONObject, torrentUri: Uri? = null, onDone: () -> Unit) {
        if (busy.value) return
        busy.value = true
        action {
            try {
                require(sources.isNotEmpty() && sources.size <= 500) { "Choose between 1 and 500 downloads." }
                require(destination.startsWith("content://")) { "Choose a folder first." }
                if (kind in setOf(DownloadKind.DIRECT, DownloadKind.VIDEO)) sources.forEach { validateDownloadUrl(it.first) }
                for ((url, name) in sources) {
                    val entry = store.enqueue(kind, url, name, destination, options)
                    if (torrentUri != null) try {
                        val file = File(store.directory(entry.id), "source.torrent")
                        store.context.contentResolver.openInputStream(torrentUri)?.use { input -> file.outputStream().use { target ->
                            val buffer = ByteArray(32768); var total = 0
                            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 8 * 1024 * 1024) { "Torrent metadata is too large (8 MB maximum)." }; target.write(buffer, 0, n) }
                        } } ?: error("Unable to read torrent file.")
                        store.dao.state(entry.id, "QUEUED", "Reading torrent metadata")
                    } catch (e: Exception) { store.dao.remove(entry.id); store.directory(entry.id).deleteRecursively(); throw e }
                }
                withContext(Dispatchers.Main) { DownloadService.start(store.context); onDone() }
            } finally { busy.value = false }
        }
    }
    fun resume(entry: DownloadEntry, folder: String? = null) = action {
        require(entry.state in setOf("PAUSED", "FAILED")) { "This transfer cannot be resumed yet." }
        store.dao.update(entry.copy(state = "QUEUED", error = "", destination = folder ?: entry.destination, detail = "Waiting"))
        withContext(Dispatchers.Main) { DownloadService.start(store.context) }
    }
    fun selectTorrent(entry: DownloadEntry, indices: Set<Int>) = action {
        require(indices.isNotEmpty()) { "Select at least one file." }
        val valid = jsonObjects(entry.files).map { it.getInt("index") }.toSet()
        require(valid.containsAll(indices)) { "Invalid file selection." }
        val options = JSONObject(entry.options).put("selected", indices.sorted().joinToString(","))
        store.dao.update(entry.copy(options = options.toString(), state = "QUEUED", detail = "Waiting"))
        withContext(Dispatchers.Main) { DownloadService.start(store.context) }
    }
    fun control(entry: DownloadEntry, action: String) {
        DownloadService.start(store.context, action, entry.id)
    }
    fun remove(entry: DownloadEntry) = action {
        require(entry.state in setOf("COMPLETE", "CANCELED")) { "Cancel the transfer before removing it." }
        store.dao.remove(entry.id)
    }
}
