package com.amaankhan.toolkit.downloads

import java.io.File
import java.net.URI
import java.util.UUID
import java.util.zip.ZipInputStream

private val reportPattern = Regex("TK-[a-f0-9]{12}")
private val ansi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val urls = Regex("(?i)(?:https?|ftp|file|content)://[^\\s<>\"']+")
private val headers = Regex("""(?i)(?:\\?["'])?(?:authorization|proxy-authorization|set-cookie|cookie)(?:\\?["'])?\s*[:=].*""")
// Drop the rest of a credential-bearing line: escaped quotes and multiword secrets
// must not defeat redaction. The preceding error and structured code remain useful.
private val secrets = Regex("""(?i)((?:\\?["'])?(?:access[_-]?token|refresh[_-]?token|po[_-]?token|token|password|passwd|secret|api[_-]?key|signature|sig)(?:\\?["'])?\s*[:=]\s*).*""")
private const val pathStart = "(?:/(?:data|storage|sdcard|mnt|home|tmp)/|[a-z]:[\\\\/])"
private val quotedPaths = Regex("(?i)(?:\"$pathStart[^\"]*(?:\"|$)|'$pathStart[^']*(?:'|$))")
private val paths = Regex("(?i)$pathStart.*")

internal fun sanitizeDiagnosticText(value: String): String {
    val plain = ansi.replace(value.take(8192).replace("\\/", "/"), "").replace(Regex("[\\p{Cntrl}]"), " ")
    return paths.replace(quotedPaths.replace(secrets.replace(headers.replace(urls.replace(plain, "[URL]"), "[credentials removed]")) {
        it.groupValues[1] + "[removed]"
    }, "[local path]"), "[local path]").take(384)
}

internal fun diagnosticSource(value: String): String = runCatching {
    val uri = URI(value)
    val host = uri.host?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9.-]{1,253}")) } ?: return@runCatching "unknown"
    val candidate = when (host) {
        "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
            uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                ?: uri.path.orEmpty().split('/').getOrNull(2)
        else -> null
    }
    host + if (candidate?.matches(Regex("[A-Za-z0-9_-]{11}")) == true) " video=$candidate" else ""
}.getOrDefault("unknown")

fun mediaReportId(message: String): String? = Regex("(?:^|\\n)Report: (TK-[a-f0-9]{12})(?=\\s|$)").find(message)?.groupValues?.get(1)

/** yt-dlp is a ZIP application prefixed with a Python shebang, not a plain ZIP. */
internal fun readMediaEngineVersion(file: File): String? = runCatching {
    file.inputStream().buffered().use { input ->
        input.mark(256)
        val hasShebang = input.read() == '#'.code && input.read() == '!'.code
        input.reset()
        if (hasShebang) {
            var length = 0
            while (input.read().let { it != -1 && it != '\n'.code }) { if (++length >= 256) return@use null }
        }
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "yt_dlp/version.py") {
                    val buffer = ByteArray(16_384)
                    var size = 0
                    while (size < buffer.size) {
                        val count = zip.read(buffer, size, buffer.size - size)
                        if (count < 0) break
                        size += count
                    }
                    return@use Regex("__version__\\s*=\\s*['\"]([0-9A-Za-z._-]+)['\"]")
                        .find(String(buffer, 0, size, Charsets.UTF_8))?.groupValues?.get(1)
                }
                entry = zip.nextEntry
            }
            null
        }
    }
}.getOrNull()

/** Only selected diagnostic lines enter the ring; titles, destinations and JSON never do. */
internal class MediaTrace {
    private val started = System.nanoTime()
    private val events = ArrayDeque<String>()
    var stage = "initializing"
    var exitCode: Int? = null
    var lastProgress: MediaProgress? = null
        private set
    var progressAtMs: Long? = null
        private set
    var retryEvents = 0
        private set
    var formats = "unknown"
        private set
    val httpStatuses = linkedSetOf<Int>()
    val elapsedMs get() = (System.nanoTime() - started) / 1_000_000

    fun observe(line: String) {
        parseMediaProgress(line)?.let {
            lastProgress = it; progressAtMs = elapsedMs
            stage = if (line.endsWith(":finished")) "processing" else "downloading"
            return
        }
        if (line.startsWith("TKNAME:") || line.startsWith("{")) return
        if (line.startsWith("[youtube]")) stage = "extracting"
        if (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[VideoRemuxer]")) stage = "processing"
        Regex("Downloading \\d+ format\\(s\\): ([A-Za-z0-9_+.-]{1,100})(?:$|\\s)").find(line)?.let { formats = it.groupValues[1] }
        val lower = line.lowercase()
        val relevant = line.startsWith("ERROR:") || line.startsWith("WARNING:") || line.startsWith("[download] Got error:") ||
            line.startsWith("Fatal Python error:") || line.startsWith("CANNOT LINK EXECUTABLE") ||
            Regex("^[A-Za-z_.]*(?:Error|Exception):").containsMatchIn(line) ||
            (line.startsWith("[download]") && ("retrying" in lower || "giving up" in lower))
        if (!relevant) return
        Regex("""(?i)\bHTTP(?:\s*(?:Error|status))?\s*[:=]?\s*([45]\d{2})\b""").findAll(line.take(8192))
            .forEach { httpStatuses += it.groupValues[1].toInt() }
        if ("retry" in lower || "retries" in lower) retryEvents++
        events.addLast("+${elapsedMs}ms ${sanitizeDiagnosticText(line)}")
        while (events.size > 24) events.removeFirst()
    }

    fun lines(): List<String> = events.toList()
}

/** Bounded private reports survive retries and process restarts, independently of staging. */
internal class MediaReportFiles(private val root: File) {
    companion object { private val lock = Any() }
    fun write(text: String): String = synchronized(lock) {
        check(root.isDirectory || root.mkdirs()) { "Unable to create diagnostic storage" }
        root.listFiles().orEmpty().filter { it.isFile && it.extension == "tmp" && reportPattern.matches(it.nameWithoutExtension) }.forEach { it.delete() }
        val id = "TK-" + UUID.randomUUID().toString().replace("-", "").take(12)
        val target = File(root, "$id.txt")
        val temp = File(root, "$id.tmp")
        try {
            temp.writeText("Toolkit diagnostic report\nReport: $id\n\n" + text.take(15_000), Charsets.UTF_8)
            check(temp.renameTo(target)) { "Unable to save diagnostic report" }
        } finally { temp.delete() }
        root.listFiles().orEmpty().filter { it.isFile && it.extension == "txt" && reportPattern.matches(it.nameWithoutExtension) && it != target }
            .sortedByDescending { it.lastModified() }.drop(19).forEach { it.delete() }
        id
    }

    fun read(id: String): String = synchronized(lock) {
        require(reportPattern.matches(id)) { "Invalid diagnostic report" }
        val file = File(root, "$id.txt")
        check(file.isFile && file.length() <= 64 * 1024) { "This report is no longer available. Retry to capture a new failure." }
        file.readText(Charsets.UTF_8)
    }

    fun list(): List<Pair<String, Long>> = synchronized(lock) { root.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "txt" && reportPattern.matches(it.nameWithoutExtension) }
        .sortedByDescending { it.lastModified() }.take(20).map { it.nameWithoutExtension to it.lastModified() }
    }
}
