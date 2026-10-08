package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaankhan.toolkit.RunPhase
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.documents.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun ImagesToPdfScreen(model: ToolkitViewModel, onBack: () -> Unit) {
    val state by model.composer.collectAsStateWithLifecycle()
    var organize by rememberSaveable { mutableStateOf(false) }
    var marginsValid by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::addImages)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), model::export)
    val back = { if (!state.busy) { if (organize) organize = false else if (state.phase == RunPhase.READY) model.options(state.options) else onBack() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(if (organize) "Arrange images" else if (state.phase == RunPhase.READY) "Preview PDF" else "Images to PDF", back, !state.busy) }, bottomBar = {
        ActionBar {
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when {
                organize -> PrimaryAction("Done") { organize = false }
                state.phase == RunPhase.CREATING -> {
                    Text("Preparing page ${state.progress} of ${state.images.size}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.progress.toFloat() / state.images.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = model::cancelCreation, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (state.phase == RunPhase.SAVING) "Saving PDF…" else "Reading images…") }
                state.phase == RunPhase.READY -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.images.size} pages · ${formatBytes(state.prepared?.length() ?: 0)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { model.options(state.options) }) { Text("Back to edit") }
                    }
                    PrimaryAction("Save as") { save.launch("Toolkit-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.pdf") }
                }
                state.phase == RunPhase.SAVED -> PrimaryAction("Create another PDF") { model.reset() }
                else -> PrimaryAction(if (state.images.isEmpty()) "Choose images" else "Preview PDF", enabled = marginsValid) {
                    if (state.images.isEmpty()) picker.launch(arrayOf("image/jpeg", "image/png")) else model.createPdf()
                }
            }
        }
    }) { padding ->
        when {
            organize -> LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.images, key = { _, image -> image.uri }) { index, image -> Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Thumbnail(image.uri, Modifier.fillMaxWidth().height(100.dp))
                        Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        Text(image.name, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        ChoiceField("Position", "${index + 1}", state.images.indices.map { "${it + 1}" }) { next -> model.moveImage(index, next.toInt() - index - 1) }
                        TextButton(onClick = { model.removeImage(image.uri) }, modifier = Modifier.fillMaxWidth()) { GlyphIcon(Glyph.TRASH); Spacer(Modifier.width(8.dp)); Text("Remove") }
                    }
                } }
            }
            state.phase == RunPhase.READY && state.prepared != null -> PdfPreview(Uri.fromFile(state.prepared).toString(), Modifier.padding(padding).fillMaxSize())
            state.phase == RunPhase.SAVED -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCard("PDF saved", state.saved?.name ?: "Find it in the folder you selected.")
                state.saved?.let { DocumentActions(it) }
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.images.isEmpty()) item { InfoCard("Start with your images", "Choose photos or scans, arrange their order, and review your PDF before saving.") }
                else {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${state.images.size} images", style = MaterialTheme.typography.titleMedium); Text("One image per page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { organize = true }, enabled = !state.busy) { Text("Manage") }
                    } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0 until minOf(state.images.size, 6)).toList().chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { index -> Surface(onClick = { organize = true }, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                    Box(Modifier.height(92.dp), contentAlignment = Alignment.Center) {
                                        if (index == 5 && state.images.size > 6) Text("+${state.images.size - 5}", style = MaterialTheme.typography.titleLarge)
                                        else { Thumbnail(state.images[index].uri, Modifier.fillMaxSize()); Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface) { Text("${index + 1}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) } }
                                    }
                                } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            } }
                        }
                    }
                    item { OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = !state.busy && state.images.size < PdfLayout.MAX_IMAGES, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { GlyphIcon(Glyph.PLUS); Spacer(Modifier.width(8.dp)); Text("Add images") } }
                    item { SectionTitle("Page layout") }
                    item { ChoiceField("Page size", state.options.paper.label, Paper.entries.map { it.label }, !state.busy) { label -> model.options(state.options.copy(paper = Paper.entries.first { it.label == label })) } }
                    item { ChoiceField("Orientation", state.options.orientation.label, Orientation.entries.map { it.label }, !state.busy) { label -> model.options(state.options.copy(orientation = Orientation.entries.first { it.label == label })) } }
                    item { MarginEditor(state.options.marginsPx ?: MarginsPx(), !state.busy, { marginsValid = it }) { model.options(state.options.copy(marginsPx = it)) } }
                }
                item { EmptyNote("PNG & JPEG · Up to ${PdfLayout.MAX_IMAGES} images. Originals stay unchanged. Images fit without cropping.") }
            }
        }
    }
}

@Composable private fun MarginEditor(initial: MarginsPx, enabled: Boolean, onValidity: (Boolean) -> Unit, onChange: (MarginsPx) -> Unit) {
    var linked by rememberSaveable { mutableStateOf(initial.top == initial.right && initial.top == initial.bottom && initial.top == initial.left) }
    var top by rememberSaveable { mutableStateOf(initial.top.toString()) }
    var right by rememberSaveable { mutableStateOf(initial.right.toString()) }
    var bottom by rememberSaveable { mutableStateOf(initial.bottom.toString()) }
    var left by rememberSaveable { mutableStateOf(initial.left.toString()) }
    fun publish() {
        val numbers = listOf(top, right, bottom, left).map { it.toIntOrNull() }
        val valid = numbers.all { it != null && it in 0..160 }
        onValidity(valid)
        if (valid) onChange(MarginsPx(numbers[0]!!, numbers[1]!!, numbers[2]!!, numbers[3]!!))
    }
    LaunchedEffect(top, right, bottom, left) { onValidity(listOf(top, right, bottom, left).all { (it.toIntOrNull() ?: -1) in 0..160 }) }
    Surface(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Margins", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("Link sides", style = MaterialTheme.typography.bodySmall); Switch(linked, { linked = it; if (it) { right = top; bottom = top; left = top; publish() } }, enabled = enabled) }
            @Composable fun Field(label: String, value: String, modifier: Modifier = Modifier, update: (String) -> Unit) {
                OutlinedTextField(value, { update(it.take(4)); publish() }, label = { Text(label) }, suffix = { Text("px") }, enabled = enabled,
                    isError = (value.toIntOrNull() ?: -1) !in 0..160, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = modifier)
            }
            if (linked) Field("All sides", top, Modifier.fillMaxWidth()) { top = it; right = it; bottom = it; left = it }
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Field("Top", top, Modifier.weight(1f)) { top = it }; Field("Right", right, Modifier.weight(1f)) { right = it } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Field("Bottom", bottom, Modifier.weight(1f)) { bottom = it }; Field("Left", left, Modifier.weight(1f)) { left = it } }
            }
            Text("0–160 px per side · 96 px = 1 inch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun Thumbnail(uri: String, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { runCatching { ImagePdfEngine.decodeImage(resolver, Uri.parse(uri), 240) }.getOrNull() } }
    Box(modifier.clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: GlyphIcon(Glyph.IMAGE)
    }
}
