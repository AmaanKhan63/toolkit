package com.amaankhan.toolkit.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject
import java.io.File
import java.time.Instant

internal object MediaReports {
    private fun files(context: Context) = MediaReportFiles(File(context.noBackupFilesDir, "media-diagnostics"))

    @Synchronized fun failure(context: Context, error: Exception, trace: MediaTrace, operation: String, source: String,
                options: JSONObject = JSONObject(), resumedBytes: Long = 0): MediaFailure {
        val reason = when {
            error is MediaFailure -> error.reason
            operation == "save" && (error is SecurityException || error.message.orEmpty().startsWith("Folder access expired")) -> MediaFailureReason.FOLDER_ACCESS
            error is SecurityException -> MediaFailureReason.STORAGE
            else -> MediaDiagnostics().apply { observe("ERROR: ${error.message.orEmpty()}") }.failure().reason.let {
                if (operation == "save" && it == MediaFailureReason.UNKNOWN) MediaFailureReason.SAVE_FAILED else it
            }
        }
        val reference = runCatching {
            @Suppress("DEPRECATION") val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val report = buildString {
                appendLine("UTC: ${Instant.now()}")
                appendLine("Operation: $operation")
                appendLine("Code: ${reason.name}")
                appendLine("App: ${pkg.versionName} (${if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode})")
                appendLine("Engine: ${engineVersion(context)}")
                appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                appendLine("Device: ${sanitizeDiagnosticText(Build.MANUFACTURER + " " + Build.MODEL)}")
                appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Network: ${network(context)}")
                appendLine("Power saver: ${context.getSystemService(PowerManager::class.java)?.isPowerSaveMode}")
                appendLine("Free private storage bytes: ${context.noBackupFilesDir.usableSpace}")
                appendLine("Source: ${diagnosticSource(source)}")
                for (key in listOf("container", "height", "format", "limitKiB")) {
                    val value = options.optString(key, "automatic")
                    appendLine("Requested $key: ${value.takeIf { it.matches(Regex("[A-Za-z0-9_+./<>*=:-]{0,100}")) } ?: "[custom value omitted]"}")
                }
                appendLine("Resolved formats: ${trace.formats}")
                appendLine("Elapsed ms: ${trace.elapsedMs}")
                appendLine("Last stage: ${trace.stage}")
                appendLine("Process exit: ${trace.exitCode ?: "not available"}")
                appendLine("HTTP status codes: ${trace.httpStatuses.joinToString().ifEmpty { "not available" }}")
                appendLine("Retry events: ${trace.retryEvents}")
                appendLine("Bytes present before attempt: $resumedBytes")
                appendLine("Last progress ms: ${trace.progressAtMs ?: "not available"}")
                trace.lastProgress?.let { appendLine("Last stream bytes: ${it.bytes}; confirmed total: ${it.total}; speed bytes/s: ${it.speed}") }
                appendLine("Failure type: ${error.javaClass.name}")
                appendLine("Failure detail: ${sanitizeDiagnosticText(error.message.orEmpty())}")
                appendLine("App stack:")
                error.stackTrace.filter { it.className.startsWith("com.amaankhan.toolkit.") }.take(8).forEach { appendLine("  ${it.className}.${it.methodName}:${it.lineNumber}") }
                appendLine("\nRecent sanitized engine diagnostics:")
                trace.lines().forEach { appendLine(it) }
                appendLine("\nNo raw source URLs, titles, destination paths, cookies or request headers are intentionally retained. Review before sharing.")
            }
            files(context).write(report)
        }.getOrNull()
        // Failure to write diagnostics (for example a full disk) must not mask the transfer error.
        return MediaFailure(reason, reference)
    }

    fun read(context: Context, id: String) = files(context).read(id)
    fun list(context: Context) = files(context).list()

    fun export(context: Context, id: String, uri: Uri) {
        val report = read(context, id)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            ?: error("Unable to save the error report. Choose another location.")
    }

    private fun engineVersion(context: Context): String =
        readMediaEngineVersion(File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")) ?: "unavailable"

    private fun network(context: Context): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching "offline / unavailable"
        listOf(NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi", NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet", NetworkCapabilities.TRANSPORT_VPN to "VPN")
            .filter { caps.hasTransport(it.first) }.joinToString { it.second } + "; validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
    }.getOrDefault("unavailable")
}
