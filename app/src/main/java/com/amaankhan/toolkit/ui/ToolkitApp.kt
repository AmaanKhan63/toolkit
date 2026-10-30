package com.amaankhan.toolkit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaankhan.toolkit.R
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.data.Preferences
import com.amaankhan.toolkit.documents.retainDocumentReadPermission
import com.amaankhan.toolkit.documents.isPdfDocument

data class ToolGroup(val title: String, val description: String, val glyph: Glyph, val ids: List<String>)
val toolGroups = listOf(
    ToolGroup("Create PDF", "Images, scans, documents", Glyph.CREATE, listOf("D01", "P05", "D06", "D07", "D08", "D13", "P15")),
    ToolGroup("Convert", "PDF to other formats", Glyph.CONVERT, listOf("D09", "D10", "D11", "D12", "P14")),
    ToolGroup("Organize pages", "Merge, split, arrange", Glyph.LAYERS, listOf("D02", "P01", "P02", "P03", "P04", "P09")),
    ToolGroup("Protect", "PDF & Excel passwords", Glyph.LOCK, listOf("D03", "D04")),
    ToolGroup("Optimize", "Size & metadata", Glyph.COMPRESS, listOf("D05", "P16")),
    ToolGroup("Workflows", "Chain your tools", Glyph.FLOW, listOf("P17")),
    ToolGroup("Download tools", "Video, links, Telegram & torrents", Glyph.DOWNLOAD, emptyList()),
)

@Composable fun ToolkitApp(model: ToolkitViewModel, preferences: Preferences) {
    var tab by rememberSaveable { mutableStateOf("Tools") }
    var downloadDetail by remember { mutableStateOf(false) }
    var originRoute by rememberSaveable { mutableStateOf("") }
    var route by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerMime by rememberSaveable { mutableStateOf("application/pdf") }
    var viewerUri by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSelection by rememberSaveable { mutableIntStateOf(0) }
    val notice by model.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exports by model.exports.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) {
        retainDocumentReadPermission(resolver, uri)
        viewerMime = if (isPdfDocument(resolver, uri)) "application/pdf" else "local/document"
        viewerUri = uri.toString()
        viewerSelection++
    } }
    val chooseFile = { openDocument.launch(arrayOf("application/pdf", "text/*", "application/json", "application/xml", "application/epub+zip", "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/octet-stream")) }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
    fun openTool(tool: Tool) {
        if (tool.available || tool.id in implementedPdfTools || tool.id in implementedOfficeTools || tool.id in implementedRichTools) { model.useTool(tool.id); originRoute = route; route = tool.id } else pending = tool.name
    }
    val back = { route = "" }
    BackHandler(route.isNotEmpty() || tab != "Tools") { if (route.isNotEmpty()) route = "" else tab = "Tools" }
    when {
        viewerUri != null -> key(viewerSelection) { if (viewerMime == "application/pdf") PdfViewerScreen(viewerUri!!, onBack = { viewerUri = null }, onChooseFile = chooseFile) else LocalViewerScreen(viewerUri!!, onBack = { viewerUri = null }, onChooseFile = chooseFile) }
        route == "Download tools" -> DownloadsScreen(initialPage = "sources", onExit = { route = "" }, onOpenFiles = { route = ""; tab = "Files" }, onQueued = { route = ""; tab = "Transfers" })
        route == "D01" -> ImagesToPdfScreen(model) { route = originRoute }
        route == "P05" -> ScanScreen { route = originRoute }
        route in implementedPdfTools -> PdfToolScreen(route) { route = originRoute }
        route in implementedRichTools -> RichToolScreen(route) { route = originRoute }
        route == "P17" -> WorkflowScreen { route = originRoute }
        route in implementedOfficeTools -> OfficeToolScreen(route) { route = originRoute }
        route == "Settings" -> SettingsScreen(model, preferences, back)
        route == "Saved" || toolGroups.any { it.title == route } -> {
            val selected = if (route == "Saved") documentTools.filter { it.id in preferences.favorites }
                else documentTools.filter { it.id in toolGroups.first { group -> group.title == route }.ids }
            Scaffold(topBar = { ScreenHeader(route, back) }) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected.isEmpty()) item { InfoCard("Keep your tools close", "Save tools using the bookmark beside their name. Find them here whenever you need them.") }
                    items(selected, key = { it.id }) { tool -> ToolRow(tool, tool.id in preferences.favorites, { openTool(tool) }, { model.favorite(tool.id) }) }
                }
            }
        }
        else -> Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (tab != "Transfers" || !downloadDetail) Surface(color = MaterialTheme.colorScheme.background) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tab == "Tools") { Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)) }
                    Text(if (tab == "Tools") "Toolkit" else tab, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { route = "Settings" }) { GlyphIcon(Glyph.SETTINGS, "Settings") }
                }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf("Tools" to Glyph.GRID, "Transfers" to Glyph.DOWNLOAD, "Files" to Glyph.FOLDER).forEach { (name, icon) ->
                        NavigationBarItem(selected = name == tab, onClick = { tab = name }, icon = { GlyphIcon(icon) }, label = { Text(name) })
                    }
                }
            },
        ) { padding ->
            when (tab) {
                "Tools" -> Dashboard(preferences, { route = it }, ::openTool, model::favorite, Modifier.padding(padding))
                "Transfers" -> DownloadsScreen(Modifier.padding(padding), onDetailChanged = { downloadDetail = it }, onOpenFiles = { tab = "Files" })
                "Files" -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Surface(shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlyphIcon(Glyph.FILE, modifier = Modifier.size(36.dp))
                        Text("Your document reader", style = MaterialTheme.typography.titleLarge)
                        Text("Read documents, spreadsheets and ebooks in one place.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryAction("Open a file", onClick = chooseFile)
                    } } }
                    item { SectionTitle("Formats you can open") }
                    item { Text("PDF · Word · Excel · PowerPoint\nEPUB · Markdown · Text & CSV · HTML", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { SectionTitle("Saved files") }
                    if (exports.isEmpty()) item { InfoCard("Your saved files will appear here", "Files created by Toolkit and completed transfers are collected here. Open any supported document above to start reading.") }
                    items(exports, key = { it.uri }) { file ->
                        Surface(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(file.name, style = MaterialTheme.typography.titleSmall)
                                Text((if (file.pages > 0) "${file.pages} pages · " else "") + formatBytes(file.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                DocumentActions(file, onPreview = if (file.mime.startsWith("video/") || file.mime.startsWith("audio/") || file.mime.startsWith("image/") || file.mime in setOf("application/zip", "application/octet-stream")) null else { { viewerMime = file.mime; viewerUri = file.uri } })
                                TextButton(onClick = { model.removeHistory(file.uri) }) { Text("Remove from history") }
                            }
                        }
                    }
                }
            }
        }
    }
    pending?.let { name -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(name) },
        text = { Text("This feature is part of the selected Toolkit scope. Its processing engine is still being implemented.") },
        confirmButton = { TextButton(onClick = { pending = null }) { Text("Close") } }) }
}

