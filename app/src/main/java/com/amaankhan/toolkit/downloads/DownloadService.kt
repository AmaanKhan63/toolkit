package com.amaankhan.toolkit.downloads

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.R
import com.amaankhan.toolkit.ToolkitApplication
import com.amaankhan.toolkit.data.ExportedDocument
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, Job>()
    private val store get() = (application as ToolkitApplication).downloads
    private var wakeLock: PowerManager.WakeLock? = null
    private val commands = kotlinx.coroutines.channels.Channel<Pair<String, String>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val started = CompletableDeferred<Unit>()
    @Volatile private var latestStartId = 0
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification("Preparing downloads"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION, notification("Preparing downloads"))
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Toolkit:Downloads").apply { acquire(6 * 60 * 60 * 1000L) }
        scope.launch {
            store.dao.recover(); started.await()
            while (isActive) {
                val observedStart = latestStartId
                while (true) { val command = commands.tryReceive().getOrNull() ?: break; applyCommand(command.first, command.second) }
                for (entry in store.dao.all().filter { it.state == "QUEUED" }) {
                    if (active.size >= 2) break
                    if (active.containsKey(entry.id)) continue
                    val task = scope.launch(start = CoroutineStart.LAZY) { runEntry(entry.id) }
                    active[entry.id] = task; task.start()
                }
                val current = store.dao.all()
                val count = current.count { it.state in setOf("RUNNING", "SAVING", "QUEUED") }
                if (count == 0 && active.isEmpty() && stopSelfResult(observedStart)) break
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification("$count transfers · ${current.sumOf { it.speed } / 1024} KB/s"))
                delay(700)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action.orEmpty(); val id = intent?.getStringExtra("id").orEmpty()
        if (action in setOf("pause", "cancel", "pause-all")) commands.trySend(action to id)
        started.complete(Unit)
        return START_NOT_STICKY
    }
    private suspend fun applyCommand(action: String, id: String) {
            val ids = if (action == "pause-all") store.dao.all().filter { it.state in setOf("RUNNING", "QUEUED", "SAVING") }.map { it.id } else listOf(id)
            ids.forEach { jobId ->
                val state = if (action == "cancel") "CANCELED" else "PAUSED"
                store.dao.state(jobId, state, state.lowercase().replaceFirstChar(Char::uppercase))
                active[jobId]?.cancelAndJoin()
                store.dao.state(jobId, state, state.lowercase().replaceFirstChar(Char::uppercase))
                if (action == "cancel") runCatching {
                    store.dao.get(jobId)?.let { cleanupPending(it) }
                    store.directory(jobId).deleteRecursively()
                }.onFailure { store.dao.state(jobId, "FAILED", "Cleanup needs attention", it.message.orEmpty()) }
            }
    }
    private suspend fun runEntry(id: String) {
        try {
            var entry = store.dao.get(id) ?: return
            if (entry.state != "QUEUED") return
            store.dao.state(id, "RUNNING", if (entry.ready) "Preparing to save" else "Connecting")
            val engines = DownloadEngines(store)
            fun limit(): Long {
                val perFile = JSONObject(entry.options).optLong("limitKiB", 0).coerceIn(0, 1_000_000) * 1024
                val overall = store.totalLimitKiB * 1024 / 2
                return listOf(perFile, overall).filter { it > 0 }.minOrNull() ?: 0
            }
            if (!entry.ready) when (DownloadKind.valueOf(entry.kind)) {
                DownloadKind.DIRECT, DownloadKind.TORRENT -> engines.aria(entry, ::limit)
                DownloadKind.VIDEO -> engines.video(entry, limit())
                DownloadKind.TELEGRAM -> (application as ToolkitApplication).telegram.download(entry)
            }
            currentCoroutineContext().ensureActive()
            entry = store.dao.get(id) ?: return
            if (entry.state == "SELECT_FILES") return
            check(entry.ready) { "The transfer did not produce a complete file." }
            export(entry)
            store.dao.state(id, "COMPLETE", "Saved to your folder")
            store.directory(id).deleteRecursively()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { store.dao.get(id)?.takeIf { it.state !in setOf("CANCELED", "PAUSED") }?.let { store.dao.state(id, "PAUSED", "Paused") } }
            throw e
        } catch (e: Exception) {
            // Upstream stderr can contain signed links. Never show or persist it verbatim.
            val message = when (e) {
                is SecurityException -> "Folder access expired. Choose the destination again and resume."
                is java.io.IOException -> "Connection or storage failed. Check your network and free space, then retry."
                else -> if (e.javaClass.name.startsWith("com.yausername")) "Media download failed. The source may be unavailable or need an engine update. Retry or inspect the link again."
                    else (e.message ?: "Transfer failed. Retry to continue.").replace(Regex("https?://\\S+"), "[source]").take(240)
            }
            store.dao.state(id, "FAILED", "Needs attention", message)
        } finally { active.remove(id) }
    }
    private suspend fun cleanupPending(entry: DownloadEntry) {
        if (entry.pendingUri.isNotBlank()) {
            val uri = Uri.parse(entry.pendingUri)
            // An already removed partial is a successful cleanup. Permission
            // failures remain errors so we never discard an inaccessible URI.
            val absent = try { contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { !it.moveToFirst() } ?: false } catch (_: java.io.FileNotFoundException) { true }
            val removed = absent || runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)
            check(removed) { "A partial saved file could not be removed. Remove it from your destination folder before retrying." }
            store.dao.get(entry.id)?.let { store.dao.update(it.copy(pendingUri = "")) }
        }
    }
    private suspend fun export(original: DownloadEntry) {
        cleanupPending(original)
        var entry = store.dao.get(original.id) ?: return
        store.dao.state(entry.id, "SAVING", "Saving to your folder")
        val tree = DocumentFile.fromTreeUri(this, Uri.parse(entry.destination)) ?: error("Choose a valid destination folder.")
        check(tree.canWrite()) { "Folder access expired. Choose the destination again and resume." }
        val completed = JSONObject(entry.exports)
        val directory = store.directory(entry.id)
        for (output in jsonObjects(entry.files)) {
            currentCoroutineContext().ensureActive()
            val path = output.getString("path")
            if (completed.has(path)) continue
            val input = File(directory, path)
            require(input.canonicalPath.startsWith(directory.canonicalPath + File.separator) && input.isFile) { "The staged file is unavailable." }
            val relative = output.getString("name").replace('\\', '/').split('/').filter(String::isNotBlank)
            require(relative.none { it == "." || it == ".." }) { "Invalid output name." }
            var parent = tree
            for (part in relative.dropLast(1)) {
                val name = safeDownloadName(part)
                parent = parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name) ?: error("Unable to create output folder.")
            }
            val name = safeDownloadName(relative.last())
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
            val document = parent.createFile(mime, name) ?: error("Unable to create output file.")
            entry = store.dao.get(entry.id) ?: return
            store.dao.update(entry.copy(pendingUri = document.uri.toString()))
            contentResolver.openOutputStream(document.uri, "w")?.use { target ->
                input.inputStream().use { source ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) { currentCoroutineContext().ensureActive(); val read = source.read(buffer); if (read < 0) break; target.write(buffer, 0, read) }
                }
            } ?: error("Unable to write output file.")
            completed.put(path, document.uri.toString())
            entry = store.dao.get(entry.id) ?: return
            store.dao.update(entry.copy(exports = completed.toString(), pendingUri = ""))
            (application as ToolkitApplication).database.exports().insert(ExportedDocument(document.uri.toString(), document.name ?: name, 0, input.length(), System.currentTimeMillis(), mime))
        }
    }
    override fun onTimeout(startId: Int, fgsType: Int) { active.values.forEach(Job::cancel); stopSelf() }
    override fun onDestroy() { scope.cancel(); if (wakeLock?.isHeld == true) wakeLock?.release(); super.onDestroy() }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("pause-all"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("Toolkit downloads").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(0, "Pause all", pause).build()
    }
    companion object {
        const val CHANNEL = "downloads"; const val NOTIFICATION = 4001
        fun start(context: Context, action: String = "run", id: String = "") {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(action).putExtra("id", id))
        }
    }
}
