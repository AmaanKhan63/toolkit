package com.amaankhan.toolkit.downloads

import org.junit.Assert.*
import org.junit.Test

class MediaOutputTest {
    @Test fun fragmentEstimateIsNotReportedAsAnExactSize() {
        val progress = parseMediaProgress("TKPROG:102760448:NA:16642998272:1048576:45:downloading")!!
        assertEquals(102760448L, progress.bytes)
        assertEquals(0L, progress.total)
        assertEquals(-1, progress.percent)
        assertTrue(progress.detail.contains("unknown", ignoreCase = true))
    }

    @Test fun exactSizeReportsCurrentStreamProgressWithoutOverflow() {
        val progress = parseMediaProgress("TKPROG:500000000000000000:1000000000000000000:NA:2048:12:downloading")!!
        assertEquals(50, progress.percent)
        assertEquals(2048L, progress.speed)
        assertTrue(progress.detail.contains("stream", ignoreCase = true))
    }

    @Test fun missingOrInvalidNumbersNeverBecomeEnormousOrNegativeProgress() {
        for (value in listOf("NA", "None", "NaN", "Infinity", "-1", "1e100")) {
            val progress = parseMediaProgress("TKPROG:$value:$value:NA:$value:$value:downloading")!!
            assertEquals(0L, progress.bytes)
            assertEquals(0L, progress.total)
            assertEquals(0L, progress.speed)
            assertEquals(-1, progress.percent)
        }
        assertNull(parseMediaProgress("WARNING: unrelated output"))
        assertNull(parseMediaProgress("TKPROG:12"))
    }

    @Test fun aFinishedStreamDoesNotClaimTheWholeTransferIsComplete() {
        val progress = parseMediaProgress("TKPROG:2048:2048:NA:NA:NA:finished")!!
        assertEquals(-1, progress.percent)
        assertEquals(0L, progress.speed)
        assertTrue(progress.detail.contains("Preparing", ignoreCase = true))
    }

    @Test fun longTransferEtaUsesHoursAndMinutes() {
        val progress = parseMediaProgress("TKPROG:2048:99999:NA:20:3835:downloading")!!
        assertTrue(progress.detail, progress.detail.contains("1h 3m"))
        assertFalse(progress.detail.contains("3835s"))
        val minutes = parseMediaProgress("TKPROG:2048:99999:NA:20:125:downloading")!!
        assertTrue(minutes.detail, minutes.detail.contains("2m 5s"))
    }

    @Test fun failedHttpRequestRetainsUsefulCategoryWithoutRetainingSignedUrl() {
        val diagnostics = MediaDiagnostics()
        diagnostics.observe("WARNING: [youtube] Signature solving failed")
        diagnostics.observe("ERROR: unable to download video data: HTTP Error 403: Forbidden https://video.example/stream?token=secret")
        diagnostics.observe("  File /data/user/0/private-account/extractor.py line 42")
        val failure = diagnostics.failure()
        assertEquals(MediaFailureReason.ACCESS_DENIED, failure.reason)
        assertFalse(failure.message!!.contains("secret"))
        assertFalse(failure.message!!.contains("private-account"))
        assertNull(failure.cause)
    }

    @Test fun terminalMissingFragmentDoesNotEraseTheNetworkFailureThatCausedIt() {
        val diagnostics = MediaDiagnostics()
        diagnostics.observe("ERROR: HTTP Error 503: Service Unavailable. Retrying (3/3)...")
        diagnostics.observe("ERROR: fragment 7 not found, unable to continue")
        assertEquals(MediaFailureReason.NETWORK, diagnostics.failure().reason)
    }

    @Test fun carriageReturnSeparatedDownloadErrorsStillRetainTheHttpCause() {
        val diagnostics = MediaDiagnostics()
        diagnostics.observe("ERROR: ")
        diagnostics.observe("[download] Got error: HTTP Error 503: Service Unavailable. Giving up after 3 retries")
        diagnostics.observe("ERROR: fragment 2 not found, unable to continue")
        assertEquals(MediaFailureReason.NETWORK, diagnostics.failure().reason)
    }

    @Test fun engineFailuresAreClassifiedIntoActionableCategories() {
        val cases = listOf(
            "ERROR: Sign in to confirm you're not a bot" to MediaFailureReason.SIGN_IN,
            "ERROR: HTTP Error 429: Too Many Requests" to MediaFailureReason.RATE_LIMITED,
            "ERROR: timed out" to MediaFailureReason.NETWORK,
            "ERROR: [Errno 28] No space left on device" to MediaFailureReason.STORAGE,
            "ERROR: Requested format is not available" to MediaFailureReason.FORMAT,
            "ERROR: Unsupported URL: https://example.test/private" to MediaFailureReason.UNSUPPORTED,
            "ERROR: Video unavailable. This video has been removed" to MediaFailureReason.UNAVAILABLE,
            "ERROR: Postprocessing: Conversion failed!" to MediaFailureReason.PROCESSING,
            "WARNING: No supported JavaScript runtime could be found" to MediaFailureReason.ENGINE,
            "ERROR: fragment 3 not found, unable to continue" to MediaFailureReason.INCOMPLETE,
        )
        for ((line, expected) in cases) {
            val diagnostics = MediaDiagnostics().apply { observe(line) }
            assertEquals(line, expected, diagnostics.failure().reason)
        }
    }

    @Test fun progressAndTitlesDoNotMasqueradeAsFailures() {
        val diagnostics = MediaDiagnostics()
        diagnostics.observe("[download] Destination: HTTP Error 403 No space left on device.mp4")
        diagnostics.observe("TKPROG:30:90:NA:20:3:downloading")
        assertEquals(MediaFailureReason.UNKNOWN, diagnostics.failure().reason)
    }
}
