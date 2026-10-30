package com.amaankhan.toolkit

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amaankhan.toolkit.data.ExportedDocument
import com.amaankhan.toolkit.data.Preferences
import com.amaankhan.toolkit.data.ThemeChoice
import com.amaankhan.toolkit.data.Palette
import com.amaankhan.toolkit.documents.ImagePdfEngine
import com.amaankhan.toolkit.documents.PdfLayout
import com.amaankhan.toolkit.documents.PdfOptions
import com.amaankhan.toolkit.documents.SelectedImage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RunPhase { IDLE, READING, CREATING, READY, SAVING, SAVED }
data class ComposerState(
    val images: List<SelectedImage> = emptyList(), val options: PdfOptions = PdfOptions(),
    val phase: RunPhase = RunPhase.IDLE, val progress: Int = 0, val message: String? = null,
    val importTotal: Int = 0,
    val prepared: File? = null, val saved: ExportedDocument? = null,
) { val busy get() = phase in setOf(RunPhase.READING, RunPhase.CREATING, RunPhase.SAVING) }

class ToolkitViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ToolkitApplication
    private val resolver = app.contentResolver
    private val staging = File(app.cacheDir, "pdf-staging")
    private val engine = ImagePdfEngine(resolver, staging)
    val preferences = app.preferences.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val exports = app.database.exports().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _composer = MutableStateFlow(ComposerState())
    val composer = _composer.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private var operation: Job? = null
    // Sequential startup cleanup completes before any new conversion is allowed to use staging.
    private val cleanup = viewModelScope.launch(Dispatchers.IO) {
        staging.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("images-") && file.extension == "pdf") file.delete()
            else if (file.isDirectory && file.name.startsWith("images-work-")) file.deleteRecursively()
        }
    }

    fun setTheme(theme: ThemeChoice) = preferenceAction { app.preferences.setTheme(theme) }
    fun setPalette(palette: Palette) = preferenceAction { app.preferences.setPalette(palette) }
    fun setProfile(name: String, workspace: String) = preferenceAction { app.preferences.setProfile(name, workspace) }
    fun useTool(id: String) = preferenceAction { app.preferences.useTool(id) }
    fun favorite(id: String) = preferenceAction { app.preferences.toggleFavorite(id) }
    fun usedImagesTool() = preferenceAction { app.preferences.useTool("D01") }
    fun clearRecents() = preferenceAction { app.preferences.clearRecents() }
    fun clearExportHistory() = preferenceAction {
        exports.value.forEach { releaseGrant(it.uri) }
        app.database.exports().clear()
    }
    fun removeHistory(uri: String) = preferenceAction { app.database.exports().remove(uri); releaseGrant(uri) }
    private fun releaseGrant(uri: String) {
        try { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { /* Provider may only have offered a temporary grant. */ }
    }
    private fun preferenceAction(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) { _notice.value = "Could not save this change. Please try again." }
    } }
    fun dismissNotice() { _notice.value = null }

    fun addImages(uris: List<Uri>) {
        if (_composer.value.busy || uris.isEmpty()) return
        val prior = _composer.value
        val knownUris = prior.images.mapTo(HashSet()) { it.uri }
        val additions = uris.filter { knownUris.add(it.toString()) }
        if (prior.images.size + additions.size > PdfLayout.MAX_IMAGES) {
            _composer.update { it.copy(message = "Choose up to ${PdfLayout.MAX_IMAGES} images per PDF. No new images were added.") }; return
        }
        if (additions.isEmpty()) return
        operation = viewModelScope.launch {
            _composer.update { it.copy(phase = RunPhase.READING, progress = 0, importTotal = additions.size, message = null) }
            try {
                val selected = withContext(Dispatchers.IO) { additions.mapIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    catch (_: SecurityException) { /* Some providers offer session-only access. */ }
                    SelectedImage(uri.toString(), displayName(uri, "Image ${prior.images.size + index + 1}")).also {
                        _composer.update { state -> state.copy(progress = index + 1) }
                    }
                } }
                invalidatePrepared()
                _composer.update { it.copy(images = prior.images + selected, phase = RunPhase.IDLE) }
            } catch (e: CancellationException) { _composer.update { it.copy(phase = RunPhase.IDLE) }; throw e }
            catch (_: Exception) { _composer.update { it.copy(phase = prior.phase, message = "The selected images could not be read. Please choose them again.") } }
        }
    }
    fun removeImage(uri: String) { edit { it.copy(images = it.images.filterNot { image -> image.uri == uri }) } }
    fun moveImage(index: Int, delta: Int) { edit {
        val items = it.images.toMutableList(); val target = index + delta
        if (index in items.indices && target in items.indices) { val image = items.removeAt(index); items.add(target, image) }
        it.copy(images = items)
    } }
    fun options(options: PdfOptions) { edit { it.copy(options = options) } }
    private fun edit(change: (ComposerState) -> ComposerState) {
        if (_composer.value.busy) return
        invalidatePrepared(); _composer.update { change(it).copy(phase = RunPhase.IDLE, message = null, saved = null) }
    }
    private fun invalidatePrepared() { _composer.value.prepared?.delete(); _composer.update { it.copy(prepared = null, saved = null) } }
    fun reset() { if (!_composer.value.busy) { invalidatePrepared(); _composer.value = ComposerState() } }

    fun createPdf() {
        val request = _composer.value
        if (request.busy || request.images.isEmpty()) return
        operation = viewModelScope.launch {
            _composer.update { it.copy(phase = RunPhase.CREATING, progress = 0, message = null) }
            try {
                cleanup.join()
                val file = engine.create(request.images, request.options) { page, _ -> _composer.update { it.copy(progress = page) } }
                _composer.update { it.copy(phase = RunPhase.READY, prepared = file) }
            } catch (e: CancellationException) {
                _composer.update { it.copy(phase = RunPhase.IDLE, message = "Creation cancelled. Your source images are unchanged.") }; throw e
            } catch (e: IOException) {
                _composer.update { it.copy(phase = RunPhase.IDLE, message = e.message ?: "Could not create the PDF. Try fewer images.") }
            } catch (_: OutOfMemoryError) {
                _composer.update { it.copy(phase = RunPhase.IDLE, message = "Not enough memory. Try fewer or smaller images.") }
            } catch (_: Exception) {
                _composer.update { it.copy(phase = RunPhase.IDLE, message = "Could not create the PDF. Check image access and free storage, then try again.") }
            }
        }
    }
    fun cancelCreation() { if (_composer.value.phase == RunPhase.CREATING) {
        _composer.update { it.copy(message = "Cancelling after the current page finishes…") }; operation?.cancel()
    } }
    fun saveCancelled() { _composer.update { it.copy(message = "PDF is ready. Choose Save PDF when you want to pick a destination.") } }
    fun export(uri: Uri?) {
        if (uri == null) { saveCancelled(); return }
        val state = _composer.value
        val file = state.prepared ?: return
        if (state.busy || state.phase != RunPhase.READY) return
        operation = viewModelScope.launch {
            _composer.update { it.copy(phase = RunPhase.SAVING, message = null) }
            var committed = false
            try {
                val exported = withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.use { destination ->
                        file.inputStream().use { source ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                destination.write(buffer, 0, count)
                            }
                        }
                        destination.flush()
                    } ?: throw IOException("The selected folder is not writable.")
                    // Completion is after close; metadata failures must not delete a completed PDF.
                    committed = true
                    ExportedDocument(uri.toString(), displayName(uri, "Toolkit.pdf"), state.images.size, file.length(), System.currentTimeMillis())
                }
                withContext(NonCancellable) {
                    var warning: String? = null
                    try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    catch (_: SecurityException) { warning = "Saved. This provider may require selecting the file again after restarting Toolkit." }
                    try { app.database.exports().insert(exported) }
                    catch (_: Exception) { warning = "PDF saved, but its history entry could not be stored. Find it in your selected folder." }
                    file.delete()
                    _composer.update { it.copy(phase = RunPhase.SAVED, prepared = null, saved = exported, message = warning) }
                }
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val removed = !committed && runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
                    val message = if (committed) "PDF saved, but its details could not be loaded. Find it in the selected folder."
                        else if (removed) "Saving failed. The incomplete output was removed. Your prepared PDF is ready to save again."
                        else "Saving failed. A partial file may remain in your chosen folder; remove it before retrying. Your prepared PDF is still ready."
                    _composer.update { it.copy(phase = if (committed) RunPhase.SAVED else RunPhase.READY, message = message) }
                }
                if (e is CancellationException) throw e
            }
        }
    }
    private fun displayName(uri: Uri, fallback: String): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.take(160)?.ifBlank { fallback } else null
        } ?: fallback
    }.getOrDefault(fallback)
}
