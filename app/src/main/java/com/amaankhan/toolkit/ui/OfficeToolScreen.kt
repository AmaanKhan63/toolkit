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
import com.amaankhan.toolkit.documents.*

val implementedOfficeTools = setOf("D04", "D07", "P17")

@Composable fun OfficeToolScreen(toolId: String, onBack: () -> Unit) {
    val model: PdfToolViewModel = viewModel(key = "office-$toolId")
    val state by model.state.collectAsStateWithLifecycle()
    var protection by rememberSaveable { mutableStateOf("File-open password") }
    var action by rememberSaveable { mutableStateOf("Lock") }
    var sheets by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    var selection by rememberSaveable { mutableStateOf("All pages") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val toPdf = toolId == "D07"
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { model.select(listOf(it), false, false) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.result?.mime ?: if (toPdf) "application/pdf" else XLSX_MIME), model::save)
    val back = { if (!state.busy) { currentPassword = ""; newPassword = ""; confirmation = ""; if (state.result != null) model.editResult() else onBack() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(if (state.result != null) "Preview result" else if (toPdf) "Excel to PDF" else "Excel protection", back, !state.busy) }, bottomBar = {
        ActionBar {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                state.busy -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress); TextButton(onClick = model::cancel) { Text("Cancel") } }
                state.saved != null -> PrimaryAction("Start again", onClick = model::reset)
                state.result != null -> {
                    Text(if (toPdf) "${state.result!!.pages} PDF pages · ${formatBytes(state.result!!.file.length())}" else "Workbook content preview · Save keeps its Excel format", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = model::editResult) { Text("Back to edit") }
                    PrimaryAction(if (toPdf) "Save PDF" else "Save workbook") { save.launch("Toolkit-${System.currentTimeMillis()}.${state.result!!.file.extension}") }
                }
                state.inputs.isEmpty() -> PrimaryAction("Choose Excel file") { picker.launch(arrayOf(XLSX_MIME, XLS_MIME, "application/octet-stream")) }
                else -> PrimaryAction("Preview result") {
                    when {
                        !toPdf && action == "Lock" && newPassword.isEmpty() -> model.error("Enter a new password.")
                        !toPdf && action == "Lock" && newPassword != confirmation -> model.error("The new passwords do not match.")
                        toPdf && selection == "Selected pages" && pages.isBlank() -> model.error("Enter the pages to include.")
                        else -> {
                            val operation = if (toPdf) OfficeAction.TO_PDF else if (protection == "File-open password") {
                                if (action == "Lock") OfficeAction.LOCK_FILE else OfficeAction.UNLOCK_FILE
                            } else if (action == "Lock") OfficeAction.LOCK_EDITING else OfficeAction.UNLOCK_EDITING
                            model.runOffice(OfficeRequest(state.inputs.single().uri, operation, currentPassword, newPassword, sheets, pages, selection == "All pages"))
                            currentPassword = ""; newPassword = ""; confirmation = ""
                        }
                    }
                }
            }
        }
    }) { padding ->
        if (state.result != null) PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.saved != null) {
                item { InfoCard("File saved", state.saved!!.name) }
                item { DocumentActions(state.saved!!) }
            } else {
                item { InfoCard(state.inputs.firstOrNull()?.name ?: "Choose an Excel workbook", if (toPdf) "Table layout uses cell values and cached formula results. Excel print settings, charts, drawings and merged-cell styling are not reproduced." else "File-open passwords encrypt the workbook. Sheet and workbook editing restrictions control changes without hiding the file’s contents.") }
                if (state.inputs.isNotEmpty()) {
                    item { OutlinedButton(onClick = { picker.launch(arrayOf(XLSX_MIME, XLS_MIME, "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Replace file") } }
                    if (!toPdf && state.inputs.firstOrNull()?.name?.endsWith(".xls", true) == true) item { EmptyNote("Legacy XLS uses older, weaker password protection. Use XLSX when strong file encryption is required.") }
                    if (!toPdf) {
                        item { ChoiceField("Protection", protection, listOf("File-open password", "Sheet & workbook editing"), !state.busy) { protection = it } }
                        item { ChoiceField("Action", action, listOf("Lock", "Unlock"), !state.busy) { action = it } }
                        if (action == "Lock") {
                            item { PasswordField("New password", newPassword, !state.busy) { newPassword = it } }
                            item { PasswordField("Confirm password", confirmation, !state.busy) { confirmation = it } }
                        }
                        if (protection == "Sheet & workbook editing") item { EmptyNote("Applies to all worksheets and workbook structure. If the source has a file-open password, the saved result removes that encryption; use File-open password → Lock to encrypt it again.") }
                    } else {
                        item { ToolkitTextField(sheets, { sheets = it.take(200) }, label = { Text("Worksheets") }, supportingText = { Text("Blank includes visible sheets. Use sheet positions, for example 1, 3–5.") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) }
                        item { ChoiceField("PDF pages", selection, listOf("All pages", "Selected pages"), !state.busy) { selection = it } }
                        if (selection == "Selected pages") item { ToolkitTextField(pages, { pages = it.take(200) }, label = { Text("Page range") }, supportingText = { Text("For example 1, 3–5. Page numbers refer to the generated table layout; invalid ranges are rejected.") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) }
                    }
                    item { PasswordField("Current file-open password (if encrypted)", currentPassword, !state.busy) { currentPassword = it } }
                    item { EmptyNote("Original workbook stays unchanged. Passwords are used for this run and are never saved.") }
                }
            }
        }
    }
}
