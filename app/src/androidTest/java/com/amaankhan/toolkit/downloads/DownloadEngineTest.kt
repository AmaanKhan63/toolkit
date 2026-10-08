package com.amaankhan.toolkit.downloads

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaankhan.toolkit.ToolkitApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DownloadEngineTest {
    private val app = ApplicationProvider.getApplicationContext<ToolkitApplication>()
    private val store get() = app.downloads
    private val ids = mutableListOf<String>()
    @After fun cleanup() = runBlocking { ids.forEach { store.dao.remove(it); store.directory(it).deleteRecursively() } }
    private suspend fun entry(kind: DownloadKind, source: String, name: String = "fixture.bin"): DownloadEntry {
        val entry = DownloadEntry(kind = kind.name, source = source, name = name, destination = "content://test/not-exported")
        ids += entry.id; store.dao.insert(entry); return entry
    }
    @Test fun nativeRuntimesLoadWithoutAnAccount() = runBlocking {
        withContext(Dispatchers.IO) {
            DownloadRuntime.initialize(app)
            val version = YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList()).apply { addOption("--version") }).out.trim()
            assertTrue(version, version.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}.*")))
            System.loadLibrary("tdjni")
            assertNotNull(Client.execute(TdApi.GetLogVerbosityLevel()))
            AriaSession(app, File(app.cacheDir, "aria-native-test").apply { mkdirs() }).use { session ->
                session.start(); val info = session.rpc("getVersion") as JSONObject
                assertTrue(info.getJSONArray("enabledFeatures").toString().contains("BitTorrent"))
            }
        }
    }
    @Test fun optionalLiveSiteInspectionUsesTheActualAppNetworkContext() = runBlocking {
        Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        val result = withTimeout(90_000) { withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=DTi8wZ1a1TA").apply {
                addOption("--dump-single-json"); addOption("--skip-download"); addOption("--socket-timeout", "10"); addOption("--retries", "1"); addOption("--extractor-retries", "1")
            }
            JSONObject(runMedia(app, request, capture = true))
        } }
        assertTrue(result.getJSONArray("formats").length() > 0)
        File(app.cacheDir, "live-media-evidence.json").writeText(JSONObject().put("id", result.getString("id")).put("formats", result.getJSONArray("formats").length()).toString())
    }
    @Test fun directDownloadUsesNativeAriaAndExactBytes() = runBlocking {
        val bytes = ByteArray(3 * 1024 * 1024) { ((it * 31) % 251).toByte() }
        FixtureServer(mapOf("/fixture.bin" to bytes)).use { server ->
            val entry = entry(DownloadKind.DIRECT, server.url + "/fixture.bin")
            withTimeout(45_000) { DownloadEngines(store).aria(entry) { 0 } }
            val done = store.dao.get(entry.id)!!
            assertTrue(done.ready)
            val result = File(store.directory(entry.id), jsonObjects(done.files).single().getString("path"))
            assertArrayEquals(bytes, result.readBytes())
        }
    }
    @Test fun mediaDownloadRunsPythonAndFfmpegAndCreatesActualMp3() = runBlocking {
        val samples = 16000
        val buffer = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { buffer.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / 16000) * 10000).toInt().toShort()) }
        FixtureServer(mapOf("/tone.wav" to buffer.array())).use { server ->
            val entry = entry(DownloadKind.VIDEO, server.url + "/tone.wav", "Test tone").copy(options = JSONObject().put("container", "mp3").toString())
            store.dao.update(entry)
            withTimeout(60_000) { DownloadEngines(store).video(entry, 0) }
            val done = store.dao.get(entry.id)!!; assertTrue(done.ready)
            val file = File(store.directory(entry.id), jsonObjects(done.files).single().getString("path"))
            assertEquals("mp3", file.extension); assertTrue(file.length() > 1000)
            val header = file.inputStream().use { ByteArray(3).also { bytes -> it.read(bytes) } }
            assertTrue(String(header) == "ID3" || header[0].toInt() and 255 == 255)
        }
    }
    @Test fun pauseRetainsControlFileAndResumeUsesRanges() = runBlocking {
        val bytes = ByteArray(4 * 1024 * 1024) { (it % 233).toByte() }
        FixtureServer(mapOf("/fixture.bin" to bytes)).use { server ->
            val entry = entry(DownloadKind.DIRECT, server.url + "/fixture.bin")
            val task = launch { DownloadEngines(store).aria(entry) { 128 * 1024 } }
            withTimeout(30_000) { while ((store.dao.get(entry.id)?.bytes ?: 0) < 128 * 1024) delay(100) }
            task.cancelAndJoin()
            assertTrue(File(store.directory(entry.id), "data").walkTopDown().any { it.name.endsWith(".aria2") })
            withTimeout(45_000) { DownloadEngines(store).aria(store.dao.get(entry.id)!!) { 0 } }
            val done = store.dao.get(entry.id)!!
            assertTrue(done.ready); assertTrue(server.ranges.get() > 0)
            assertArrayEquals(bytes, File(store.directory(entry.id), jsonObjects(done.files).single().getString("path")).readBytes())
        }
    }
    @Test fun torrentInspectsBeforeTransferAndDownloadsOnlySelectedFile() = runBlocking {
        val a = ByteArray(65536) { 35 }; val b = ByteArray(65536) { (it % 197).toByte() }
        FixtureServer(mapOf("/fixture/a.bin" to a, "/fixture/b.bin" to b)).use { server ->
            val entry = entry(DownloadKind.TORRENT, "local-torrent", "Fixture torrent")
            val all = a + b
            val pieces = ByteArrayOutputStream().apply { all.asList().chunked(16384).forEach { write(MessageDigest.getInstance("SHA-1").digest(it.toByteArray())) } }.toByteArray()
            val info = mapOf("name" to "fixture", "piece length" to 16384, "pieces" to pieces, "private" to 1,
                "files" to listOf(mapOf("length" to a.size, "path" to listOf("a.bin")), mapOf("length" to b.size, "path" to listOf("b.bin"))))
            File(store.directory(entry.id), "source.torrent").writeBytes(bencode(mapOf("info" to info, "url-list" to listOf(server.url + "/"))))
            withTimeout(30_000) { DownloadEngines(store).aria(entry) { 0 } }
            val inspected = store.dao.get(entry.id)!!
            assertEquals("SELECT_FILES", inspected.state); assertEquals(2, jsonObjects(inspected.files).size); assertFalse(inspected.ready)
            assertEquals(0, server.transfers.get())
            val chosen = inspected.copy(options = JSONObject().put("selected", "2").toString(), state = "QUEUED")
            store.dao.update(chosen)
            withTimeout(60_000) { DownloadEngines(store).aria(chosen) { 0 } }
            val done = store.dao.get(entry.id)!!; assertTrue(done.ready)
            val output = jsonObjects(done.files).single(); assertTrue(output.getString("name").endsWith("b.bin"))
            assertArrayEquals(b, File(store.directory(entry.id), output.getString("path")).readBytes())
        }
    }
    @Test fun encryptedCredentialsSurviveReloadWithoutPlaintextPreferences() {
        val namespace = "telegram-test-${System.nanoTime()}"
        val secrets = TelegramSecrets(app, namespace)
        try {
            val original = JSONObject().put("hash", "synthetic-only-credential").put("id", 42)
            secrets.save(original)
            assertEquals("synthetic-only-credential", TelegramSecrets(app, namespace).read()!!.getString("hash"))
            val saved = app.getSharedPreferences(namespace, 0).all.toString()
            assertFalse(saved.contains("synthetic-only-credential"))
            secrets.clear(); assertNull(secrets.read())
        } finally { secrets.clear() }
    }
    @Test fun interruptedQueueIsRecoverableWithoutMarkingComplete() = runBlocking {
        val entry = entry(DownloadKind.DIRECT, "https://example.com/fixture")
        store.dao.update(entry.copy(state = "SAVING", ready = true, files = "[]", pendingUri = "content://test/partial"))
        store.dao.recover()
        val restored = store.dao.get(entry.id)!!
        assertEquals("PAUSED", restored.state); assertTrue(restored.ready); assertEquals("content://test/partial", restored.pendingUri)
    }
    private fun bencode(value: Any): ByteArray = when (value) {
        is String -> bencode(value.toByteArray())
        is ByteArray -> "${value.size}:".toByteArray() + value
        is Number -> "i${value}e".toByteArray()
        is List<*> -> "l".toByteArray() + value.fold(byteArrayOf()) { acc, item -> acc + bencode(item!!) } + "e".toByteArray()
        is Map<*, *> -> "d".toByteArray() + value.keys.map { it as String }.sorted().fold(byteArrayOf()) { acc, key -> acc + bencode(key) + bencode(value[key]!!) } + "e".toByteArray()
        else -> error("Unsupported fixture")
    }
}

