package com.amaankhan.toolkit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amaankhan.toolkit.documents.documentDisplayName
import com.amaankhan.toolkit.documents.documentReadError
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

data class PdfReaderState(val file: File? = null, val pages: Int = 0, val busy: Boolean = false, val passwordRequired: Boolean = false, val error: String = "", val outline: List<Pair<String, Int>> = emptyList(), val name: String = "Document")
class PdfReaderModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val state = MutableStateFlow(PdfReaderState())
    val matches = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val searching = MutableStateFlow(false)
    private var load: Job? = null; private var search: Job? = null
    private var searchGeneration = 0
    private val directory = File(app.cacheDir, "pdf-tools").apply { mkdirs() }
    fun open(source: String, password: String = "") {
        val previousLoad = load
        val previousSearch = search
        val previousFile = state.value.file
        previousLoad?.cancel(); previousSearch?.cancel(); matches.value = emptyList()
        previousFile?.delete()
        searchGeneration++; searching.value = false
        load = viewModelScope.launch {
            var output: File? = null
            var name = "Document"
            state.value = PdfReaderState(busy = true)
            try {
                previousLoad?.join(); previousSearch?.join()
                val next = withContext(Dispatchers.IO) {
                    if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Reader cache is unavailable.")
                    val uri = Uri.parse(source)
                    name = documentDisplayName(app.contentResolver, uri)
                    val target = File(directory, "viewer-${UUID.randomUUID()}.pdf")
                    output = target
                    val input = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else app.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
                    input.use { stream -> PDDocument.load(stream, password, MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)).use { doc ->
                        if (doc.numberOfPages !in 1..2000) throw PdfPageLimitException()
                        val outline = mutableListOf<Pair<String, Int>>()
                        fun collect(first: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem?, depth: Int = 0) {
                            if (depth > 16) return
                            var item = first
                            val seen = mutableSetOf<com.tom_roush.pdfbox.cos.COSDictionary>()
                            while (item != null && seen.add(item.cosObject) && outline.size < 500) {
                                val index = runCatching { item!!.findDestinationPage(doc)?.let { page -> doc.pages.indexOf(page) } ?: -1 }.getOrDefault(-1)
                                if (index >= 0) outline += item.title.orEmpty() to index
                                collect(item.firstChild, depth + 1); item = item.nextSibling
                            }
                        }
                        collect(doc.documentCatalog.documentOutline?.firstChild)
                        currentCoroutineContext().ensureActive(); doc.isAllSecurityToBeRemoved = true; doc.save(target)
                        PdfReaderState(target, doc.numberOfPages, outline = outline, name = name)
                    } }
                }
                state.value = next
            } catch (e: CancellationException) { throw e }
            catch (_: InvalidPasswordException) { currentCoroutineContext().ensureActive(); state.value = PdfReaderState(name = name, passwordRequired = true, error = if (password.isBlank()) "This PDF requires a password." else "That password did not unlock the PDF.") }
            catch (_: PdfPageLimitException) { currentCoroutineContext().ensureActive(); state.value = PdfReaderState(name = name, error = "Open a PDF with 1–2,000 pages.") }
            catch (e: OutOfMemoryError) { currentCoroutineContext().ensureActive(); state.value = PdfReaderState(name = name, error = documentReadError(e, pdf = true)) }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); state.value = PdfReaderState(name = name, error = documentReadError(e, pdf = true)) }
            finally { output?.takeIf { it != state.value.file }?.delete() }
        }
    }
    fun find(query: String) {
        search?.cancel(); matches.value = emptyList()
        val generation = ++searchGeneration
        searching.value = false
        val file = state.value.file ?: return
        if (query.isBlank()) return
        search = viewModelScope.launch {
            searching.value = true
            try { matches.value = withContext(Dispatchers.IO) { PDDocument.load(file, MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)).use { doc ->
                val results = mutableListOf<Pair<Int, String>>(); val stripper = PDFTextStripper().apply { sortByPosition = true }
                repeat(doc.numberOfPages) { index ->
                    currentCoroutineContext().ensureActive(); stripper.startPage = index + 1; stripper.endPage = index + 1
                    val text = stripper.getText(doc); val position = text.indexOf(query, ignoreCase = true)
                    if (position >= 0 && results.size < 500) results += index to text.substring((position - 35).coerceAtLeast(0), (position + query.length + 100).coerceAtMost(text.length)).replace('\n', ' ')
                }; results
            } } } catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); state.value = state.value.copy(error = "Text search could not read this PDF.") }
            finally { if (generation == searchGeneration) searching.value = false }
        }
    }
    fun renderFailed(file: File, message: String) {
        val current = state.value
        // Ignore a late callback from a preview that has already been replaced.
        if (current.file != file) return
        search?.cancel(); searchGeneration++
        matches.value = emptyList(); searching.value = false
        file.delete()
        state.value = PdfReaderState(name = current.name, error = message)
    }
    fun close() {
        load?.cancel(); search?.cancel(); searchGeneration++
        state.value.file?.delete()
        state.value = PdfReaderState()
        matches.value = emptyList(); searching.value = false
    }
    override fun onCleared() { close(); super.onCleared() }
}

private class PdfPageLimitException : Exception()
