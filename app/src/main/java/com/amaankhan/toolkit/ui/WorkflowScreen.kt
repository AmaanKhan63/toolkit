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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.WorkflowEditorModel
import com.amaankhan.toolkit.documents.*

@Composable fun WorkflowScreen(onBack: () -> Unit) {
    val editor: WorkflowEditorModel = viewModel()
    val runner: PdfToolViewModel = viewModel(key = "workflow-run")
    val template by editor.template.collectAsStateWithLifecycle()
    val saved by editor.saved.collectAsStateWithLifecycle()
    val notice by editor.notice.collectAsStateWithLifecycle()
    val state by runner.state.collectAsStateWithLifecycle()
    var secrets by remember { mutableStateOf(emptyMap<String, StepPasswords>()) }
    var confirmations by remember { mutableStateOf(emptyMap<String, String>()) }
    var batch by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { runner.select(listOf(it), false, false) } }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { runner.select(it, false, template.steps.firstOrNull()?.operation?.input == WorkflowType.PDF) }
    val resultMime = state.result?.mime ?: "application/pdf"
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(resultMime), runner::save)
    val invalid = runCatching { template.validate() }.exceptionOrNull()?.message
    val compatible = WorkflowOperation.entries.filter { op ->
        val prior = template.steps.lastOrNull()?.operation
        prior == null || (!prior.terminal && prior.output == op.input && op != WorkflowOperation.PDF_MERGE)
    }
    fun resetRun() { runner.reset(); secrets = emptyMap(); confirmations = emptyMap() }
    fun choose() {
        if (template.steps.firstOrNull()?.operation == WorkflowOperation.IMAGES_PDF) multiple.launch(arrayOf("image/png", "image/jpeg"))
        else if (template.steps.firstOrNull()?.operation == WorkflowOperation.PDF_MERGE) multiple.launch(arrayOf("application/pdf"))
        else {
        val types = when (template.steps.firstOrNull()?.operation?.input) {
            WorkflowType.EXCEL -> arrayOf(XLSX_MIME, "application/vnd.ms-excel", "application/octet-stream")
            WorkflowType.WORD -> arrayOf(DOCX_MIME, "application/msword")
            WorkflowType.PRESENTATION -> arrayOf(PPTX_MIME, "application/vnd.ms-powerpoint")
            WorkflowType.TEXT -> arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")
            WorkflowType.HTML -> arrayOf("text/html", "application/xhtml+xml")
            else -> arrayOf("application/pdf")
        }
        if (batch) multiple.launch(types) else picker.launch(types)
        }
    }
    val back = { if (!state.busy) { secrets = emptyMap(); confirmations = emptyMap(); if (state.result != null) runner.editResult() else onBack() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(if (state.result == null) "Workflows" else "Preview result", back, !state.busy) }, bottomBar = {
        ActionBar {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.busy && state.result == null && state.saved == null) {
                TextButton(onClick = editor::save, enabled = invalid == null, modifier = Modifier.fillMaxWidth()) { Text("Save workflow") }
            }
            when {
                state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress); TextButton(onClick = runner::cancel) { Text("Cancel") } }
                state.saved != null -> PrimaryAction("Start another run") { resetRun() }
                state.result != null -> {
                    if (state.result!!.summary.isNotBlank()) Text(state.result!!.summary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = runner::editResult) { Text("Back to edit") }
                    PrimaryAction("Save result") { save.launch("Toolkit-workflow-${System.currentTimeMillis()}.${when(resultMime) { XLSX_MIME -> "xlsx"; XLS_MIME -> "xls"; DOCX_MIME -> "docx"; PPTX_MIME -> "pptx"; "text/plain" -> "txt"; "application/zip" -> "zip"; else -> "pdf" }}") }
                }
                state.inputs.isEmpty() -> PrimaryAction("Choose input", invalid == null) { choose() }
                else -> PrimaryAction("Run workflow", invalid == null) {
                    val mismatch = template.steps.any { it.operation.needsNewPassword && (secrets[it.id]?.new.isNullOrEmpty() || secrets[it.id]?.new != confirmations[it.id]) }
                    if (mismatch) runner.error("Enter and confirm the new password for each lock step.")
                    else { runner.runWorkflow(template, secrets); secrets = emptyMap(); confirmations = emptyMap() }
                }
            }
        }
    }) { padding ->
        if (state.result != null) {
            if (state.result!!.mime == "text/plain" || state.result!!.preview.extension in setOf("txt", "md")) TextPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
            else if (state.result!!.images.isNotEmpty()) ImageResultPreview(state.result!!, Modifier.padding(padding).fillMaxSize())
            else if (state.result!!.preview.extension in setOf("png", "jpg")) ImageResultPreview(state.result!!.copy(images = listOf(state.result!!.preview), sourcePages = listOf(1)), Modifier.padding(padding).fillMaxSize())
            else PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
        } else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.saved != null) {
                item { InfoCard("Result saved", state.saved!!.name) }
                item { DocumentActions(state.saved!!) }
            } else {
                item {
                    if (saved.isNotEmpty()) ChoiceField("Saved workflows", "Choose a workflow", saved.map { "${it.name} · ${it.id.take(6)}" }, !state.busy) { label ->
                        resetRun(); editor.select(saved.first { "${it.name} · ${it.id.take(6)}" == label })
                    }
                }
                item { ToolkitTextField(template.name, editor::rename, label = { Text("Workflow name") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
                if (template.steps.isEmpty()) item {
                    InfoCard("Choose your starting tool", "Start with any supported document or images. Each step receives the previous step’s output.")
                    TextButton(onClick = {
                        resetRun(); editor.select(WorkflowTemplate("Unlock Excel → PDF → lock", listOf(WorkflowStep(WorkflowOperation.EXCEL_UNLOCK), WorkflowStep(WorkflowOperation.EXCEL_PDF), WorkflowStep(WorkflowOperation.PDF_LOCK))))
                    }) { Text("Use Excel → protected PDF example") }
                }
                itemsIndexed(template.steps, key = { _, step -> step.id }) { index, step ->
                    Surface(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${index + 1}. ${step.operation.label}", style = MaterialTheme.typography.titleSmall)
                            Text("${step.operation.input.name} → ${step.operation.output.name}", style = MaterialTheme.typography.bodySmall)
                            if (step.operation == WorkflowOperation.IMAGES_PDF) {
                                ChoiceField("Page size", Paper.valueOf(step.paper).label, Paper.entries.map { it.label }, !state.busy) { label -> editor.change(step.copy(paper = Paper.entries.first { it.label == label }.name)) }
                                ChoiceField("Orientation", Orientation.valueOf(step.orientation).label, Orientation.entries.map { it.label }, !state.busy) { label -> editor.change(step.copy(orientation = Orientation.entries.first { it.label == label }.name)) }
                                ToolkitTextField(step.marginPx, { editor.change(step.copy(marginPx = it.take(3))) }, label = { Text("Margin on each side") }, suffix = { Text("px") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("0–160 px · 96 px per inch") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                            }
                            if (step.operation == WorkflowOperation.EXCEL_PDF) {
                                EmptyNote("Table layout with cached values. Excel charts, print layout and styling are not reproduced.")
                                ToolkitTextField(step.sheets, { editor.change(step.copy(sheets = it.take(200))) }, label = { Text("Sheets · blank for visible sheets") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                            }
                            if (step.operation.input == WorkflowType.PDF || step.operation in setOf(WorkflowOperation.WORD_PDF, WorkflowOperation.PRESENTATION_PDF, WorkflowOperation.TEXT_PDF, WorkflowOperation.HTML_PDF)) {
                                ToolkitTextField(step.pages, { editor.change(step.copy(pages = it.take(200))) }, label = { Text("Pages · blank for all") }, placeholder = { Text("1, 3–5") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                            }
                            if (step.operation == WorkflowOperation.PDF_ROTATE) ChoiceField("Rotation", "${step.rotation}°", listOf("0°", "90°", "180°", "270°"), !state.busy) { editor.change(step.copy(rotation = it.removeSuffix("°").toInt())) }
                            if (step.operation == WorkflowOperation.PDF_CROP) ChoiceField("Crop per edge", "${step.cropPx} px", listOf("0 px", "8 px", "16 px", "24 px", "32 px", "48 px"), !state.busy) { editor.change(step.copy(cropPx = it.removeSuffix(" px").toInt())) }
                            if (step.operation == WorkflowOperation.PDF_IMAGES) {
                                ChoiceField("Format", step.imageFormat, listOf("PNG", "JPEG"), !state.busy) { editor.change(step.copy(imageFormat = it)) }
                                ChoiceField("Longest edge", "${step.imageEdge} px", listOf("1200 px", "1800 px", "2400 px"), !state.busy) { editor.change(step.copy(imageEdge = it.removeSuffix(" px").toInt())) }
                            }
                            if (step.operation == WorkflowOperation.PDF_COMPRESS) ChoiceField("Image longest edge", "${step.imageEdge} px", listOf("1200 px", "1800 px", "2400 px"), !state.busy) { editor.change(step.copy(imageEdge = it.removeSuffix(" px").toInt())) }
                            if (step.operation == WorkflowOperation.PDF_STAMP) {
                                ToolkitTextField(step.stampText, { editor.change(step.copy(stampText = it.take(160))) }, label = { Text("Text · {page} and {total} supported") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                                Text("Vertical position · ${step.stampY}%"); Slider(step.stampY.toFloat(), { editor.change(step.copy(stampY = it.toInt())) }, valueRange = 0f..100f, enabled = !state.busy)
                                Text("Opacity · ${step.stampOpacity}%"); Slider(step.stampOpacity.toFloat(), { editor.change(step.copy(stampOpacity = it.toInt())) }, valueRange = 5f..100f, enabled = !state.busy)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { editor.move(index, -1); resetRun() }, enabled = !state.busy && index > 0) { GlyphIcon(Glyph.UP, "Move step ${index + 1} up") }
                                IconButton(onClick = { editor.move(index, 1); resetRun() }, enabled = !state.busy && index < template.steps.lastIndex) { GlyphIcon(Glyph.DOWN, "Move step ${index + 1} down") }
                                IconButton(onClick = { editor.remove(step.id); resetRun() }, enabled = !state.busy) { GlyphIcon(Glyph.CLOSE, "Remove step ${index + 1}") }
                            }
                        }
                    }
                }
                if (compatible.isNotEmpty() && template.steps.size < 20) item { ChoiceField("Add step", "Choose a tool", compatible.map { it.label }, !state.busy) { label ->
                    editor.add(compatible.first { it.label == label }); resetRun()
                } }
                if (template.steps.isNotEmpty() && invalid != null) item { Text(invalid, color = MaterialTheme.colorScheme.error) }
                if (template.steps.firstOrNull()?.operation !in setOf(null, WorkflowOperation.IMAGES_PDF, WorkflowOperation.PDF_MERGE)) item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(batch, { batch = it; resetRun() }, enabled = !state.busy); Text("Run separately on multiple files") }
                    if (batch) EmptyNote("Each file runs through the same steps and passwords. Results are saved together in a ZIP.")
                }
                item { Row {
                    TextButton(onClick = { resetRun(); editor.select(WorkflowTemplate("My workflow", emptyList())) }, enabled = !state.busy) { Text("New") }
                    if (saved.any { it.id == template.id }) TextButton(onClick = { editor.delete(template.id) }, enabled = !state.busy) { Text("Delete saved") }
                } }
                notice?.let { item { EmptyNote(it) } }
                if (state.inputs.isNotEmpty()) {
                    item { InfoCard("Run input", state.inputs.joinToString("\n") { it.name }) }
                    item { OutlinedButton(onClick = { choose() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Replace input") } }
                    template.steps.forEachIndexed { index, step ->
                        if ((index == 0 && step.operation.input in setOf(WorkflowType.PDF, WorkflowType.EXCEL)) || step.operation.needsPassword) item {
                            val secret = secrets[step.id] ?: StepPasswords()
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Step ${index + 1} · ${step.operation.label}", style = MaterialTheme.typography.titleSmall)
                                PasswordField("Current password (if needed)", secret.current, !state.busy) { secrets = secrets + (step.id to secret.copy(current = it)) }
                                if (step.operation.needsNewPassword) {
                                    PasswordField("New password", secret.new, !state.busy) { secrets = secrets + (step.id to secret.copy(new = it)) }
                                    PasswordField("Confirm password", confirmations[step.id].orEmpty(), !state.busy) { confirmations = confirmations + (step.id to it) }
                                }
                            }
                        }
                    }
                    item { EmptyNote("Passwords apply only to this run. Saved workflows contain tools and options, never passwords or input files.") }
                }
            }
        }
    }
}
