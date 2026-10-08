package com.amaankhan.toolkit.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaankhan.toolkit.downloads.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun TelegramScreen(model: DownloadsModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val telegram = model.telegram
    val status by telegram.status.collectAsStateWithLifecycle()
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var value by remember(status.step) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var chats by remember { mutableStateOf(emptyList<TelegramChat>()) }
    var chat by remember { mutableStateOf<TelegramChat?>(null) }
    var chatLimit by remember { mutableIntStateOf(100) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Documents") }
    var files by remember { mutableStateOf(emptyList<TelegramFile>()) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var next by remember { mutableLongStateOf(0) }
    var searched by remember { mutableStateOf(false) }
    var folder by rememberSaveable { mutableStateOf(model.store.defaultFolder) }
    var showSignout by remember { mutableStateOf(false) }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) model.action {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folder = uri.toString(); model.store.defaultFolder = folder
    } }
    fun perform(block: suspend () -> Unit) { if (working) return; working = true; model.action { try { block() } finally { working = false } } }
    fun loadFiles(more: Boolean = false) { val current = chat ?: return; perform {
        val result = telegram.files(current.id, query, type, if (more) next else 0)
        files = if (more) (files + result.files).distinctBy { it.message } else result.files
        if (!more) selected = emptySet()
        next = result.next; searched = true
    } }
    LaunchedEffect(Unit) { model.action { telegram.start() } }
    LaunchedEffect(status.step) { if (status.step == "ready") perform { chats = telegram.chats("") } }
    LaunchedEffect(chat?.id, type) { if (chat != null) loadFiles() }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (chat != null) { chat = null; query = ""; files = emptyList(); searched = false } else onBack() }) { GlyphIcon(Glyph.BACK, "Back") }
            Text(chat?.title ?: "Telegram", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (status.step !in setOf("credentials", "connecting")) TextButton(onClick = { showSignout = true }) { Text("Sign out") }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (status.error.isNotBlank()) item { Text(status.error, color = MaterialTheme.colorScheme.error) }
            when (status.step) {
                "credentials" -> {
                    item { Text("Connect your Telegram account", style = MaterialTheme.typography.titleMedium); EmptyNote("Browse channels and groups you already have access to. No bot token is needed.") }
                    item { InfoCard("Get your API credentials", "1. Open my.telegram.org and sign in.\n2. Choose API development tools.\n3. Use your existing application, or create one with a title and short name.\n4. Copy its api_id and api_hash below.\n\nThese stay encrypted on this device. Your Telegram login code and 2FA password are used only for signing in.") }
                    item { OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps"))) }, modifier = Modifier.fillMaxWidth()) { Text("Open Telegram API settings") } }
                    item { ToolkitTextField(apiId, { apiId = it.filter(Char::isDigit).take(12) }, label = { Text("API ID") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { PasswordField("API hash", apiHash, !working) { apiHash = it.take(32) } }
                    item { PrimaryAction("Connect", !working && apiId.isNotBlank() && apiHash.length == 32) { perform { telegram.connect(apiId, apiHash); apiHash = "" } } }
                }
                "phone", "code", "password", "email", "email-code" -> {
                    item { Text(status.hint, style = MaterialTheme.typography.titleMedium) }
                    item { if (status.step == "password") PasswordField("Two-step verification password", value, !working) { value = it } else ToolkitTextField(value, { value = it.take(200) }, label = { Text(when (status.step) { "phone" -> "Phone number with country code"; "email" -> "Email address"; else -> "Login code" }) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { PrimaryAction("Continue", !working && value.isNotBlank()) { perform { telegram.submit(value); value = "" } } }
                }
                "ready" -> {
                    item { ToolkitTextField(query, { query = it.take(200) }, label = { Text(if (chat == null) "Search your chats" else "Search filenames / captions") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedButton(onClick = { if (chat == null) perform { chats = telegram.chats(query, chatLimit) } else loadFiles() }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Search") } }
                    if (chat == null) {
                        items(chats, key = { it.id }) { item -> Surface(onClick = { chat = item; query = "" }, shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.title, Modifier.weight(1f)); GlyphIcon(Glyph.CHEVRON) } } }
                        item { TextButton(onClick = { chatLimit += 100; perform { chats = telegram.chats(query, chatLimit) } }, enabled = !working) { Text("Load more chats") } }
                    } else {
                        item { ChoiceField("File type", type, listOf("Documents", "Videos", "Audio", "Photos", "All media"), enabled = !working) { type = it } }
                        if (searched && files.isEmpty()) item { EmptyNote("No downloadable files on this page. Protected messages cannot be saved.") }
                        items(files, key = { it.message }) { file -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(file.message in selected, { selected = if (it) selected + file.message else selected - file.message })
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) { Text(file.name, style = MaterialTheme.typography.bodyMedium); Text("${file.kind} · ${formatBytes(file.bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } }
                        if (next != 0L) item { OutlinedButton(onClick = { loadFiles(true) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Load more files") } }
                    }
                }
                "closed", "error" -> item { PrimaryAction("Reconnect", !working) { perform { telegram.start() } } }
                else -> item { EmptyNote(status.hint.ifBlank { "Connecting…" }) }
            }
        }
        if (status.step == "ready" && chat != null) ActionBar {
            TextButton(onClick = { folderPicker.launch(folder.takeIf(String::isNotBlank)?.let(Uri::parse)) }) { Text(if (folder.isBlank()) "Choose destination folder" else "Destination selected · Change") }
            PrimaryAction("Queue ${selected.size} files", !working && selected.isNotEmpty() && folder.isNotBlank()) { perform {
                telegram.enqueue(files.filter { it.message in selected }, folder)
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
                    DownloadService.start(context); onBack()
                }
            } }
        }
    }
    if (showSignout) AlertDialog(onDismissRequest = { showSignout = false }, title = { Text("Sign out of Telegram?") }, text = { Text("This removes this device’s Toolkit session and cached Telegram files. Downloads already saved to your chosen folder remain.") }, confirmButton = { TextButton(onClick = { showSignout = false; perform { telegram.signOut() } }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { showSignout = false }) { Text("Cancel") } })
}