@Composable private fun Dashboard(preferences: Preferences, navigate: (String) -> Unit, onTool: (Tool) -> Unit, onSave: (String) -> Unit, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val singleColumn = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ToolkitTextField(query, { query = it }, placeholder = { Text("Search tools or formats") }, singleLine = true,
            leadingIcon = { GlyphIcon(Glyph.SEARCH) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { GlyphIcon(Glyph.CLOSE, "Clear search") } },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)) }
        item { Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (query.isBlank()) "Your tools" else "Search results", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { navigate("Saved") }) { Text("Saved"); Spacer(Modifier.width(8.dp)); GlyphIcon(Glyph.STAR) }
        } }
        if (query.isBlank()) {
            items(toolGroups.chunked(if (singleColumn) 1 else 2)) { groups -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { group -> Surface(onClick = { navigate(group.title) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    if (singleColumn) Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(group.glyph, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GlyphIcon(Glyph.CHEVRON)
                    } else
                    Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlyphIcon(group.glyph, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(group.title, style = MaterialTheme.typography.titleSmall)
                        Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            } }
            item { EmptyNote("Document tools work on your device.") }
        } else {
            val results = documentTools.filter { it.name.contains(query.trim(), true) }
            val matchesDownloads = listOf("download", "youtube", "video", "audio", "telegram", "torrent", "link").any { it.contains(query.trim(), true) || query.contains(it, true) }
            if (matchesDownloads) item {
                Surface(onClick = { navigate("Download tools") }, shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { GlyphIcon(Glyph.DOWNLOAD); Text("Download tools", style = MaterialTheme.typography.titleSmall) } }
            }
            if (results.isEmpty() && !matchesDownloads) item { EmptyNote("No tools match “${query.trim()}”. Try a format such as PDF or Excel.") }
            items(results, key = { it.id }) { tool -> ToolRow(tool, tool.id in preferences.favorites, { onTool(tool) }, { onSave(tool.id) }) }
        }
    }
}

@Composable fun ToolRow(tool: Tool, saved: Boolean, onClick: () -> Unit, onSave: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClick, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphIcon(tool.glyph, tint = MaterialTheme.colorScheme.primary)
                    Column { Text(tool.name, style = MaterialTheme.typography.titleSmall)
                        if (!tool.available && tool.id !in implementedPdfTools && tool.id !in implementedOfficeTools && tool.id !in implementedRichTools) Text("In development", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            IconToggleButton(checked = saved, onCheckedChange = { onSave() }) {
                GlyphIcon(if (saved) Glyph.CHECK else Glyph.STAR, if (saved) "Unsave ${tool.name}" else "Save ${tool.name}", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
