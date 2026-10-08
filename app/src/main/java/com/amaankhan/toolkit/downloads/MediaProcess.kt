package com.amaankhan.toolkit.downloads

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

/** A process group makes cancel stop Python and any active FFmpeg child together. */
internal suspend fun runMedia(context: Context, request: YoutubeDLRequest, capture: Boolean = false, line: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
    DownloadRuntime.initialize(context)
    val native = File(context.applicationInfo.nativeLibraryDir)
    val base = File(context.noBackupFilesDir, "youtubedl-android")
    val packages = File(base, "packages")
    request.addOption("--js-runtimes", "quickjs:${File(native, "libqjs.so").path}")
    request.addOption("--ffmpeg-location", File(native, "libffmpeg.so").path)
    request.addOption("--no-cache-dir")
    // This fixed shell fragment contains no interpolated URL, filename or credential.
    // All engine arguments are positional arguments passed through "$@" unchanged.
    val args = listOf("/system/bin/sh", "-c", "echo \$\$; exec /system/bin/setsid \"\$@\"", "toolkit-media",
        File(native, "libpython.so").path, File(base, "yt-dlp/yt-dlp").path) + request.buildCommand()
    val process = ProcessBuilder(args).redirectErrorStream(true).apply {
        environment()["LD_LIBRARY_PATH"] = listOf("python", "ffmpeg", "aria2c").joinToString(":") { File(packages, "$it/usr/lib").path }
        environment()["SSL_CERT_FILE"] = File(packages, "python/usr/etc/tls/cert.pem").path
        environment()["PYTHONHOME"] = File(packages, "python/usr").path
        environment()["HOME"] = File(packages, "python/usr").path
        environment()["TMPDIR"] = context.cacheDir.path
        environment()["PATH"] = System.getenv("PATH").orEmpty() + ":" + native.path
    }.start()
    val pid = java.util.concurrent.atomic.AtomicInteger(0)
    val output = StringBuilder()
    coroutineScope {
    // Killing on cancellation releases a blocking read even when the source goes silent.
    val cancellationGuard = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally {
            if (pid.get() > 0) runCatching { Os.kill(-pid.get(), OsConstants.SIGTERM) }
            process.destroy()
        }
    }
    try {
        runInterruptible {
            process.inputStream.bufferedReader().use { reader ->
                pid.set(reader.readLine()?.trim()?.toIntOrNull() ?: error("Could not start the media process."))
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val text = reader.readLine() ?: break
                    if (capture && text.startsWith("{")) { require(output.length + text.length <= 16 * 1024 * 1024) { "Media inspection is too large." }; output.append(text) }
                    line(text)
                }
            }
            val result = process.waitFor()
            check(result == 0) { "Media download failed. The link may be unavailable, unsupported or require an engine update." }
        }
        output.toString()
    } finally {
        withContext(NonCancellable) { cancellationGuard.cancelAndJoin() }
        if (!process.waitFor(2, TimeUnit.SECONDS)) { if (pid.get() > 0) runCatching { Os.kill(-pid.get(), OsConstants.SIGKILL) }; process.destroyForcibly() }
    }
    }
}
