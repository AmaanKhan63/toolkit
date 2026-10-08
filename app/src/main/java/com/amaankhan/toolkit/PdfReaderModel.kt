package com.amaankhan.toolkit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

data class PdfReaderState(val file: File? = null, val pages: Int = 0, val busy: Boolean = false, val passwordRequired: Boolean = false, val error: String = "", val outline: List<Pair<String, Int>> = emptyList())
class PdfReaderModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val state = MutableStateFlow(PdfReaderState())
    val matches = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val searching = MutableStateFlow(false)
    private var load: Job? = null; private var search: Job? = null
    private val owned = java.util.concurrent.ConcurrentLinkedQueue<File>()
    private val directory = File(app.cacheDir, "pdf-tools").apply { mkdirs() }
    fun open(source: String, password: String = "") {
        load?.cancel(); search?.cancel(); matches.value = emptyList()
        load = viewModelScope.launch {
            state.value = PdfReaderState(busy = true)
            try {
                val next = withContext(Dispatchers.IO) {
                    val output = File(directory, "viewer-${UUID.randomUUID()}.pdf"); owned += output
                    val uri = Uri.parse(source)
                    val input = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else app.contentResolver.openInputStream(uri) ?: error("The file is no longer accessible.")
                    input.use { stream -> PDDocument.load(stream, password, MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)).use { doc ->
                        require(doc.numberOfPages in 1..2000) { "Open a PDF with 1–2,000 pages." }
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
                        currentCoroutineContext().ensureActive(); doc.isAllSecurityToBeRemoved = true; doc.save(output)
                        PdfReaderState(output, doc.numberOfPages, outline = outline)
                    } }
                }
                state.value = next
            } catch (e: CancellationException) { throw e }
            catch (_: InvalidPasswordException) { state.value = PdfReaderState(passwordRequired = true, error = if (password.isBlank()) "This PDF requires a password." else "That password did not unlock the PDF.") }
            catch (_: OutOfMemoryError) { state.value = PdfReaderState(error = "Not enough memory. Try a smaller document.") }
            catch (e: Exception) { state.value = PdfReaderState(error = e.message ?: "Unable to open this PDF.") }
        }
    }
    fun find(query: String) {
        search?.cancel(); matches.value = emptyList()
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
            catch (e: Exception) { state.value = state.value.copy(error = "Text search could not read this PDF.") }
            finally { searching.value = false }
        }
    }
    override fun onCleared() { super.onCleared(); owned.forEach(File::delete) }
}
