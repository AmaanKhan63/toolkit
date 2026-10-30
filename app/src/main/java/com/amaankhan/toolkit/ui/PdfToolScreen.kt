package com.amaankhan.toolkit.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.documents.*

val implementedPdfTools = setOf("D02", "D03", "D12", "P01", "P02", "P03", "P14", "P16", "D05", "P04", "P09")

@Composable fun PdfToolScreen(toolId: String, onBack: () -> Unit) {
    val model: PdfToolViewModel = viewModel(key = "pdf-$toolId")
    val state by model.state.collectAsStateWithLifecycle()
    val title = documentTools.first { it.id == toolId }.name
    var imageFormat by rememberSaveable { mutableStateOf("PNG") }
    var imageEdge by rememberSaveable { mutableStateOf("1800 px") }
    var action by rememberSaveable(toolId) { mutableStateOf("Protect") }
    var allPages by rememberSaveable(toolId) { mutableStateOf(true) }
    var pages by rememberSaveable(toolId) { mutableStateOf("") }
    var rotation by rememberSaveable(toolId) { mutableStateOf("0°") }
    var crop by rememberSaveable(toolId) { mutableStateOf("32") }
    var textFormat by rememberSaveable { mutableStateOf("TXT") }
    var ranges by remember { mutableStateOf(emptyMap<String, String>()) }
    var linkedCrop by rememberSaveable { mutableStateOf(true) }
    var cropTop by rememberSaveable { mutableStateOf("32") }
    var cropRight by rememberSaveable { mutableStateOf("32") }
    var cropBottom by rememberSaveable { mutableStateOf("32") }
    var cropLeft by rememberSaveable { mutableStateOf("32") }
    var insertPages by rememberSaveable { mutableStateOf("") }
    var insertAfter by rememberSaveable { mutableStateOf("") }
    var allowPrint by rememberSaveable { mutableStateOf(true) }
    var allowModify by rememberSaveable { mutableStateOf(true) }
    var allowExtract by rememberSaveable { mutableStateOf(true) }
    val insertPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { if (state.inputs.size > 1) model.remove(1); model.select(listOf(uri), true, false) } }
    var imageMode by rememberSaveable { mutableStateOf("Render pages") }
    var splitMode by rememberSaveable { mutableStateOf("One PDF") }
    var splitEvery by rememberSaveable { mutableStateOf("1") }
    var splitGroups by rememberSaveable { mutableStateOf("") }
    var compression by rememberSaveable { mutableStateOf("Balanced · 1800 px") }
    var markType by rememberSaveable { mutableStateOf(if (toolId == "P09") "Image" else "Page numbers") }
    var markText by rememberSaveable { mutableStateOf("{page} / {total}") }
    var markSize by rememberSaveable { mutableStateOf("24") }
    var markX by rememberSaveable { mutableFloatStateOf(50f) }
    var markY by rememberSaveable { mutableFloatStateOf(if (toolId == "P04") 95f else 75f) }
    var markWidth by rememberSaveable { mutableFloatStateOf(30f) }
    var opacity by rememberSaveable { mutableFloatStateOf(if (toolId == "P09") 1f else .5f) }
    var asset by remember { mutableStateOf("") }
    var signaturePad by remember { mutableStateOf(false) }
    val metadata by model.metadata.collectAsStateWithLifecycle()
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) asset = uri.toString() }
    DisposableEffect(asset) { val old = asset; onDispose { if (old.startsWith("file:")) Uri.parse(old).path?.let { java.io.File(it).delete() } } }
    if (signaturePad) SignaturePad({ signaturePad = false }) { asset = it; signaturePad = false }
    // Passwords intentionally never enter saveable state, preferences, history or logs.
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var permissionsExpanded by rememberSaveable { mutableStateOf(false) }
    var enterPassword by remember(state.inputs) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> model.select(uris, true) }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.select(listOf(it), false) } }
    fun chooseInput() { if (toolId == "D02") picker.launch(arrayOf("application/pdf")) else singlePicker.launch(arrayOf("application/pdf")) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.result?.mime ?: "application/pdf"), model::save)
    val saveFolder = rememberLauncherForActivityResult(DestinationFolderContract(), model::saveImagesToFolder)
    val back = { if (!state.busy) { password = ""; newPassword = ""; confirmation = ""; if (state.result != null) model.editResult() else onBack() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(if (state.result != null) "Preview result" else title, back, !state.busy) }, bottomBar = {
        if (state.inputs.isNotEmpty() || state.busy || state.error != null || state.saved != null) ActionBar {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            when {
                state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress, style = MaterialTheme.typography.bodySmall); TextButton(onClick = model::cancel) { Text("Cancel") } }
                state.saved != null -> PrimaryAction("Start again", onClick = model::reset)
                state.result != null -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.result!!.pages} pages · ${formatBytes(state.result!!.file.length())}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = model::editResult) { Text("Back to edit") }
                    }
                    if (toolId == "D03" && action == "Protect") Text("Content preview · saved PDF requires your new password", style = MaterialTheme.typography.bodySmall)
                    PrimaryAction(if (toolId == "D12") "Save images as ZIP" else "Save as") { save.launch("Toolkit-${toolId}-${System.currentTimeMillis()}.${when (state.result?.mime) { "text/plain" -> "txt"; "text/markdown" -> "md"; "application/zip" -> "zip"; else -> "pdf" }}") }
                    if (state.result!!.images.isNotEmpty()) TextButton(onClick = { saveFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Save images to a folder") }
                }
                state.inputs.isEmpty() -> PrimaryAction("Choose PDF${if (toolId == "D02") "s" else ""}") { chooseInput() }
                else -> PrimaryAction("Preview result") {
                    when {
                        toolId == "D03" && action == "Protect" && newPassword.isBlank() -> model.error("Enter a new password.")
                        toolId == "D03" && action == "Protect" && newPassword != confirmation -> model.error("The new passwords do not match.")
                        toolId == "D03" && action == "Unlock" && state.passwordRequired && password.isEmpty() -> model.error("Enter the current password.")
                        !allPages && pages.isBlank() -> model.error("Choose the pages to include.")
                        toolId == "P03" && (crop.toIntOrNull() ?: -1) !in 0..160 -> model.error("Enter a crop between 0 and 160 px.")
                        toolId in setOf("D05", "P04", "P09") || (toolId == "D12" && imageMode == "Embedded images") || (toolId == "P01" && splitMode != "One PDF") -> {
                            val mode = when (toolId) { "D05" -> "COMPRESS"; "P04" -> "STAMP"; "P09" -> "SIGN"; "D12" -> "EMBEDDED"; else -> "SPLIT" }
                            if (toolId == "P09" && asset.isBlank()) model.error("Draw or choose a signature first.")
                            else if (toolId == "P04" && markType == "Image" && asset.isBlank()) model.error("Choose a watermark image.")
                            else {
                                val options = org.json.JSONObject().put("pages", if (allPages) "" else pages)
                                    .put("edge", when (compression) { "Lossless cleanup" -> 0; "Smallest · 800 px" -> 800; "High quality · 2400 px" -> 2400; else -> 1800 })
                                    .put("quality", if (compression.startsWith("Smallest")) 55 else 85)
                                    .put("text", markText).put("size", markSize.toIntOrNull() ?: 24).put("x", markX).put("y", markY).put("width", markWidth).put("opacity", opacity)
                                    .put("mode", if (splitMode == "Custom ranges") "Ranges" else "Each page").put("groups", splitGroups).put("every", splitEvery.toIntOrNull() ?: 1)
                                model.runExtra(ExtraPdfRequest(mode, state.inputs.single().uri, password, options, if (markType == "Image" || toolId == "P09") asset else ""))
                                password = ""
                            }
                        }
                        else -> {
                            val operation = when (toolId) {
                                "D02" -> PdfOperation.MERGE
                                "D03" -> if (action == "Protect") PdfOperation.PROTECT else PdfOperation.UNLOCK
                                "P01" -> PdfOperation.EXTRACT
                                "P02" -> PdfOperation.ORGANIZE
                                "P03" -> PdfOperation.CROP
                                "P14" -> PdfOperation.TEXT
                                "D12" -> PdfOperation.IMAGES
                                else -> PdfOperation.REMOVE_METADATA
                            }
                            model.run(PdfRequest(operation, state.inputs.map { it.uri }, pages, allPages, password, newPassword, rotation.removeSuffix("°").toInt(), crop.toIntOrNull() ?: 0, imageFormat, imageEdge.removeSuffix(" px").toInt(), textFormat = textFormat,
                                sourcePages = state.inputs.map { ranges[it.uri].orEmpty() },
                                cropEdges = if (linkedCrop) null else MarginsPx(top = cropTop.toIntOrNull() ?: -1, right = cropRight.toIntOrNull() ?: -1, bottom = cropBottom.toIntOrNull() ?: -1, left = cropLeft.toIntOrNull() ?: -1),
                                insertPages = insertPages, insertAfter = insertAfter.toIntOrNull() ?: -1,
                                allowPrint = allowPrint, allowModify = allowModify, allowExtract = allowExtract))
                            password = ""; newPassword = ""; confirmation = ""
                        }
                    }
                }
            }
        }
    }) { padding ->
        when {
            state.result != null -> if (state.result!!.mime == "application/pdf") PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
                else if (state.result!!.images.isNotEmpty()) ImageResultPreview(state.result!!, Modifier.padding(padding).fillMaxSize())
                else if (state.result!!.preview.extension == "pdf") Column(Modifier.padding(padding).fillMaxSize()) { EmptyNote("First split document. Save the ZIP for all parts."); PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.weight(1f)) }
                else TextPreview(Uri.fromFile(state.result!!.file).toString(), Modifier.padding(padding).fillMaxSize())
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.saved != null) {
                    item { InfoCard("File saved", state.saved!!.name) }
                    item { DocumentActions(state.saved!!) }
                } else {
                    if (state.inputs.isEmpty()) item { FileStartPanel("Start with ${if (toolId == "D02") "your PDFs" else "a PDF"}", "${title}. Choose a document to get started, then review your changes before saving.", "PDF documents", if (toolId == "D02") "Choose PDFs" else "Choose PDF", if (toolId == "D03") Glyph.LOCK else Glyph.FILE, !state.busy, ::chooseInput) }
                    itemsIndexed(state.inputs, key = { _, input -> input.uri }) { index, input -> Surface(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { GlyphIcon(Glyph.FILE); Text(input.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall); IconButton(onClick = { model.remove(index) }, enabled = !state.busy) { GlyphIcon(Glyph.CLOSE, "Remove ${input.name}") } }
                            if (toolId == "D02") ToolkitTextField(ranges[input.uri].orEmpty(), { ranges = ranges + (input.uri to it) }, label = { Text("Pages from this PDF · blank for all") }, placeholder = { Text("1, 3-5") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                            if (toolId == "D02") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { model.move(index, -1) }, enabled = !state.busy && index > 0) { GlyphIcon(Glyph.UP, "Move file ${index + 1} up") }
                                IconButton(onClick = { model.move(index, 1) }, enabled = !state.busy && index < state.inputs.lastIndex) { GlyphIcon(Glyph.DOWN, "Move file ${index + 1} down") }
                            }
                        }
                    } }
                    if (state.inputs.isNotEmpty()) {
                        item { OutlinedButton(onClick = { chooseInput() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (toolId == "D02") "Add PDFs" else "Replace file") } }
                        if (toolId == "D03") {
                            item { ChoiceField("What would you like to do?", if (action == "Protect") "Protect with a password" else "Remove password", listOf("Protect with a password", "Remove password"), !state.busy) { action = if (it == "Remove password") "Unlock" else "Protect"; password = ""; newPassword = ""; confirmation = "" } }
                            if (state.passwordRequired) item {
                                InfoCard("This PDF is already protected", if (action == "Protect") "Enter its current password to open it, then set the new password below." else "Enter the password that opens this PDF. We’ll save an unlocked copy.")
                                Spacer(Modifier.height(16.dp))
                                PasswordField("Current PDF password", password, !state.busy) { password = it }
                            }
                            if (action == "Protect") {
                                item { PasswordField("New password", newPassword, !state.busy) { newPassword = it } }
                                item { PasswordField("Confirm password", confirmation, !state.busy) { confirmation = it } }
                                item { ExpandableOptions("Reader permissions", "Printing, editing and copying", permissionsExpanded, { permissionsExpanded = !permissionsExpanded }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowPrint, { allowPrint = it }, enabled = !state.busy); Text("Allow printing") }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowModify, { allowModify = it }, enabled = !state.busy); Text("Allow editing") }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowExtract, { allowExtract = it }, enabled = !state.busy); Text("Allow copying text") }
                                    EmptyNote("These permissions depend on the PDF reader. Your password encrypts the file.")
                                } }
                            } else if (!state.passwordRequired) item { EmptyNote("This PDF opens without a password. You can still save a copy with any reader restrictions removed.") }
                        }
                        if (toolId in setOf("P01", "P02", "P03", "P14", "D12", "D05", "P04", "P09")) {
                            item { ChoiceField("Pages", if (allPages) "All pages" else "Selected pages", listOf("All pages", "Selected pages"), !state.busy) { allPages = it == "All pages" } }
                            if (!allPages) item { ToolkitTextField(pages, { pages = it }, label = { Text(if (toolId == "P02") "Page order" else "Page selection") }, placeholder = { Text(if (toolId == "P02") "3, 1, 2, 2" else "1, 3-5") }, supportingText = { Text(if (toolId == "P02") "Repeat pages to duplicate; omit pages to remove." else "Enter page numbers and ranges.${state.pageCount?.let { " $it pages available." }.orEmpty()}") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) }
                        }
                        if (toolId == "P14") item { ChoiceField("Text output", textFormat, listOf("TXT", "Markdown"), !state.busy) { textFormat = it }; EmptyNote("Markdown preserves text with page headings; complex PDF formatting is not reconstructed.") }
                        if (toolId == "P02") {
                            item { OutlinedButton(onClick = { insertPicker.launch(arrayOf("application/pdf")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.inputs.size > 1) "Replace inserted PDF" else "Insert pages from another PDF") } }
                            if (state.inputs.size > 1) {
                                item { ToolkitTextField(insertPages, { insertPages = it }, label = { Text("Inserted pages · blank for all") }, modifier = Modifier.fillMaxWidth()) }
                                item { ToolkitTextField(insertAfter, { insertAfter = it.filter(Char::isDigit).take(4) }, label = { Text("Insert after output page · blank for end") }, supportingText = { Text("0 inserts before the first page.") }, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                        if (toolId == "D12") {
                            item { ChoiceField("Export", imageMode, listOf("Render pages", "Embedded images"), !state.busy) { imageMode = it } }
                            item { ChoiceField("Format", imageFormat, listOf("PNG", "JPEG"), !state.busy) { imageFormat = it } }
                            item { ChoiceField("Longest image edge", imageEdge, listOf("1200 px", "1800 px", "2400 px"), !state.busy) { imageEdge = it } }
                            item { EmptyNote("Rendered pages are saved together in a ZIP. Up to 100 pages per export.") }
                        }
                        if (toolId == "P01") {
                            item { ChoiceField("Output", splitMode, listOf("One PDF", "Every N pages", "Custom ranges"), !state.busy) { splitMode = it } }
                            if (splitMode == "Every N pages") item { ToolkitTextField(splitEvery, { splitEvery = it.filter(Char::isDigit).take(4) }, label = { Text("Pages per document") }, modifier = Modifier.fillMaxWidth()) }
                            if (splitMode == "Custom ranges") item { ToolkitTextField(splitGroups, { splitGroups = it }, label = { Text("Groups separated by semicolons") }, placeholder = { Text("1-3; 4,6; 7-10") }, supportingText = { Text("Each group creates a separate PDF in a ZIP.") }, modifier = Modifier.fillMaxWidth()) }
                        }
                        if (toolId == "D05") {
                            item { ChoiceField("Compression", compression, listOf("Lossless cleanup", "High quality · 2400 px", "Balanced · 1800 px", "Smallest · 800 px"), !state.busy) { compression = it } }
                            item { EmptyNote("Text stays searchable. Image modes reduce image detail; already optimized files may not get smaller. Review the actual output size before saving.") }
                        }
                        if (toolId == "P04" || toolId == "P09") {
                            if (toolId == "P04") item { ChoiceField("Mark", markType, listOf("Page numbers", "Text", "Image"), !state.busy) { markType = it; if (it == "Page numbers") { markText = "{page} / {total}"; markY = 95f; opacity = 1f } else if (it == "Text") { markText = "CONFIDENTIAL"; markY = 50f; opacity = .35f } } }
                            if (markType != "Image" && toolId != "P09") {
                                item { ToolkitTextField(markText, { markText = it.take(160) }, label = { Text("Text · {page} and {total} supported") }, modifier = Modifier.fillMaxWidth()) }
                                item { ToolkitTextField(markSize, { markSize = it.filter(Char::isDigit).take(2) }, label = { Text("Text size (8?96 pt)") }, modifier = Modifier.fillMaxWidth()) }
                            } else {
                                if (toolId == "P09") item { PrimaryAction("Draw signature", !state.busy) { signaturePad = true } }
                                item { OutlinedButton(onClick = { assetPicker.launch(arrayOf("image/png", "image/jpeg")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (asset.isBlank()) "Choose an image" else "Image selected · Replace") } }
                                item { Text("Width · ${markWidth.toInt()}%"); Slider(markWidth, { markWidth = it }, valueRange = 5f..90f, enabled = !state.busy) }
                            }
                            item { Text("Horizontal position · ${markX.toInt()}%"); Slider(markX, { markX = it }, valueRange = 0f..100f, enabled = !state.busy) }
                            item { Text("Vertical position · ${markY.toInt()}% from top"); Slider(markY, { markY = it }, valueRange = 0f..100f, enabled = !state.busy) }
                            item { Text("Opacity · ${(opacity * 100).toInt()}%"); Slider(opacity, { opacity = it }, valueRange = .05f..1f, enabled = !state.busy) }
                            if (toolId == "P09") item { EmptyNote("Places a visible signature mark. Review its position on the generated page before saving.") }
                        }
                        if (toolId == "P02") item { ChoiceField("Rotate output pages", rotation, listOf("0°", "90°", "180°", "270°"), !state.busy) { rotation = it } }
                        if (toolId == "P03") {
                            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(linkedCrop, { linkedCrop = it }, enabled = !state.busy); Text("Use the same crop on every edge") } }
                            if (!linkedCrop) item {
                                listOf("Top" to cropTop, "Right" to cropRight, "Bottom" to cropBottom, "Left" to cropLeft).forEach { (edge, value) ->
                                    ToolkitTextField(value, { next -> val filtered = next.filter(Char::isDigit).take(3); when (edge) { "Top" -> cropTop = filtered; "Right" -> cropRight = filtered; "Bottom" -> cropBottom = filtered; else -> cropLeft = filtered } }, label = { Text("$edge crop (px)") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (linkedCrop) item { ToolkitTextField(crop, { crop = it.take(4) }, label = { Text("Crop each edge") }, suffix = { Text("px") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !state.busy) }
                            item { EmptyNote("96 px = 1 inch. Cropping changes the visible page area; it does not delete hidden content.") }
                        }
                        if (toolId == "P16") item { OutlinedButton(onClick = { model.inspectMetadata(password) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Inspect metadata") }; if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall) }
                        if (toolId == "P16") item { InfoCard("Remove PDF metadata", "Clears the document information fields and XMP metadata. Content, annotations and embedded attachments are unchanged.") }
                        if (toolId != "D03") {
                            if (state.passwordRequired || enterPassword) item { PasswordField("Current PDF password", password, !state.busy) { password = it } }
                            else item { TextButton(onClick = { enterPassword = true }) { Text("File needs a password?") } }
                        }
                        item { EmptyNote("Processing stays on your device. Originals stay unchanged.") }
                    }
                }
            }
        }
    }
}
@Composable fun PasswordField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    ToolkitTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } })
}
