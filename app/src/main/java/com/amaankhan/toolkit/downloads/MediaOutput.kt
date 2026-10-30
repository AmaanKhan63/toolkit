package com.amaankhan.toolkit.downloads

internal data class MediaProgress(val bytes: Long, val total: Long, val speed: Long, val percent: Int, val detail: String)

internal fun parseMediaProgress(line: String): MediaProgress? {
    if (!line.startsWith("TKPROG:")) return null
    val values = line.split(':')
    if (values.size != 7 || values[6] !in setOf("downloading", "finished")) return null
    fun number(index: Int): Long = values[index].toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong() ?: 0
    val bytes = number(1)
    // Fragment estimates can swing by gigabytes. Only publish confirmed stream sizes.
    val total = number(2).takeIf { it >= bytes } ?: 0
    val finished = values[6] == "finished"
    val speed = if (finished) 0 else number(4)
    val eta = number(5)
    val percent = if (finished || total == 0L) -1 else (bytes.toDouble() / total * 100).toInt().coerceIn(0, 100)
    val detail = when {
        finished -> "Preparing audio / video"
        total == 0L -> "Downloading · total size unknown"
        eta > 0 -> {
            val remaining = when {
                eta >= 3600 -> "${eta / 3600}h ${(eta % 3600) / 60}m"
                eta >= 60 -> "${eta / 60}m ${eta % 60}s"
                else -> "${eta}s"
            }
            "Downloading stream · $remaining remaining"
        }
        else -> "Downloading stream"
    }
    return MediaProgress(bytes, total, speed, percent, detail)
}

internal enum class MediaFailureReason(val userMessage: String) {
    ACCESS_DENIED("The site refused the media request (403). Resume to refresh the link; if it repeats, update the media engine or try another format."),
    SIGN_IN("This source requires sign-in or a site verification that Toolkit cannot complete. Try a public, unrestricted link."),
    RATE_LIMITED("The site is limiting requests (429). Wait a few minutes, then resume. Avoid starting several downloads from this site together."),
    NETWORK("The media connection failed after retries. Check your connection, then resume to refresh the link and continue."),
    STORAGE("There is not enough writable storage for the media. Free up space on this device, then resume."),
    FOLDER_ACCESS("Folder access expired. Choose the destination again and resume."),
    SAVE_FAILED("The media downloaded, but saving failed. Check folder access and free space, then retry saving."),
    FORMAT("The selected format is unavailable. Inspect the link again and choose Automatic or a different quality."),
    UNSUPPORTED("This link is not supported by the media engine. Use the original video page, or Direct link for a file URL."),
    UNAVAILABLE("The source is unavailable, removed or restricted. Check that it opens publicly and is available in your region."),
    PROCESSING("The media downloaded but could not be combined or converted. Check free space, then resume; try another container if it repeats."),
    ENGINE("The media engine could not process this site. Update the media engine and retry; if it persists, check for a Toolkit update."),
    INCOMPLETE("A media fragment could not be downloaded after retries. The file was not marked complete. Resume to refresh the source and continue."),
    TIMED_OUT("The source took too long to respond. Check your connection and try again; update the media engine if inspection keeps failing."),
    UNKNOWN("Media download failed. Check the source, connection and free space, then resume. Update the media engine if the problem repeats."),
}

/** Only fixed app-owned messages cross the subprocess boundary; never retain stderr or its cause. */
internal class MediaFailure(val reason: MediaFailureReason, val diagnosticId: String? = null) : IllegalStateException(
    reason.userMessage + "\nCode: ${reason.name}" + (diagnosticId?.let { "\nReport: $it" } ?: ""))

internal class MediaDiagnostics {
    private var errorReason: MediaFailureReason? = null
    private var warningReason: MediaFailureReason? = null

    fun observe(line: String) {
        val text = line.take(8192).lowercase()
        // yt-dlp emits "ERROR: \r[download] Got error: ..."; readLine splits at that CR.
        val isError = text.startsWith("error:") || text.startsWith("[download] got error:")
        if (!isError && !text.startsWith("warning:")) return
        val reason = when {
            listOf("no space left", "disk full", "disk quota", "permission denied", "read-only file system").any(text::contains) -> MediaFailureReason.STORAGE
            listOf("sign in", "sign-in", "login required", "log in", "http error 401", "confirm you're not a bot").any(text::contains) -> MediaFailureReason.SIGN_IN
            "429" in text || "too many requests" in text -> MediaFailureReason.RATE_LIMITED
            "http error 403" in text || "403: forbidden" in text -> MediaFailureReason.ACCESS_DENIED
            listOf("timed out", "timeout", "connection reset", "connection refused", "network is unreachable", "unable to resolve", "name resolution", "certificate verify failed", "http error 500", "http error 502", "http error 503", "http error 504", "remote end closed", "incomplete read", "incompleteread").any(text::contains) -> MediaFailureReason.NETWORK
            "requested format" in text && "not available" in text -> MediaFailureReason.FORMAT
            "unsupported url" in text -> MediaFailureReason.UNSUPPORTED
            listOf("video unavailable", "video has been removed", "private video", "not available in your country", "geo restricted", "http error 404").any(text::contains) -> MediaFailureReason.UNAVAILABLE
            listOf("postprocessing", "conversion failed", "ffmpeg exited").any(text::contains) -> MediaFailureReason.PROCESSING
            listOf("javascript runtime", "challenge solving", "signature solving", "signature extraction", "nsig extraction", "yt-dlp-ejs", "no such option", "cannot find ffmpeg").any(text::contains) -> MediaFailureReason.ENGINE
            "fragment" in text && ("not found" in text || "unable to continue" in text) -> MediaFailureReason.INCOMPLETE
            else -> null
        } ?: return
        if (isError) {
            // A final fragment summary adds no information to an earlier HTTP failure.
            if (reason != MediaFailureReason.INCOMPLETE || errorReason == null) errorReason = reason
        } else warningReason = reason
    }

    fun failure() = MediaFailure(errorReason ?: warningReason ?: MediaFailureReason.UNKNOWN)
}
