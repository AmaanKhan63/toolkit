package com.amaankhan.toolkit.downloads

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class MediaReportTest {
    @Test fun diagnosticTextRemovesCredentialsSignedUrlsAndPrivatePaths() {
        val input = "ERROR: HTTP Error 403 at https://user:pass@video.test/play?sig=signed-secret " +
            "token=token-secret password='two word secret' api_key=api-secret /data/user/0/private-name/file.part"
        val safe = sanitizeDiagnosticText(input)
        assertTrue(safe.contains("HTTP Error 403"))
        for (secret in listOf("user:pass", "signed-secret", "token-secret", "two word secret", "api-secret", "private-name")) {
            assertFalse("Leaked $secret: $safe", safe.contains(secret))
        }
        assertFalse(sanitizeDiagnosticText("Authorization: Bearer private-token").contains("private-token"))
        assertFalse(sanitizeDiagnosticText("Cookie: SID=private-cookie; other=value").contains("private-cookie"))
        assertFalse(sanitizeDiagnosticText("ERROR: \"access_token\": \"json secret\"").contains("json secret"))
        assertFalse(sanitizeDiagnosticText("ERROR: https:\\/\\/example.test/path?sig=escaped-secret").contains("escaped-secret"))
        for (key in listOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")) {
            for (quote in listOf("'", "\"")) {
                assertFalse(sanitizeDiagnosticText("ERROR: {$quote$key$quote: ${quote}Bearer quoted-secret$quote}").contains("quoted-secret"))
            }
        }
        assertFalse(sanitizeDiagnosticText("ERROR: '/storage/emulated/0/Download/Private video name.mp4'").contains("video name"))
        assertFalse(sanitizeDiagnosticText("ERROR: \"C:\\Users\\Private Person\\file.mp4\"").contains("Person"))
        assertFalse(sanitizeDiagnosticText("ERROR: /storage/emulated/0/Download/Private video name.mp4").contains("video name"))
        assertFalse(sanitizeDiagnosticText("ERROR: {\"password\":\"prefix\\\"private-tail\"}").contains("private-tail"))
        assertFalse(sanitizeDiagnosticText("ERROR: {\\\"Authorization\\\":\\\"Bearer escaped-header\\\"}").contains("escaped-header"))
    }

    @Test fun sourceSummaryRetainsOnlyHostAndPublicYoutubeVideoId() {
        assertEquals("youtu.be video=lh4JdZTJe7k", diagnosticSource("https://youtu.be/lh4JdZTJe7k?si=private-share-token"))
        assertEquals("www.youtube.com video=lh4JdZTJe7k", diagnosticSource("https://www.youtube.com/watch?v=lh4JdZTJe7k&token=secret"))
        assertEquals("example.test", diagnosticSource("https://user:secret@example.test/private/path?token=hidden"))
        assertEquals("unknown", diagnosticSource("not a URL with secrets"))
    }

    @Test fun reportReferencesAcceptOnlyGeneratedIds() {
        assertEquals("TK-0123456789ab", mediaReportId("Try again.\nReport: TK-0123456789ab"))
        assertNull(mediaReportId("Report: ../../private"))
        assertNull(mediaReportId("Report: TK-0123456789abEXTRA"))
    }

    @Test fun traceRetainsCauseAndProgressWithoutTitlesAndIsBounded() {
        val trace = MediaTrace()
        trace.observe("TKNAME:\"Private document title\"")
        trace.observe("[download] Destination: Private document title.mp4")
        trace.observe("[info] video: Downloading 1 format(s): 137+140")
        trace.observe("TKPROG:4096:8192:NA:1024:4:downloading")
        repeat(80) { trace.observe("[download] Got error: HTTP Error 503. Retrying (3/3) https://example.test/?token=hidden-$it") }
        assertEquals("137+140", trace.formats)
        assertEquals(4096L, trace.lastProgress!!.bytes)
        assertEquals(80, trace.retryEvents)
        assertEquals(setOf(503), trace.httpStatuses)
        assertEquals(24, trace.lines().size)
        assertTrue(trace.lines().all { it.contains("HTTP Error 503") && it.length < 440 })
        assertFalse(trace.lines().joinToString().contains("Private document"))
        assertFalse(trace.lines().joinToString().contains("hidden-"))
        trace.observe("ERROR: [generic] path?token=private-value: Unable to download webpage: HTTP Error 403: Forbidden")
        assertEquals(setOf(503, 403), trace.httpStatuses)
        assertFalse(trace.lines().last().contains("private-value"))
    }

    @Test fun reportStorageIsBoundedReadableAfterReloadAndRejectsPaths() {
        val directory = Files.createTempDirectory("toolkit-reports-").toFile()
        try {
            val files = MediaReportFiles(directory)
            File(directory, "TK-aaaaaaaaaaaa.tmp").writeText("Interrupted diagnostic write")
            File(directory, "unrelated.tmp").writeText("Not ours")
            repeat(25) { files.write("Synthetic failure $it") }
            assertEquals(20, directory.listFiles()!!.count { it.extension == "txt" })
            assertFalse(File(directory, "TK-aaaaaaaaaaaa.tmp").exists())
            assertTrue(File(directory, "unrelated.tmp").exists())
            val id = files.write("x".repeat(100_000))
            val restored = MediaReportFiles(directory).read(id)
            assertTrue(restored.startsWith("Toolkit diagnostic report\nReport: $id"))
            assertTrue(restored.toByteArray().size <= 64 * 1024)
            assertThrows(IllegalArgumentException::class.java) { files.read("../private") }
        } finally { directory.deleteRecursively() }
    }

    @Test fun actualVersionIsReadFromShebangPrefixedPythonZipApp() {
        val file = Files.createTempFile("toolkit-core-", ".zip").toFile()
        try {
            file.outputStream().use { output ->
                output.write("#!/usr/bin/env python3\n".toByteArray())
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("yt_dlp/version.py"))
                    zip.write("__version__ = '2026.10.26'\n".toByteArray()); zip.closeEntry()
                }
            }
            assertEquals("2026.10.26", readMediaEngineVersion(file))
        } finally { file.delete() }
    }
}
