package com.amaankhan.toolkit

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amaankhan.toolkit.data.ExportedDocument
import com.amaankhan.toolkit.documents.*
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InputDocument(val uri: String, val name: String)
data class PdfToolState(
    val inputs: List<InputDocument> = emptyList(), val pageCount: Int? = null,
    val busy: Boolean = false, val progress: String = "", val error: String? = null,
    val result: PdfResult? = null, val saved: ExportedDocument? = null,
)
class PdfToolViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ToolkitApplication
    private val resolver = app.contentResolver
    private val directory = File(app.cacheDir, "pdf-tools").apply { mkdirs() }
    private val engine = PdfToolEngine(app, directory)
    private val _state = MutableStateFlow(PdfToolState())
    val state = _state.asStateFlow()
    private var task: Job? = null
    fun select(uris: List<Uri>, append: Boolean, inspectPdf: Boolean = true) {
        if (_state.value.busy || uris.isEmpty()) return
        metadata.value = ""
        task = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, progress = "Reading files…") }
            try {
                val inputs = withContext(Dispatchers.IO) { uris.map { InputDocument(it.toString(), name(it)) } }
                val all = ((if (append) _state.value.inputs else emptyList()) + inputs).distinctBy { it.uri }
                require(all.size <= 20) { "Choose up to 20 files." }
                editResult()
                val count = if (inspectPdf && all.size == 1) try { engine.pageCount(all[0].uri) } catch (_: InvalidPasswordException) { null } else null
                _state.update { it.copy(inputs = all, pageCount = count, busy = false) }
            } catch (e: CancellationException) { _state.update { it.copy(busy = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(busy = false, error = e.message ?: "The file could not be read.") } }
        }
    }
    fun remove(index: Int) { if (!_state.value.busy) _state.update { it.copy(inputs = it.inputs.filterIndexed { i, _ -> i != index }, pageCount = null) } }
    fun move(index: Int, delta: Int) { if (!_state.value.busy) _state.update {
        val inputs = it.inputs.toMutableList(); val target = index + delta
        if (index in inputs.indices && target in inputs.indices) inputs.add(target, inputs.removeAt(index))
        it.copy(inputs = inputs)
    } }
    fun editResult() { _state.value.result?.delete(); _state.update { it.copy(result = null, saved = null, error = null) } }
    fun reset() { if (!_state.value.busy) { editResult(); _state.value = PdfToolState() } }
    fun error(message: String) { _state.update { it.copy(error = message) } }
    fun run(request: PdfRequest) {
        runOperation { progress -> engine.execute(request, progress) }
    }
    fun runOffice(request: OfficeRequest) {
        runOperation { progress -> OfficeEngine(app, directory).execute(request, progress) }
    }
    fun runExtra(request: ExtraPdfRequest) { runOperation { progress -> ExtraPdfEngine(app, directory).execute(request, progress) } }
    fun runRich(request: RichPdfRequest) { runOperation { progress -> RichPdfEngine(app, directory).execute(request, progress) } }
    fun runPdfOffice(request: PdfOfficeRequest) { runOperation { progress -> PdfOfficeEngine(app, directory).execute(request, progress) } }
    val metadata = MutableStateFlow("")
    fun inspectMetadata(password: String) {
        val source = _state.value.inputs.firstOrNull()?.uri ?: return
        viewModelScope.launch { try { val value = ExtraPdfEngine(app, directory).metadata(source, password); if (_state.value.inputs.firstOrNull()?.uri == source) metadata.value = value } catch (e: CancellationException) { throw e } catch (e: Exception) { error(e.message ?: "Unable to inspect metadata.") } }
    }
    fun runWorkflow(template: WorkflowTemplate, passwords: Map<String, StepPasswords>) {
        val inputs = _state.value.inputs.map { it.uri }
        runOperation { progress ->
            val engine = WorkflowEngine(app, directory)
            if (inputs.size > 1 && template.steps.firstOrNull()?.operation !in setOf(WorkflowOperation.PDF_MERGE, WorkflowOperation.IMAGES_PDF)) engine.executeBatch(template, inputs, passwords, progress)
            else engine.execute(template, inputs, passwords, progress)
        }
    }
    private fun runOperation(operation: suspend ((String) -> Unit) -> PdfResult) {
        if (_state.value.busy) return
        editResult()
        task = viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = "Preparing…", error = null) }
            try {
                val result = operation { progress -> _state.update { it.copy(progress = progress) } }
                _state.update { it.copy(result = result, busy = false) }
            } catch (e: CancellationException) { _state.update { it.copy(busy = false, error = "Cancelled. Your original files are unchanged.") }; throw e }
            catch (_: InvalidPasswordException) { _state.update { it.copy(busy = false, error = "The current password is missing or incorrect. Enter it and try again.") } }
            catch (_: OutOfMemoryError) { _state.update { it.copy(busy = false, error = "Not enough memory. Try a smaller document or fewer files.") } }
            catch (e: Exception) { _state.update { it.copy(busy = false, error = e.message ?: "This PDF could not be processed.") } }
        }
    }
    fun cancel() { task?.cancel() }
    fun saveImagesToFolder(uri: Uri?) {
        val result = _state.value.result ?: return
        if (uri == null || _state.value.busy || result.images.isEmpty()) return
        task = viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = "Saving images…", error = null) }
            val created = mutableListOf<androidx.documentfile.provider.DocumentFile>()
            var committed = false
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val exported = withContext(Dispatchers.IO) {
                    val tree = requireNotNull(androidx.documentfile.provider.DocumentFile.fromTreeUri(app, uri))
                    result.images.mapIndexed { index, image ->
                        currentCoroutineContext().ensureActive()
                        _state.update { it.copy(progress = "Saving image ${index + 1}/${result.images.size}") }
                        val mime = if (image.extension == "png") "image/png" else "image/jpeg"
                        val page = result.sourcePages.getOrElse(index) { index + 1 }
                        val name = "Toolkit-page-${page.toString().padStart(3, '0')}-${index + 1}.${image.extension}"
                        val document = tree.createFile(mime, name) ?: kotlin.error("Unable to create the image.")
                        created += document
                        resolver.openOutputStream(document.uri, "w")?.use { target -> image.inputStream().use { source ->
                            val buffer = ByteArray(65536)
                            while (true) { currentCoroutineContext().ensureActive(); val count = source.read(buffer); if (count < 0) break; target.write(buffer, 0, count) }
                        } } ?: kotlin.error("Unable to save the image.")
                        ExportedDocument(document.uri.toString(), document.name ?: name, 0, image.length(), System.currentTimeMillis(), mime)
                    }
                }
                committed = true
                withContext(NonCancellable) {
                    var warning: String? = null
                    try { exported.forEach { app.database.exports().insert(it) } } catch (_: Exception) { warning = "Images saved. Some history entries could not be recorded." }
                    result.delete()
                    _state.update { it.copy(busy = false, result = null, saved = exported.first().copy(name = "${exported.size} images saved"), error = warning) }
                }
            } catch (failure: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val removed = if (!committed) created.map { runCatching { it.delete() }.getOrDefault(false) }.all { it } else false
                    _state.update { it.copy(busy = false, error = if (committed) "Images saved. Check your selected folder." else if (removed) "Saving failed. New partial outputs were removed; choose a folder and retry." else "Saving failed. Some new files remain in the selected folder; remove those before retrying.") }
                }
                if (failure is CancellationException) throw failure
            }
        }
    }
    fun save(uri: Uri?) {
        val result = _state.value.result ?: return
        if (uri == null || _state.value.busy) return
        task = viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = "Saving…", error = null) }
            var committed = false
            try {
                val exported = withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.use { output -> result.file.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) { currentCoroutineContext().ensureActive(); val length = input.read(buffer); if (length == -1) break; output.write(buffer, 0, length) }
                        output.flush()
                    } } ?: kotlin.error("Destination is not writable.")
                    committed = true
                    ExportedDocument(uri.toString(), name(uri), result.pages, result.file.length(), System.currentTimeMillis(), result.mime)
                }
                withContext(NonCancellable) {
                    var warning: String? = null
                    try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    catch (_: SecurityException) { warning = "Saved. This provider may require selecting the file again after restarting Toolkit." }
                    try { app.database.exports().insert(exported) } catch (_: Exception) { warning = "File saved. Its history entry could not be stored." }
                    result.delete()
                    _state.update { it.copy(busy = false, result = null, saved = exported, error = warning) }
                }
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val removed = !committed && runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
                    _state.update { it.copy(busy = false, error = if (committed) "File saved, but its details could not be recorded. Find it in your chosen folder." else if (removed) "Saving failed. The incomplete output was removed; you can retry." else "Saving failed. A partial file may remain in the chosen folder. Remove it before retrying.") }
                }
                if (e is CancellationException) throw e
            }
        }
    }
    private fun name(uri: Uri): String = runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0)?.take(160) else null } ?: uri.lastPathSegment ?: "Document" }.getOrDefault("Document")
    override fun onCleared() { super.onCleared(); _state.value.result?.delete() }
}
