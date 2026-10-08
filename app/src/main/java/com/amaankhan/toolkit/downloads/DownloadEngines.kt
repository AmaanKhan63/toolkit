package com.amaankhan.toolkit.downloads

import android.content.Context
import android.util.Base64
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object DownloadRuntime {
    @Synchronized fun initialize(context: Context) {
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        Aria2c.getInstance().init(context)
    }
}

/** One private RPC server per running transfer, never exposed on the network. */
class AriaSession(private val context: Context, private val directory: File, private val torrent: Boolean = false) : AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private var process: Process? = null
    suspend fun start() = withContext(Dispatchers.IO) {
        DownloadRuntime.initialize(context)
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val binary = File(context.applicationInfo.nativeLibraryDir, "libaria2c.so")
        val command = listOf(binary.path, "--no-conf=true", "--enable-rpc=true", "--rpc-listen-all=false", "--rpc-listen-port=$port", "--rpc-secret=$token",
            "--dir=${directory.path}", "--continue=true", "--auto-file-renaming=false", "--allow-overwrite=false", "--file-allocation=none",
            "--console-log-level=warn", "--summary-interval=0", "--max-concurrent-downloads=1", "--max-connection-per-server=8", "--split=8",
            "--min-split-size=2M", "--max-tries=3", "--connect-timeout=30", "--timeout=60", "--follow-metalink=false", "--follow-torrent=false",
            "--no-netrc=true", "--check-certificate=true", "--ca-certificate=${File(packages, "python/usr/etc/tls/cert.pem").path}",
            "--enable-dht=$torrent", "--enable-dht6=false", "--dht-file-path=${File(directory, "dht.dat").path}", "--listen-port=49152-65535", "--dht-listen-port=49152-65535",
            "--enable-peer-exchange=$torrent", "--bt-enable-lpd=false", "--seed-time=0")
        process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = listOf("aria2c", "python", "ffmpeg").joinToString(":") { File(packages, "$it/usr/lib").path }
        }.start()
        // Drain output without retaining URLs, RPC secrets or source credentials in logs.
        thread(isDaemon = true, name = "aria-output") { runCatching { process?.inputStream?.bufferedReader()?.use { reader -> while (reader.readLine() != null) Unit } } }
        var connected = false
        repeat(100) {
            currentCoroutineContext().ensureActive()
            if (!connected) { connected = runCatching { rpc("getVersion"); true }.getOrDefault(false); if (!connected) delay(100) }
        }
        check(connected) { "The download engine could not start on this device." }
    }
    fun rpc(method: String, vararg params: Any): Any {
        val connection = URL("http://127.0.0.1:$port/jsonrpc").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.connectTimeout = 1000; connection.readTimeout = 5000; connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("jsonrpc", "2.0").put("id", "toolkit").put("method", "aria2.$method")
                .put("params", JSONArray().put("token:$token").apply { params.forEach { put(it) } }).toString()
            connection.outputStream.use { it.write(body.toByteArray()) }
            val result = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            if (result.has("error")) error("Download engine: ${result.getJSONObject("error").optString("message").take(200)}")
            return result.get("result")
        } finally { connection.disconnect() }
    }
    override fun close() {
        runCatching { rpc("forceShutdown") }
        process?.let { if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly() }
    }
}

