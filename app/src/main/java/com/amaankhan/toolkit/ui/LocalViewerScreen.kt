package com.amaankhan.toolkit.ui

import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amaankhan.toolkit.documents.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LocalViewerScreen(uri: String, onBack: () -> Unit, onChooseFile: () -> Unit = onBack) {
    val context = LocalContext.current
    val reader = remember(context) { LocalDocumentReader(context) }
    var document by remember(uri) { mutableStateOf<LocalDocument?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var name by remember(uri) { mutableStateOf("Document") }
    var password by remember(uri) { mutableStateOf("") }
    var passwordRequired by remember(uri) { mutableStateOf(false) }
    var attempt by remember(uri) { mutableIntStateOf(0) }
    var section by rememberSaveable(uri) { mutableIntStateOf(0) }
    var source by rememberSaveable(uri) { mutableStateOf(false) }
    var query by rememberSaveable(uri) { mutableStateOf("") }
    var textZoom by rememberSaveable { mutableIntStateOf(100) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var textOnly by remember(uri) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(uri) { mutableStateOf(false) }
    LaunchedEffect(uri, attempt) {
        error = null
        document = null
        textOnly = false
        passwordRequired = false
        try {
            name = withContext(Dispatchers.IO) { reader.name(uri) }
            textOnly = name.substringAfterLast('.').lowercase() in listOf("txt", "csv", "tsv", "json", "xml", "log")
            if (!textOnly) document = reader.read(uri, password)
            password = ""
        } catch (e: CancellationException) { throw e }
        catch (e: OutOfMemoryError) { error = documentReadError(e) }
        catch (_: org.apache.poi.EncryptedDocumentException) { passwordRequired = true; error = "This spreadsheet needs a valid password. Enter it and try again." }
        catch (e: Exception) { error = documentReadError(e) }
    }
    BackHandler { if (fullScreen) fullScreen = false else onBack() }
    Scaffold(topBar = { if (!fullScreen) ScreenHeader(name, onBack) else Row(Modifier.fillMaxWidth().statusBarsPadding()) { IconButton(onClick = { fullScreen = false }) { GlyphIcon(Glyph.CLOSE, "Exit fullscreen") } } }) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
        when {
            error != null -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (passwordRequired) Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { PasswordField("File password", password, true) { password = it } }
                DocumentAccessRecovery(error!!, { attempt++ }, onChooseFile)
            }
            textOnly -> key(uri, attempt) { StructuredTextViewer(uri, name) { error = it } }
            document == null -> Box(Modifier.padding(24.dp)) { CircularProgressIndicator() }
            else -> {
                val doc = document!!
                if (doc.sections.isEmpty()) { DocumentAccessRecovery("No readable sections were found in this document.", { attempt++ }, onChooseFile); return@Column }
                val current = section.coerceIn(doc.sections.indices)
                if (!fullScreen) {
                if (doc.sections.size > 1) Row(Modifier.padding(horizontal = 12.dp)) {
                    IconButton(onClick = { section = current - 1 }, enabled = current > 0) { GlyphIcon(Glyph.BACK, "Previous section") }
                    Box(Modifier.weight(1f)) { ChoiceField(if (doc.kind == "Spreadsheet") "Sheet" else if (doc.kind == "EPUB") "Chapter" else "Slide", doc.sections[current].title, doc.sections.map { it.title }) { title -> section = doc.sections.indexOfFirst { it.title == title } } }
                    IconButton(onClick = { section = current + 1 }, enabled = current < doc.sections.lastIndex) { GlyphIcon(Glyph.CHEVRON, "Next section") }
                }
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (doc.source.isNotEmpty()) TextButton(onClick = { source = !source }) { Text(if (source) "Rendered" else "Source") }
                    TextButton(onClick = { textZoom = (textZoom - 10).coerceAtLeast(70) }) { Text("A−") }
                    TextButton(onClick = { textZoom = (textZoom + 10).coerceAtMost(220) }) { Text("A+") }
                    TextButton(onClick = { fullScreen = true }) { Text("Fullscreen") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolkitTextField(query, { query = it; webView?.findAllAsync(it) }, label = { Text("Find text") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { webView?.findNext(false) }, enabled = query.isNotBlank()) { GlyphIcon(Glyph.BACK, "Previous match") }
                    IconButton(onClick = { webView?.findNext(true) }, enabled = query.isNotBlank()) { GlyphIcon(Glyph.CHEVRON, "Next match") }
                }
                if (doc.note.isNotBlank()) Text(doc.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else if (doc.sections.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { section = current - 1 }, enabled = current > 0) { GlyphIcon(Glyph.BACK, "Previous section") }
                    Text(doc.sections[current].title, modifier = Modifier.weight(1f).padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { section = current + 1 }, enabled = current < doc.sections.lastIndex) { GlyphIcon(Glyph.CHEVRON, "Next section") }
                }
                val html = if (source) "<pre>${htmlEscape(doc.source)}</pre>" else doc.sections[current].html
                SafeDocumentWebView(documentHtml(html), textZoom, Modifier.weight(1f).fillMaxWidth(),
                    onNavigate = if (doc.sections.size > 1 && !source) { { delta -> section = (current + delta).coerceIn(doc.sections.indices) } } else null
                ) { webView = it }
            }
        }
    } }
}

@Composable internal fun DocumentAccessRecovery(message: String, onRetry: () -> Unit, onChooseFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        PrimaryAction("Retry", onClick = onRetry)
        OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) { Text("Choose another file") }
    }
}

