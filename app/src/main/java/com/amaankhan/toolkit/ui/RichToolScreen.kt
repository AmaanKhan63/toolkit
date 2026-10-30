package com.amaankhan.toolkit.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.documents.RichPdfRequest
import com.amaankhan.toolkit.documents.PdfOfficeRequest

val implementedRichTools = setOf("D06", "D08", "D09", "D10", "D11", "D13", "P15")
@Composable fun RichToolScreen(toolId: String, onBack: () -> Unit) {
    val model: PdfToolViewModel = viewModel(key = "rich-$toolId")
    val state by model.state.collectAsStateWithLifecycle()
    var allPages by rememberSaveable { mutableStateOf(true) }
    var pages by rememberSaveable { mutableStateOf("") }
    var sections by rememberSaveable { mutableStateOf("") }
    var margin by rememberSaveable { mutableStateOf("48") }
    var font by rememberSaveable { mutableStateOf("12") }
    var password by remember { mutableStateOf("") }
    val fromPdf = toolId in setOf("D09", "D10", "D11")
    val extension = when (toolId) { "D09" -> "docx"; "D10" -> "xlsx"; "D11" -> "pptx"; else -> "pdf" }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.select(listOf(it), false, false) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.result?.mime ?: "application/pdf"), model::save)
    val back = { if (!state.busy) { if (state.result != null) model.editResult() else onBack() } }
    BackHandler(onBack = back)
    fun choose() { picker.launch(when (toolId) {
        "D09", "D10", "D11" -> arrayOf("application/pdf")
        "D06" -> arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")
        "D08" -> arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.ms-powerpoint")
        "D13" -> arrayOf("text/html", "application/xhtml+xml")
        else -> arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")
    }) }
    Scaffold(topBar = { ScreenHeader(if (state.result == null) documentTools.first { it.id == toolId }.name else "Preview result", back, !state.busy) }, bottomBar = { if (state.inputs.isNotEmpty() || state.busy || state.error != null || state.saved != null) ActionBar {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        when {
            state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress); TextButton(onClick = model::cancel) { Text("Cancel") } }
            state.result != null -> { Text("${state.result!!.pages} pages · ${formatBytes(state.result!!.file.length())}", style = MaterialTheme.typography.bodySmall); PrimaryAction("Save as") { save.launch("Toolkit-${System.currentTimeMillis()}.$extension") }; TextButton(onClick = model::editResult) { Text("Back to edit") } }
            state.saved != null -> PrimaryAction("Start again", onClick = model::reset)
            state.inputs.isEmpty() -> PrimaryAction("Choose document", onClick = ::choose)
            else -> PrimaryAction("Preview result") {
                if (!allPages && pages.isBlank()) model.error("Enter the output pages to include.")
                else if (fromPdf) { model.runPdfOffice(PdfOfficeRequest(state.inputs.single().uri, extension, if (allPages) "" else pages, password)); password = "" }
                else model.runRich(RichPdfRequest(state.inputs.single().uri, if (allPages) "" else pages, sections, margin.toIntOrNull() ?: -1, font.toIntOrNull() ?: -1))
            }
        }
    } }) { padding ->
        if (state.result != null) PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.saved != null) { item { InfoCard("File saved", state.saved!!.name); DocumentActions(state.saved!!) } }
            else {
                if (state.inputs.isEmpty()) item { FileStartPanel("Choose your document", "${documentTools.first { it.id == toolId }.name}. Review the converted pages before saving a copy.", when (toolId) { "D06" -> "Word · DOC & DOCX"; "D08" -> "PowerPoint · PPT & PPTX"; "D13" -> "Local HTML files"; "P15" -> "Text & Markdown"; else -> "PDF documents" }, "Choose document", enabled = !state.busy, onChoose = ::choose) }
                if (state.inputs.isNotEmpty()) {
                    item { InfoCard(if (fromPdf) "Convert on your device" else "Create a local PDF", when (toolId) {
                    "D09" -> "Reconstructs editable text from the PDF’s text layer. Fonts, columns and page layout may differ. Scanned text needs OCR and is not converted."
                    "D10" -> "Infers cells from text positions, with one sheet per selected page. Review the resulting table: complex or borderless tables may need corrections. Original formulas cannot be recovered."
                    "D11" -> "Creates one image per selected PDF page, placed on a slide without cropping. Slide content is visual, not editable text."
                    "D06" -> "Creates a readable layout with searchable text, tables and supported images. Complex Word positioning and fields may differ. Review the generated pages before saving."
                    "D08" -> "Keeps slide size, positioned text, images and basic shapes. Charts, SmartArt, groups and effects may differ. Review the generated slides before saving."
                    else -> "Text stays searchable. Scripts and external resources are disabled. Review the generated pages before saving."
                    }) }
                }
                if (state.inputs.isNotEmpty()) {
                    item { Text(state.inputs.single().name, style = MaterialTheme.typography.titleMedium); OutlinedButton(onClick = ::choose, enabled = !state.busy) { Text("Replace document") } }
                    if (toolId == "D08") item { ToolkitTextField(sections, { sections = it }, label = { Text("Slides (blank = all)") }, placeholder = { Text("1, 3-5") }, modifier = Modifier.fillMaxWidth()) }
                    item { ChoiceField("Output pages", if (allPages) "All pages" else "Selected pages", listOf("All pages", "Selected pages"), !state.busy) { allPages = it == "All pages" } }
                    if (!allPages) item { ToolkitTextField(pages, { pages = it }, label = { Text("Output page selection") }, placeholder = { Text("1, 3-5") }, supportingText = { Text("Applied after laying out the document. Invalid page numbers are rejected.") }, modifier = Modifier.fillMaxWidth()) }
                    if (fromPdf) item { PasswordField("Current PDF password (if protected)", password, !state.busy) { password = it } }
                    else if (toolId != "D08") {
                        item { ToolkitTextField(margin, { margin = it.filter(Char::isDigit).take(3) }, label = { Text("Margins (0–160 px)") }, modifier = Modifier.fillMaxWidth()) }
                        item { ToolkitTextField(font, { font = it.filter(Char::isDigit).take(2) }, label = { Text("Body text size (8–28 pt)") }, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}
