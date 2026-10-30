package com.amaankhan.toolkit.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.downloads.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DownloadsScreen(modifier: Modifier = Modifier, model: DownloadsModel = viewModel(), onDetailChanged: (Boolean) -> Unit = {}, initialPage: String = "queue", onExit: () -> Unit = {}, onOpenFiles: () -> Unit = {}, onQueued: () -> Unit = {}) {
    val entries by model.entries.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var filter by rememberSaveable { mutableStateOf("Active") }
    var selectId by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationJob by remember { mutableStateOf<DownloadEntry?>(null) }
    var settings by remember { mutableStateOf(false) }
    var recentReports by remember { mutableStateOf(emptyList<Pair<String, Long>>()) }
    LaunchedEffect(settings) { if (settings) recentReports = model.recentErrorReports() }
    var reportToSave by rememberSaveable { mutableStateOf<String?>(null) }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val id = reportToSave
        reportToSave = null
        if (uri != null && id != null) model.saveErrorReport(id, uri)
    }
    fun saveErrorReport(id: String) { reportToSave = id; saveReport.launch("Toolkit-error-$id.txt") }
    LaunchedEffect(page, selectId) { onDetailChanged(page != "queue" || selectId != null) }
    DisposableEffect(Unit) { onDispose { onDetailChanged(false) } }
    val context = LocalContext.current
    val folder = rememberLauncherForActivityResult(DestinationFolderContract()) { uri -> if (uri != null) model.action {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        destinationJob?.let { model.resume(it, uri.toString()) }; destinationJob = null
    } }
    fun backToList() { if (page == "sources" && initialPage == "sources") onExit() else page = if (initialPage == "sources") "sources" else "queue"; selectId = null }
    BackHandler(page != "queue" || selectId != null) { backToList() }
    Column(modifier.fillMaxSize()) {
        when {
            selectId != null -> entries.firstOrNull { it.id == selectId }?.let { selected -> TorrentFileSelection(selected, { selectId = null }) { model.selectTorrent(selected, it); selectId = null } }
            page == "telegram" -> TelegramScreen(model, onBack = { backToList() }, onQueued = { page = "queue"; onQueued() })
            page == "sources" -> Column(Modifier.fillMaxSize()) {
                ScreenHeader("Download tools", { backToList() })
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { EmptyNote("Choose where to get your file. Track progress in Transfers and find saved files in Files.") }
                    item { DownloadSource("Video & audio", "YouTube and other supported links", Glyph.DOWNLOAD, Modifier.fillMaxWidth()) { page = "VIDEO" } }
                    item { DownloadSource("Direct link", "Download a file from its URL", Glyph.FILE, Modifier.fillMaxWidth()) { page = "DIRECT" } }
                    item { DownloadSource("Telegram", "Save files from your chats", Glyph.FOLDER, Modifier.fillMaxWidth()) { page = "telegram" } }
                    item { DownloadSource("Torrent / magnet", "Choose which files to download", Glyph.FLOW, Modifier.fillMaxWidth()) { page = "TORRENT" } }
                }
            }
            page != "queue" -> NewDownload(DownloadKind.valueOf(page), model, { backToList() }, { page = "queue"; onQueued() })
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { page = "sources" }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { GlyphIcon(Glyph.PLUS); Spacer(Modifier.width(8.dp)); Text("New transfer") }
                    IconButton(onClick = { settings = true }) { GlyphIcon(Glyph.SETTINGS, "Transfer settings") }
                } }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Active", "Completed", "All").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
                } }
                val visible = entries.filter { when (filter) { "Active" -> it.state !in setOf("COMPLETE", "CANCELED"); "Completed" -> it.state == "COMPLETE"; else -> true } }
                if (visible.isEmpty()) item { InfoCard(if (filter == "Completed") "No completed transfers yet" else "No transfers here", "Start a download from Tools or tap New transfer. You can pause, resume and track it here.") }
                items(visible, key = { it.id }) { entry ->
                    Surface(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text("${DownloadKind.valueOf(entry.kind).label} · ${entry.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.state in setOf("RUNNING", "SAVING", "QUEUED")) {
                            if (entry.progress in 0..100 && entry.state == "RUNNING") LinearProgressIndicator(progress = { entry.progress / 100f }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (entry.bytes > 0 || entry.total > 0) Text("${formatBytes(entry.bytes)}${if (entry.total > 0) " / ${formatBytes(entry.total)}" else ""}${if (entry.speed > 0) " · ${formatBytes(entry.speed)}/s" else ""}", style = MaterialTheme.typography.bodySmall)
                        if (entry.error.isNotBlank()) Text(entry.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        mediaReportId(entry.error)?.let { id ->
                            OutlinedButton(onClick = { saveErrorReport(id) }, modifier = Modifier.fillMaxWidth()) { Text("Save error report") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (entry.state) {
                                "QUEUED", "RUNNING", "SAVING" -> TextButton(onClick = { model.control(entry, "pause") }) { Text("Pause") }
                                "PAUSED", "FAILED" -> {
                                    TextButton(onClick = { model.resume(entry) }) { Text(if (entry.ready) "Retry saving" else if (entry.state == "FAILED") "Retry" else "Resume") }
                                    TextButton(onClick = { destinationJob = entry; folder.launch(Uri.parse(entry.destination)) }) { Text("Change folder") }
                                    if (entry.kind == "VIDEO" && entry.state == "FAILED") TextButton(onClick = { settings = true }) { Text("Media engine") }
                                }
                                "SELECT_FILES" -> TextButton(onClick = { selectId = entry.id }) { Text("Choose files") }
                                "COMPLETE", "CANCELED" -> TextButton(onClick = { model.remove(entry) }) { Text("Remove from history") }
                            }
                            if (entry.state !in setOf("COMPLETE", "CANCELED")) TextButton(onClick = { model.control(entry, "cancel") }) { Text("Cancel") }
                        }
                        if (entry.state == "COMPLETE") TextButton(onClick = onOpenFiles) { Text("View saved files") }
                    } }
                }
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Transfer settings", style = MaterialTheme.typography.titleLarge)
            var total by remember { mutableStateOf(model.store.totalLimitKiB.toString()) }
            ToolkitTextField(total, { value -> total = value.filter(Char::isDigit).take(7); model.store.totalLimitKiB = total.toLongOrNull() ?: 0 }, label = { Text("Combined limit (KB/s)") }, supportingText = { Text("0 = unlimited. Shared across two direct / media transfer slots. Changes apply to media when its next transfer starts. Telegram uses its own connection.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            val updating by model.busy.collectAsStateWithLifecycle()
            OutlinedButton(onClick = model::updateMediaEngine, enabled = !updating, modifier = Modifier.fillMaxWidth()) { Text(if (updating) "Updating media engine…" else "Update yt-dlp") }
            EmptyNote("Update the media engine if a supported site stops working. Transfers keep their files and progress when paused.")
            if (recentReports.isNotEmpty()) {
                val labels = recentReports.map { (id, time) -> "$id · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))}" }
                ChoiceField("Recent error reports", "Choose a report to save", labels) { label -> saveErrorReport(label.substringBefore(" ·")) }
                EmptyNote("The latest 20 reports stay on this device, including after retries. Save a report to review and share it.")
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = { model.error.value = null }, title = { Text("Unable to continue") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(error!!)
            mediaReportId(error!!)?.let { id -> OutlinedButton(onClick = { saveErrorReport(id) }) { Text("Save error report") } }
        } }, confirmButton = { TextButton(onClick = { model.error.value = null }) { Text("OK") } })
    if (notice != null) AlertDialog(onDismissRequest = { model.notice.value = null }, title = { Text("Downloader status") }, text = { Text(notice!!) }, confirmButton = { TextButton(onClick = { model.notice.value = null }) { Text("OK") } })
}
@Composable private fun DownloadSource(title: String, detail: String, glyph: Glyph, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlyphIcon(Glyph.CHEVRON)
        }
    }
}

@Composable private fun NewDownload(kind: DownloadKind, model: DownloadsModel, back: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(kind) { model.inspection.value = null }
    var links by rememberSaveable(kind) { mutableStateOf("") }
    var name by rememberSaveable(kind) { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf(model.store.defaultFolder) }
    var torrent by rememberSaveable { mutableStateOf<String?>(null) }
    var container by rememberSaveable { mutableStateOf("mp4") }
    var quality by rememberSaveable { mutableStateOf("1080p") }
    var limit by rememberSaveable { mutableStateOf("0") }
    var seed by rememberSaveable { mutableStateOf("0") }
    var upload by rememberSaveable { mutableStateOf("256") }
    val busy by model.busy.collectAsStateWithLifecycle()
    val inspected by model.inspection.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var format by remember { mutableStateOf("") }
    val rows = remember(inspected) { inspected?.optJSONArray("entries")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty() }
    LaunchedEffect(inspected) { selected = rows.indices.toSet(); format = "" }
    val folderPicker = rememberLauncherForActivityResult(DestinationFolderContract()) { uri -> if (uri != null) model.action {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folder = uri.toString()
    } }
    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> torrent = uri?.toString() }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(kind.label, back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ToolkitTextField(links, { links = it.take(32000); model.inspection.value = null }, label = { Text(if (kind == DownloadKind.TORRENT) "Magnet link" else "Link or multiple links, one per line") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
            if (kind == DownloadKind.TORRENT) {
                item { OutlinedButton(onClick = { torrentPicker.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (torrent == null) "Choose a .torrent file instead" else "Torrent file selected · Replace") } }
                item { NumericDownloadField("Seed after downloading (minutes)", seed) { seed = it }; EmptyNote("0 stops at completion. Uploading to peers also happens during the download.") }
                item { NumericDownloadField("Upload limit (KB/s)", upload) { upload = it } }
            }
            if (kind == DownloadKind.DIRECT) item { ToolkitTextField(name, { name = it.take(180) }, label = { Text("Filename (optional for a single link)") }, modifier = Modifier.fillMaxWidth()) }
            if (kind == DownloadKind.VIDEO) {
                item { ChoiceField("Format", container, listOf("mkv", "mp4", "mp3", "m4a", "opus")) { container = it } }
                item { ChoiceField("Video quality", quality, listOf("Best available", "2160p", "1440p", "1080p", "720p", "480p", "360p")) { quality = it } }
                item { OutlinedButton(onClick = { model.inspect(links.trim()) }, enabled = !busy && links.trim().isNotBlank() && !links.trim().contains('\n'), modifier = Modifier.fillMaxWidth()) { Text("Inspect formats / playlist") } }
                if (rows.isNotEmpty()) {
                    item { Text("Playlist · ${rows.size} items", style = MaterialTheme.typography.titleSmall); EmptyNote("Select videos to add to the queue. Up to 500 entries per inspection.") }
                    items(rows.indices.toList(), key = { it }) { i -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in selected, { selected = if (it) selected + i else selected - i })
                        Text(rows[i].optString("title", "Video ${i + 1}"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    } }
                } else if (inspected != null) item {
                    Text(inspected!!.optString("title", "Available media"), style = MaterialTheme.typography.titleSmall)
                    val formats = inspected!!.optJSONArray("formats")
                    val choices = listOf("Automatic") + (0 until (formats?.length() ?: 0)).mapNotNull { i -> formats?.optJSONObject(i)?.let { f -> "${f.optString("format_id")} · ${f.optString("ext")} · ${f.optString("resolution", f.optString("format_note"))}" } }
                    ChoiceField("Source stream", choices.firstOrNull { it.startsWith("$format ·") } ?: "Automatic", choices) { format = if (it == "Automatic") "" else it.substringBefore(" ·") }
                    EmptyNote("Automatic pairs the best compatible video and audio. A specific stream may contain video or audio only.")
                }
            }
            item { NumericDownloadField("Download limit (KB/s, 0 = unlimited)", limit) { limit = it } }
            item { OutlinedButton(onClick = { folderPicker.launch(folder.takeIf(String::isNotBlank)?.let(Uri::parse)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (folder.isBlank()) "Choose destination folder" else "Destination selected · Change") } }
            item { if (folder.isNotBlank()) Text("Save to: ${destinationFolderName(folder)}", style = MaterialTheme.typography.bodyMedium); FolderGuidance() }
        }
        ActionBar {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryAction(if (kind == DownloadKind.TORRENT) "Read torrent files" else "Add to queue", enabled = !busy && folder.isNotBlank() && (links.isNotBlank() || torrent != null)) {
                if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
                val sources = when {
                    kind == DownloadKind.TORRENT -> listOf((if (torrent != null) "local-torrent" else links.trim()) to "Torrent download")
                    kind == DownloadKind.VIDEO && rows.isNotEmpty() -> selected.sorted().map { i ->
                        val row = rows[i]; (row.optString("webpage_url").ifBlank { row.optString("url") }) to row.optString("title", "Video ${i + 1}")
                    }
                    else -> links.lines().map(String::trim).filter(String::isNotBlank).map { url ->
                        url to if (name.isNotBlank() && !links.trim().contains('\n')) name else if (kind == DownloadKind.VIDEO) inspected?.optString("title")?.takeIf(String::isNotBlank) ?: "Media download"
                        else Uri.parse(url).lastPathSegment?.takeIf(String::isNotBlank) ?: "download.bin"
                    }
                }
                val options = JSONObject().put("container", container).put("height", quality.removeSuffix("p").toIntOrNull() ?: 0).put("format", format)
                    .put("limitKiB", limit.toLongOrNull() ?: 0).put("seedMinutes", seed.toIntOrNull() ?: 0).put("uploadKiB", upload.toLongOrNull() ?: 256)
                model.queue(kind, sources, folder, options, torrent?.let(Uri::parse), onQueued)
            }
        }
    }
}
@Composable private fun NumericDownloadField(label: String, value: String, update: (String) -> Unit) {
    ToolkitTextField(value, { update(it.filter(Char::isDigit).take(7)) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
@Composable private fun TorrentFileSelection(entry: DownloadEntry, back: () -> Unit, start: (Set<Int>) -> Unit) {
    val files = remember(entry.files) { jsonObjects(entry.files) }
    var selected by remember(entry.id) { mutableStateOf(files.map { it.getInt("index") }.toSet()) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose torrent files", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp)) {
            item { Text("${selected.size} selected · ${formatBytes(files.filter { it.getInt("index") in selected }.sumOf { it.optLong("bytes") })}"); TextButton(onClick = { selected = if (selected.size == files.size) emptySet() else files.map { it.getInt("index") }.toSet() }) { Text("Select / clear all") } }
            items(files) { file -> val index = file.getInt("index"); Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(index in selected, { selected = if (it) selected + index else selected - index })
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) { Text(file.getString("name")); Text(formatBytes(file.optLong("bytes")), style = MaterialTheme.typography.bodySmall) }
            } }
        }
        ActionBar { PrimaryAction("Download selected files", selected.isNotEmpty()) { start(selected) } }
    }
}