@Composable fun SafeDocumentWebView(html: String, zoom: Int = 100, modifier: Modifier = Modifier, onNavigate: ((Int) -> Unit)? = null, onView: (WebView) -> Unit = {}) {
    var previous by remember { mutableStateOf("") }
    AndroidView(factory = { context -> SectionWebView(context).apply {
        settings.javaScriptEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false
        settings.blockNetworkLoads = true; settings.domStorageEnabled = false; settings.databaseEnabled = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.builtInZoomControls = true; settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        onView(this)
    } }, update = { view -> view.onNavigate = onNavigate; view.settings.textZoom = zoom; if (previous != html) { previous = html; view.loadDataWithBaseURL("https://document.toolkit.invalid/", html, "text/html", "UTF-8", null) } }, onRelease = { it.stopLoading(); it.destroy() }, modifier = modifier.clipToBounds())
}

@Composable private fun StructuredTextViewer(uri: String, name: String, onReadError: (String) -> Unit) {
    var mode by rememberSaveable(uri) { mutableStateOf("Source") }
    val extension = name.substringAfterLast('.').lowercase()
    val context = LocalContext.current
    val reader = remember(context) { LocalDocumentReader(context) }
    var formatted by remember(uri, mode) { mutableStateOf<String?>(null) }
    val reportError by rememberUpdatedState(onReadError)
    LaunchedEffect(uri, mode) {
        if (mode != "Source") {
            try {
            val (text, more) = reader.textChunk(uri, 0, 512 * 1024)
            formatted = if (more) "Structured preview is limited to 512K characters. Use Source to read the complete file." else runCatching {
                when (extension) {
                    "json" -> if (text.trimStart().startsWith("[")) "<pre>${htmlEscape(org.json.JSONArray(text).toString(2))}</pre>" else "<pre>${htmlEscape(org.json.JSONObject(text).toString(2))}</pre>"
                    "csv", "tsv" -> csvTable(text, if (extension == "csv") ',' else '\t')
                    else -> "<pre>${htmlEscape(text)}</pre>"
                }
            }.getOrElse { "<p>Unable to format this file. Open Source to inspect it.</p>" }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) { reportError(documentReadError(e)) }
            catch (e: Exception) { reportError(documentReadError(e)) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (extension in listOf("csv", "tsv", "json")) Row(Modifier.padding(horizontal = 16.dp)) { TextButton(onClick = { mode = "Source" }) { Text("Source") }; TextButton(onClick = { mode = "Structured" }) { Text(if (extension == "json") "Formatted" else "Table") } }
        if (mode == "Source") TextPreview(uri, Modifier.weight(1f).fillMaxWidth(), onReadError = reportError)
        else if (formatted == null) CircularProgressIndicator(Modifier.padding(24.dp)) else SafeDocumentWebView(documentHtml(formatted!!), modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
internal fun csvTable(text: String, delimiter: Char): String {
    val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
    while (i < text.length) { val c = text[i]
        when {
            c == '"' && quoted && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == delimiter && !quoted -> { row += cell.toString(); cell.clear() }
            c == '\n' && !quoted -> { row += cell.toString().removeSuffix("\r"); cell.clear(); rows += row; row = mutableListOf() }
            else -> cell.append(c)
        }; i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    return "<table>" + rows.joinToString("") { cells -> "<tr>" + cells.joinToString("") { "<td style=\"white-space:pre-wrap\">${htmlEscape(it)}</td>" } + "</tr>" } + "</table>"
}