private class FixtureServer(private val files: Map<String, ByteArray>) : AutoCloseable {
    private val server = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()
    val url = "http://127.0.0.1:${server.localPort}"
    val ranges = AtomicInteger(0); val transfers = AtomicInteger(0)
    init { thread(isDaemon = true) { while (!server.isClosed) try { val socket = server.accept(); pool.submit { runCatching { respond(socket) }; socket.close() } } catch (_: Exception) { } } }
    private fun respond(socket: Socket) {
        socket.soTimeout = 5000
        val reader = socket.getInputStream().bufferedReader(); val first = reader.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim() }
        val path = first.split(' ')[1].substringBefore('?'); val data = files[path]
        val output = socket.getOutputStream()
        if (data == null) { output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return }
        transfers.incrementAndGet()
        val range = headers["range"]?.removePrefix("bytes=")?.split('-')
        val start = range?.getOrNull(0)?.toIntOrNull() ?: 0
        val end = (range?.getOrNull(1)?.toIntOrNull() ?: data.lastIndex).coerceAtMost(data.lastIndex)
        if (range != null) ranges.incrementAndGet()
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val contentRange = if (range != null) "Content-Range: bytes $start-$end/${data.size}\r\n" else ""
        output.write("HTTP/1.1 $status\r\nContent-Length: ${end - start + 1}\r\nAccept-Ranges: bytes\r\nContent-Type: application/octet-stream\r\n${contentRange}Connection: close\r\n\r\n".toByteArray())
        if (!first.startsWith("HEAD")) output.write(data, start, end - start + 1)
        output.flush()
    }
    override fun close() { server.close(); pool.shutdownNow() }
}
