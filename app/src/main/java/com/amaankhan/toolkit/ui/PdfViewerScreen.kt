package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.PdfReaderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun PdfViewerScreen(uri: String, onBack: () -> Unit) {
    val model: PdfReaderModel = viewModel(key = "reader-$uri")
    val state by model.state.collectAsStateWithLifecycle()
    val matches by model.matches.collectAsStateWithLifecycle()
    val searching by model.searching.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable(uri) { mutableIntStateOf(0) }
    var jump by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sheet by remember { mutableStateOf("") }
    var pageNumber by remember { mutableStateOf("") }
    val preferences = LocalContext.current.getSharedPreferences("pdf-bookmarks", 0)
    val bookmarkKey = remember(uri) { java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) } }
    var bookmarks by remember(uri) { mutableStateOf(preferences.getStringSet(bookmarkKey, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet()) }
    fun navigate(index: Int) { jump = index to ((jump?.second ?: 0) + 1); sheet = "" }
    LaunchedEffect(uri) { model.open(uri) }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { ScreenHeader("PDF viewer", onBack) }) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
        when {
            state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Opening document…", Modifier.padding(20.dp)) }
            state.passwordRequired -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.error); PasswordField("PDF password", password, true) { password = it }
                PrimaryAction("Open PDF", password.isNotEmpty()) { model.open(uri, password); password = "" }
            }
            state.file == null -> Text(state.error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            else -> {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { sheet = "Pages" }) { Text("Pages") }
                    TextButton(onClick = { sheet = "Search" }) { Text("Search") }
                    TextButton(onClick = { sheet = "Bookmarks" }) { Text("Bookmarks") }
                    IconButton(onClick = {
                        bookmarks = if (page in bookmarks) bookmarks - page else bookmarks + page
                        preferences.edit().putStringSet(bookmarkKey, bookmarks.map(Int::toString).toSet()).apply()
                    }) { GlyphIcon(if (page in bookmarks) Glyph.CHECK else Glyph.STAR, if (page in bookmarks) "Remove page bookmark" else "Bookmark page") }
                }
                if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                PdfPreview(Uri.fromFile(state.file).toString(), Modifier.weight(1f).fillMaxWidth(), jump) { page = it }
            }
        }
    } }
    if (sheet.isNotBlank()) ModalBottomSheet(onDismissRequest = { sheet = "" }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(sheet, style = MaterialTheme.typography.titleLarge) }
            when (sheet) {
                "Search" -> {
                    item { OutlinedTextField(query, { query = it }, label = { Text("Find text") }, modifier = Modifier.fillMaxWidth(), singleLine = true); PrimaryAction("Search", !searching) { model.find(query) } }
                    if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (!searching && matches.isEmpty()) item { EmptyNote("No matching pages. Scanned PDFs may have no searchable text.") }
                    items(matches) { (index, snippet) -> Surface(onClick = { navigate(index) }, shape = MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall); Text(snippet, style = MaterialTheme.typography.bodySmall) } } }
                }
                "Bookmarks" -> {
                    if (bookmarks.isEmpty() && state.outline.isEmpty()) item { EmptyNote("Bookmark a page using the star. Document bookmarks also appear here.") }
                    items(bookmarks.filter { it in 0 until state.pages }.sorted()) { index -> TextButton(onClick = { navigate(index) }, modifier = Modifier.fillMaxWidth()) { Text("Page ${index + 1}") } }
                    if (state.outline.isNotEmpty()) item { SectionTitle("Document outline") }
                    items(state.outline) { (title, index) -> TextButton(onClick = { navigate(index) }, modifier = Modifier.fillMaxWidth()) { Text("$title · ${index + 1}") } }
                }
                else -> {
                    item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(pageNumber, { pageNumber = it.filter(Char::isDigit).take(4) }, label = { Text("Page number") }, modifier = Modifier.weight(1f), singleLine = true)
                        TextButton(onClick = { pageNumber.toIntOrNull()?.let { navigate(it - 1) } }, enabled = (pageNumber.toIntOrNull() ?: 0) in 1..state.pages) { Text("Go") }
                    } }
                    items((0 until state.pages).toList()) { index -> Surface(onClick = { navigate(index) }, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            state.file?.let { PdfThumbnail(it, index) }; Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        }
                    } }
                }
            }
        }
    }
}
@Composable private fun PdfThumbnail(file: File, page: Int) {
    val bitmap by produceState<Bitmap?>(null, file, page) { value = withContext(Dispatchers.IO) {
        runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer -> renderer.openPage(page).use { p ->
            val scale = 180f / maxOf(p.width, p.height); val image = Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.WHITE); p.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); image
        } } } }.getOrNull()
    } }
    Box(Modifier.size(64.dp, 84.dp), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), "Page ${page + 1} thumbnail", Modifier.fillMaxSize()) } }
}