class DownloadEngines(private val store: DownloadStore) {
    private val context = store.context
    suspend fun aria(entry: DownloadEntry, limit: () -> Long) = withContext(Dispatchers.IO) {
        val directory = store.directory(entry.id)
        val data = File(directory, "data").apply { mkdirs() }
        val settings = JSONObject(entry.options)
        val torrent = entry.kind == DownloadKind.TORRENT.name
        AriaSession(context, data, torrent).use { engine ->
            engine.start()
            val options = JSONObject().put("dir", data.path).put("max-download-limit", limit().toString())
            if (!torrent) options.put("out", safeDownloadName(entry.name))
            else {
                options.put("seed-time", settings.optInt("seedMinutes", 0).coerceIn(0, 1440).toString())
                    .put("seed-ratio", "0.0").put("max-upload-limit", (settings.optLong("uploadKiB", 256).coerceAtLeast(1) * 1024).toString())
                    .put("enable-peer-exchange", "true")
                if (settings.optString("selected").isNotBlank()) options.put("select-file", settings.getString("selected"))
                else options.put("pause", "true")
            }
            val metadata = File(directory, "source.torrent")
            var gid = if (torrent && metadata.exists()) {
                engine.rpc("addTorrent", Base64.encodeToString(metadata.readBytes(), Base64.NO_WRAP), JSONArray(), options) as String
            } else if (torrent) {
                options.remove("pause")
                options.put("bt-save-metadata", "true").put("pause-metadata", "true").put("follow-torrent", "true")
                engine.rpc("addUri", JSONArray().put(entry.source), options) as String
            } else engine.rpc("addUri", JSONArray().put(entry.source), options) as String
            var lastLimit = -1L
            while (true) {
                currentCoroutineContext().ensureActive()
                val status = engine.rpc("tellStatus", gid) as JSONObject
                val followed = status.optJSONArray("followedBy")
                if (followed != null && followed.length() > 0) {
                    gid = followed.getString(0)
                    val candidates = data.listFiles()?.filter { it.extension == "torrent" }.orEmpty()
                    require(candidates.size == 1) { "Torrent metadata could not be located. Try a .torrent file." }
                    candidates.single().copyTo(metadata, overwrite = true)
                    continue
                }
                val list = status.optJSONArray("files") ?: JSONArray()
                if (torrent && settings.optString("selected").isBlank() && metadata.exists() && list.length() > 0 && status.optString("status") == "paused") {
                    val manifest = JSONArray()
                    for (i in 0 until list.length()) {
                        val file = list.getJSONObject(i); val path = File(file.getString("path"))
                        require(path.canonicalPath.startsWith(data.canonicalPath + File.separator)) { "Unsafe torrent file path." }
                        manifest.put(JSONObject().put("index", file.getInt("index")).put("name", path.relativeTo(data).path).put("bytes", file.optLong("length")))
                    }
                    val current = store.dao.get(entry.id) ?: return@withContext
                    store.dao.update(current.copy(files = manifest.toString(), state = "SELECT_FILES", detail = "Choose files to download", speed = 0))
                    return@withContext
                }
                val nextLimit = limit()
                if (lastLimit != nextLimit) { engine.rpc("changeGlobalOption", JSONObject().put("max-overall-download-limit", nextLimit.toString())); lastLimit = nextLimit }
                val total = status.optLong("totalLength"); val bytes = status.optLong("completedLength"); val speed = status.optLong("downloadSpeed")
                val state = status.optString("status")
                val text = when { torrent && total == 0L -> "Finding peers & metadata"; bytes >= total && total > 0 && state == "active" -> "Seeding · ${status.optLong("uploadSpeed") / 1024} KB/s up"; else -> "Downloading" }
                store.dao.progress(entry.id, bytes, total, speed, if (total > 0) ((bytes * 100) / total).toInt() else -1, text)
                if (state == "error") error("Transfer failed (aria2 ${status.optString("errorCode")}). Check the link, connection and free storage, then retry.")
                if (state == "complete") {
                    val outputs = JSONArray()
                    for (i in 0 until list.length()) {
                        val f = list.getJSONObject(i)
                        if (f.optString("selected", "true") == "true") {
                            val file = File(f.getString("path"))
                            require(file.canonicalPath.startsWith(data.canonicalPath + File.separator)) { "Unsafe output path." }
                            check(file.isFile && file.length() == f.optLong("length")) { "The downloaded file is incomplete." }
                            outputs.put(JSONObject().put("path", file.relativeTo(directory).path).put("name", file.relativeTo(data).path).put("bytes", file.length()))
                        }
                    }
                    check(outputs.length() > 0) { "No files were downloaded." }
                    store.dao.get(entry.id)?.let { store.dao.update(it.copy(ready = true, files = outputs.toString(), speed = 0)) }
                    return@withContext
                }
                delay(750)
            }
        }
    }
    suspend fun video(entry: DownloadEntry, limit: Long) = withContext(Dispatchers.IO) {
        DownloadRuntime.initialize(context)
        val directory = store.directory(entry.id)
        val data = File(directory, "data").apply { mkdirs() }
        val settings = JSONObject(entry.options)
        val request = YoutubeDLRequest(entry.source).apply {
            addOption("--ignore-config"); addOption("--no-playlist"); addOption("--no-mtime"); addOption("--restrict-filenames")
            addOption("--continue"); addOption("--no-overwrites"); addOption("--concurrent-fragments", 8)
            addOption("--newline"); addOption("--progress"); addOption("--progress-template", "download:TKPROG:%(progress.downloaded_bytes)s:%(progress.total_bytes,progress.total_bytes_estimate)s:%(progress.speed)s:%(progress.eta)s")
            addOption("--paths", data.path); addOption("-o", "%(title).120B [%(id)s].%(ext)s")
            addCommands(listOf("--print-to-file", "after_move:filepath", File(directory, "outputs.txt").path))
            val audio = settings.optString("container") in setOf("mp3", "m4a", "opus")
            val height = settings.optInt("height", 0)
            val selectedFormat = settings.optString("format")
            addOption("-f", if (selectedFormat.isNotBlank()) selectedFormat else if (audio) "bestaudio/best" else if (height > 0) "bv*[height<=$height]+ba/b[height<=$height]" else "bv*+ba/b")
            if (audio) { addOption("-x"); addOption("--audio-format", settings.getString("container")); addOption("--audio-quality", "0") }
            else if (settings.optString("container") == "mp4") { addOption("--merge-output-format", "mp4"); addOption("--remux-video", "mp4") }
            else { addOption("--merge-output-format", "mkv") }
            if (limit > 0) addOption("--limit-rate", limit.toString())
        }
        try {
            var updated = 0L
            runMedia(context, request) { line ->
                val now = System.currentTimeMillis()
                if (line.startsWith("TKPROG:") && now - updated >= 500) {
                    val values = line.split(':'); val bytes = values.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0
                    val total = values.getOrNull(2)?.toDoubleOrNull()?.toLong() ?: 0; val speed = values.getOrNull(3)?.toDoubleOrNull()?.toLong() ?: 0
                    val eta = values.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0
                    runBlocking { store.dao.progress(entry.id, bytes, total, speed, if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else -1, if (eta > 0) "Downloading · ${eta}s remaining" else "Downloading / preparing media") }
                    updated = now
                }
            }
            currentCoroutineContext().ensureActive()
            val manifest = File(directory, "outputs.txt")
            val files = if (manifest.isFile) manifest.readLines().filter(String::isNotBlank).distinct().map(::File) else emptyList()
            check(files.isNotEmpty()) { "The source returned no downloadable media." }
            val outputs = JSONArray()
            files.forEach { file ->
                require(file.canonicalPath.startsWith(data.canonicalPath + File.separator) && file.isFile) { "Media output is unavailable." }
                outputs.put(JSONObject().put("path", file.relativeTo(directory).path).put("name", file.name).put("bytes", file.length()))
            }
            store.dao.get(entry.id)?.let { store.dao.update(it.copy(ready = true, files = outputs.toString(), speed = 0, bytes = files.sumOf(File::length), total = files.sumOf(File::length))) }
        } finally { /* runMedia releases the complete process group, including FFmpeg. */ }
    }
    suspend fun inspectVideo(url: String): JSONObject = withContext(Dispatchers.IO) {
        validateDownloadUrl(url); DownloadRuntime.initialize(context)
        val request = YoutubeDLRequest(url).apply { addOption("--ignore-config"); addOption("--flat-playlist"); addOption("--dump-single-json"); addOption("--playlist-end", 500); addOption("--socket-timeout", 30) }
        JSONObject(runMedia(context, request, capture = true))
    }
}
