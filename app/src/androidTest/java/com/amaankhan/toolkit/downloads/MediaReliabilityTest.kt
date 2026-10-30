package com.amaankhan.toolkit.downloads

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amaankhan.toolkit.ToolkitApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import android.net.Uri
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class MediaReliabilityTest {
    private val app = ApplicationProvider.getApplicationContext<ToolkitApplication>()
    private val store get() = app.downloads
    private val ids = mutableListOf<String>()

    @After fun cleanup() = runBlocking {
        ids.forEach { store.dao.remove(it); store.directory(it).deleteRecursively() }
    }

    @Test fun subprocess403ProducesSafeActionableFailure() = runBlocking {
        MediaFixture { _, _ -> Response(403, "Forbidden") }.use { server ->
            val request = YoutubeDLRequest("${server.url}/video?token=do-not-persist").apply { addOption("--dump-single-json") }
            val failure = try {
                withTimeout(30_000) { runMedia(app, request, capture = true) }
                fail("Expected the HTTP request to fail")
                null
            } catch (e: MediaFailure) { e }
            assertEquals(MediaFailureReason.ACCESS_DENIED, failure!!.reason)
            assertFalse(failure.message!!.contains("do-not-persist"))
            assertNull(failure.cause)
        }
    }

    @Test fun failedMediaPersistsAnExportableRedactedDiagnosticReport() = runBlocking {
        MediaFixture { _, _ -> Response(403, "Forbidden") }.use { server ->
            val entry = entry("${server.url}/private/path?token=do-not-save", JSONObject().put("container", "mp4").put("height", 1080))
            val failure = try {
                withTimeout(30_000) { DownloadEngines(store).video(entry, 0) }
                fail("Expected the source to refuse the transfer"); null
            } catch (e: MediaFailure) { e }
            assertEquals(MediaFailureReason.ACCESS_DENIED, failure!!.reason)
            val id = requireNotNull(failure.diagnosticId)
            assertEquals(id, mediaReportId(failure.message!!))
            val report = MediaReports.read(app, id)
            assertTrue(report.contains("Process exit: 1"))
            assertTrue(report.contains("HTTP status codes: 403"))
            assertTrue(report.contains("Requested height: 1080"))
            assertTrue(report.contains("Android:"))
            assertTrue(report.contains("Engine: 20"))
            assertFalse(report.contains("do-not-save"))
            assertFalse(report.contains("/private/path"))
            assertFalse(report.contains(server.url))
            val export = File(app.cacheDir, "exported-diagnostic-fixture.txt")
            try {
                MediaReports.export(app, id, Uri.fromFile(export))
                assertEquals(report, export.readText())
            } finally { export.delete() }
            val inspectionFailure = try {
                withTimeout(30_000) { DownloadEngines(store).inspectVideo(entry.source) }
                fail("Expected inspection to fail too"); null
            } catch (e: MediaFailure) { e }
            val inspectionReport = MediaReports.read(app, requireNotNull(inspectionFailure?.diagnosticId))
            assertTrue(inspectionReport.contains("Operation: inspect"))
            assertFalse(inspectionReport.contains("do-not-save"))
        }
    }

    @Test fun saveFailureReportPreservesFolderRecoveryGuidance() {
        for (error in listOf(SecurityException("content://private-provider/path"), IllegalStateException("Folder access expired. Choose the destination again and resume."))) {
            val failure = MediaReports.failure(app, error, MediaTrace().apply { stage = "saving" }, "save", "https://example.test/video")
            assertEquals(MediaFailureReason.FOLDER_ACCESS, failure.reason)
            assertTrue(failure.message!!.contains("Choose the destination again"))
            val report = MediaReports.read(app, requireNotNull(failure.diagnosticId))
            assertTrue(report.contains("Last stage: saving"))
            assertFalse(report.contains("private-provider"))
        }
    }

    @Test fun missingFragmentFailsInsteadOfExportingAnIncompleteVideo() = runBlocking {
        val missingRequests = AtomicInteger()
        MediaFixture { path, _ ->
            when (path) {
                "/video.m3u8" -> Response(body = "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:1,\nfirst.ts\n#EXTINF:1,\nmissing.ts\n#EXT-X-ENDLIST\n".toByteArray(), type = "application/vnd.apple.mpegurl")
                "/first.ts" -> Response(body = ByteArray(188 * 3) { if (it % 188 == 0) 0x47 else 0 }, type = "video/mp2t")
                else -> { missingRequests.incrementAndGet(); Response(404, "Not Found") }
            }
        }.use { server ->
            val entry = entry("${server.url}/video.m3u8", JSONObject().put("container", "mkv"))
            val failure = try {
                withTimeout(60_000) { DownloadEngines(store).video(entry, 0) }
                fail("Missing media fragments must not be silently skipped")
                null
            } catch (e: MediaFailure) { e }
            assertNotNull(failure)
            assertFalse(store.dao.get(entry.id)!!.ready)
            assertEquals("video", store.dao.get(entry.id)!!.name)
            assertTrue("Expected initial request and bounded fragment retries", missingRequests.get() in 2..8)
            assertFalse(File(store.directory(entry.id), "outputs.txt").exists())
        }
    }

    @Test fun resolvedMediaTitleDoesNotOverwritePausedTransferState() = runBlocking {
        val entry = entry("https://example.test/media", JSONObject())
        store.dao.update(entry.copy(state = "PAUSED", bytes = 1024, detail = "Paused", error = "Keep this status"))
        store.dao.updateName(entry.id, "Resolved video title")
        val saved = store.dao.get(entry.id)!!
        assertEquals("Resolved video title", saved.name)
        assertEquals("PAUSED", saved.state)
        assertEquals("Paused", saved.detail)
        assertEquals("Keep this status", saved.error)
        assertEquals(1024L, saved.bytes)
    }

    @Test fun cancellationStopsSilentInspectionPromptly() = runBlocking {
        val contacted = CompletableDeferred<Unit>()
        MediaFixture { _, _ ->
            contacted.complete(Unit)
            Thread.sleep(15_000)
            Response()
        }.use { server ->
            val task = launch { DownloadEngines(store).inspectVideo("${server.url}/silent") }
            withTimeout(30_000) { contacted.await() }
            withTimeout(5_000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
        }
    }

    @Test fun optionalMediaEngineUpdateRecordsActualRuntimeVersions() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        withContext(Dispatchers.IO) {
            DownloadRuntime.initialize(app)
            val engine = YoutubeDL.getInstance()
            fun actualVersion() = engine.execute(YoutubeDLRequest(emptyList()).apply { addOption("--version") }).out.trim()
            val before = actualVersion()
            val result = requireNotNull(engine.updateYoutubeDL(app)) { "Media engine update returned no status" }
            val after = actualVersion()
            assertTrue(after.matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}.*")))
            assertEquals(after, engine.version(app))
            File(app.cacheDir, "media-engine-version-evidence.json").writeText(JSONObject()
                .put("before", before).put("after", after).put("result", result.name).toString())
        }
    }

    @Test fun optionalReportedVideoInspectsAndRemuxesAShortSectionInActualAppContext() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        val engines = DownloadEngines(store)
        val info = withTimeout(100_000) { engines.inspectVideo("https://youtu.be/lh4JdZTJe7k") }
        assertEquals("lh4JdZTJe7k", info.getString("id"))
        assertTrue(info.getJSONArray("formats").length() > 0)
        val entry = entry("https://youtu.be/lh4JdZTJe7k", JSONObject().put("container", "mp4").put("height", 360))
        val directory = store.directory(entry.id)
        // The reported source is twelve hours long. Exercise the real network/FFmpeg path
        // using a short section; this does not establish full-transfer reliability.
        val request = YoutubeDLRequest(entry.source).apply {
            addOption("--ignore-config"); addOption("--no-playlist")
            addOption("-f", "bv*[height<=360]+ba/b[height<=360]")
            addOption("-S", "vcodec:h264,acodec:aac")
            addOption("--download-sections", "*0-10")
            addOption("--merge-output-format", "mp4"); addOption("--remux-video", "mp4")
            addOption("--abort-on-unavailable-fragments")
            addOption("-o", File(directory, "section.%(ext)s").path)
        }
        withTimeout(180_000) { runMedia(app, request) }
        val file = File(directory, "section.mp4")
        assertTrue(file.length() > 1024)
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            assertTrue(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 1..30_000)
            assertTrue(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt() <= 360)
        } finally { retriever.release() }
    }

    @Test fun optionalReportedVideoDownloadsPastFailurePointAndResumes() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        val entry = entry("https://youtu.be/lh4JdZTJe7k", JSONObject().put("container", "mp4").put("height", 1080))
        val engines = DownloadEngines(store)
        val evidence = JSONObject().put("engine", YoutubeDL.getInstance().version(app)).put("heightLimit", 1080)
        suspend fun reach(target: Long): Long = coroutineScope {
            val transfer = async { engines.video(entry, 2L * 1024 * 1024) }
            try {
                withTimeout(300_000) {
                    while (true) {
                        val current = requireNotNull(store.dao.get(entry.id))
                        if (current.bytes >= target) return@withTimeout current.bytes
                        if (transfer.isCompleted) { transfer.await(); error("Source finished before the requested transfer checkpoint") }
                        delay(250)
                    }
                    @Suppress("UNREACHABLE_CODE") 0L
                }
            } finally { transfer.cancelAndJoin() }
        }
        try {
            val paused = reach(104L * 1024 * 1024)
            evidence.put("pausedBytes", paused)
            assertTrue(store.directory(entry.id).walkTopDown().any { it.isFile && it.name.endsWith(".part") })
            assertFalse(requireNotNull(store.dao.get(entry.id)).ready)
            val resumed = reach(paused + 4L * 1024 * 1024)
            evidence.put("resumedBytes", resumed).put("result", "passed")
            assertTrue(resumed > paused)
            assertNotEquals("Media reliability fixture", requireNotNull(store.dao.get(entry.id)).name)
        } catch (error: Throwable) {
            evidence.put("result", "failed").put("failureType", error.javaClass.simpleName)
            withContext(NonCancellable) { evidence.put("lastBytes", store.dao.get(entry.id)?.bytes ?: 0) }
            throw error
        } finally {
            File(app.cacheDir, "media-transfer-evidence.json").writeText(evidence.toString())
        }
    }

    private suspend fun entry(source: String, options: JSONObject): DownloadEntry {
        val entry = DownloadEntry(kind = DownloadKind.VIDEO.name, source = source, name = "Media reliability fixture",
            destination = "content://test/not-exported", options = options.toString())
        ids += entry.id
        store.dao.insert(entry)
        return entry
    }
}

private data class Response(val status: Int = 200, val reason: String = "OK", val body: ByteArray = byteArrayOf(), val type: String = "application/octet-stream")

private class MediaFixture(private val respond: (String, String) -> Response) : AutoCloseable {
    private val server = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()
    val url = "http://127.0.0.1:${server.localPort}"
    init { thread(isDaemon = true) {
        while (!server.isClosed) try {
            val socket = server.accept()
            pool.submit { socket.use { runCatching { serve(it) } } }
        } catch (_: Exception) { }
    } }

    private fun serve(socket: Socket) {
        socket.soTimeout = 5000
        val reader = socket.getInputStream().bufferedReader()
        val first = reader.readLine() ?: return
        while (true) { if (reader.readLine().isNullOrEmpty()) break }
        val method = first.substringBefore(' ')
        val path = first.split(' ')[1].substringBefore('?')
        val response = respond(path, method)
        socket.getOutputStream().apply {
            write("HTTP/1.1 ${response.status} ${response.reason}\r\nContent-Length: ${response.body.size}\r\nContent-Type: ${response.type}\r\nConnection: close\r\n\r\n".toByteArray())
            if (method != "HEAD") write(response.body)
            flush()
        }
    }

    override fun close() { server.close(); pool.shutdownNow() }
}
