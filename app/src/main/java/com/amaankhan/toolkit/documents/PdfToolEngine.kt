package com.amaankhan.toolkit.documents

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class PdfOperation { MERGE, PROTECT, UNLOCK, EXTRACT, ORGANIZE, CROP, REMOVE_METADATA, TEXT, IMAGES }
data class PdfRequest(
    val operation: PdfOperation, val sources: List<String>, val pages: String = "", val allPages: Boolean = true,
    val password: String = "", val newPassword: String = "", val rotation: Int = 0, val cropPx: Int = 0,
    val imageFormat: String = "PNG", val imageEdge: Int = 1800,
    val textFormat: String = "TXT", val sourcePages: List<String> = emptyList(), val cropEdges: MarginsPx? = null,
    val insertPages: String = "", val insertAfter: Int = -1,
    val allowPrint: Boolean = true, val allowModify: Boolean = true, val allowExtract: Boolean = true,
)
data class PdfResult(val file: File, val preview: File, val pages: Int, val mime: String = "application/pdf", val images: List<File> = emptyList(), val sourcePages: List<Int> = emptyList(), val summary: String = "") {
    fun delete() { file.delete(); if (preview != file) preview.delete(); images.forEach { it.delete() } }
}

/** Operates in app-private staging. No inputs are modified and no document network access is used. */
class PdfToolEngine(private val context: Context, private val directory: File) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    private fun memory() = MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)
    private fun load(source: String, password: String): PDDocument {
        val uri = Uri.parse(source)
        val input = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri)
        return input?.use { PDDocument.load(it, password, memory()) } ?: throw IOException("The file is no longer accessible. Select it again.")
    }
    suspend fun pageCount(source: String, password: String = ""): Int = withContext(Dispatchers.IO) {
        directory.mkdirs(); load(source, password).use { it.numberOfPages }
    }
    suspend fun execute(request: PdfRequest, progress: (String) -> Unit = {}): PdfResult {
        val created = mutableListOf<File>()
        fun output(extension: String = ".pdf") = File.createTempFile("tool-", extension, directory).also { created += it }
        try {
            return withContext(Dispatchers.IO) {
                require(request.sources.isNotEmpty()) { "Choose a PDF first." }
                require(request.sources.size <= 20) { "Choose up to 20 PDFs at a time." }
                if (!directory.exists() && !directory.mkdirs()) throw IOException("Temporary storage is unavailable.")
                if (directory.usableSpace < 64L * 1024 * 1024) throw IOException("Free at least 64 MB of storage and try again.")
                currentCoroutineContext().ensureActive()
                val out = output(when(request.operation) { PdfOperation.TEXT -> if (request.textFormat == "Markdown") ".md" else ".txt"; PdfOperation.IMAGES -> ".zip"; else -> ".pdf" })
                val images = mutableListOf<File>()
                var sourcePages = emptyList<Int>()
                var count = 0
                var preview = out
                if (request.operation == PdfOperation.MERGE) {
                    PDDocument(memory()).use { target ->
                        val merger = PDFMergerUtility()
                        request.sources.forEachIndexed { index, source ->
                            currentCoroutineContext().ensureActive()
                            progress("Merging file ${index + 1} of ${request.sources.size}")
                            load(source, request.password).use { doc ->
                                require(target.numberOfPages + doc.numberOfPages <= 2000) { "Merge at most 2,000 pages per run." }
                                val range = request.sourcePages.getOrNull(index).orEmpty()
                                if (range.isBlank()) merger.appendDocument(target, doc)
                                else PDDocument(memory()).use { selectedDocument ->
                                    PageSelection.parse(range, doc.numberOfPages).forEach { pageIndex -> val original = doc.getPage(pageIndex); selectedDocument.importPage(original).resources = original.resources }
                                    // appendDocument deep-copies resources before the source closes.
                                    merger.appendDocument(target, selectedDocument)
                                }
                            }
                        }
                        count = target.numberOfPages
                        target.save(out)
                    }
                } else load(if (request.operation == PdfOperation.ORGANIZE) request.sources.first() else request.sources.single(), request.password).use { doc ->
                    require(doc.numberOfPages in 1..2000) { "Choose a PDF with 1–2,000 pages." }
                    count = doc.numberOfPages
                    val selected = if (request.allPages) (0 until count).toList() else PageSelection.parse(request.pages, count, request.operation == PdfOperation.ORGANIZE)
                    progress("Processing ${selected.size} pages")
                    when (request.operation) {
                        PdfOperation.PROTECT -> {
                            require(request.newPassword.isNotEmpty()) { "Enter a new password." }
                            require(request.newPassword.toByteArray(Charsets.UTF_8).size <= 127) { "Use a password of at most 127 UTF-8 bytes." }
                            doc.isAllSecurityToBeRemoved = false
                            val access = AccessPermission().apply { setCanPrint(request.allowPrint); setCanPrintDegraded(request.allowPrint); setCanModify(request.allowModify); setCanModifyAnnotations(request.allowModify); setCanFillInForm(request.allowModify); setCanAssembleDocument(request.allowModify); setCanExtractContent(request.allowExtract) }
                            val policy = StandardProtectionPolicy(java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString(), request.newPassword, access)
                            policy.encryptionKeyLength = 256
                            doc.protect(policy)
                            doc.save(out)
                            // Reopen the generated encrypted output to validate it and create its content preview.
                            preview = output()
                            PDDocument.load(out, request.newPassword, memory()).use { verified ->
                                require(verified.isEncrypted) { "The output could not be encrypted." }
                                verified.isAllSecurityToBeRemoved = true
                                verified.save(preview)
                            }
                        }
                        PdfOperation.UNLOCK -> { doc.isAllSecurityToBeRemoved = true; doc.save(out) }
                        PdfOperation.EXTRACT, PdfOperation.ORGANIZE -> PDDocument(memory()).use { target ->
                            require(request.rotation in listOf(0, 90, 180, 270))
                            selected.forEach { index ->
                                currentCoroutineContext().ensureActive()
                                val original = doc.getPage(index)
                                val page = target.importPage(original)
                                page.resources = original.resources
                                page.rotation = (original.rotation + request.rotation) % 360
                            }
                            val inserted = if (request.operation == PdfOperation.ORGANIZE && request.sources.size > 1) load(request.sources[1], request.password) else null
                            try {
                            if (inserted != null) {
                                require(request.sources.size == 2) { "Insert from one additional PDF per run." }
                                    val indices = if (request.insertPages.isBlank()) (0 until inserted.numberOfPages).toList() else PageSelection.parse(request.insertPages, inserted.numberOfPages)
                                    val position = if (request.insertAfter < 0) target.numberOfPages else request.insertAfter
                                    require(position in 0..target.numberOfPages) { "Insertion position must be between 0 and ${target.numberOfPages}." }
                                    require(target.numberOfPages + indices.size <= 2000)
                                    indices.forEachIndexed { offset, sourceIndex ->
                                        currentCoroutineContext().ensureActive(); val original = inserted.getPage(sourceIndex); val page = target.importPage(original)
                                        page.resources = original.resources; page.rotation = (original.rotation + request.rotation) % 360
                                        if (position + offset < target.numberOfPages - 1) { target.removePage(page); target.pages.insertBefore(page, target.getPage(position + offset)) }
                                    }
                            }
                            count = target.numberOfPages; target.save(out)
                            } finally { inserted?.close() }
                        }
                        PdfOperation.CROP -> {
                            require(request.cropPx in 0..160) { "Crop must be between 0 and 160 px." }
                            val edges = request.cropEdges ?: MarginsPx(request.cropPx, request.cropPx, request.cropPx, request.cropPx)
                            require(listOf(edges.left, edges.top, edges.right, edges.bottom).all { it in 0..160 })
                            selected.forEach { index ->
                                currentCoroutineContext().ensureActive()
                                val page = doc.getPage(index); val box = page.cropBox
                                val visual = listOf(edges.left, edges.top, edges.right, edges.bottom)
                                val turns = ((page.rotation % 360 + 360) % 360) / 90
                                val rotated = List(4) { visual[(it + turns) % 4] * .75f }
                                val left = rotated[0]; val top = rotated[1]; val right = rotated[2]; val bottom = rotated[3]
                                require(box.width > left + right && box.height > top + bottom) { "The crop would remove the whole page." }
                                page.cropBox = PDRectangle(box.lowerLeftX + left, box.lowerLeftY + bottom, box.width - left - right, box.height - top - bottom)
                            }
                            doc.isAllSecurityToBeRemoved = true; doc.save(out)
                        }
                        PdfOperation.REMOVE_METADATA -> {
                            doc.documentInformation = PDDocumentInformation()
                            doc.documentCatalog.metadata = null
                            doc.isAllSecurityToBeRemoved = true; doc.save(out)
                        }
                        PdfOperation.IMAGES -> {
                            require(selected.size <= 100) { "Export up to 100 pages at a time." }
                            require(request.imageFormat in listOf("PNG", "JPEG") && request.imageEdge in listOf(1200, 1800, 2400))
                            val renderSource = output()
                            doc.isAllSecurityToBeRemoved = true; doc.save(renderSource)
                            try {
                                android.os.ParcelFileDescriptor.open(renderSource, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                                        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
                                            selected.forEachIndexed { order, pageIndex ->
                                                currentCoroutineContext().ensureActive()
                                                if (directory.usableSpace < 32L * 1024 * 1024) throw IOException("Temporary storage is full. Try fewer pages.")
                                                progress("Rendering image ${order + 1} of ${selected.size}")
                                                val extension = if (request.imageFormat == "PNG") "png" else "jpg"
                                                val image = output(".$extension")
                                                renderer.openPage(pageIndex).use { page ->
                                                    val scale = request.imageEdge.toFloat() / maxOf(page.width, page.height)
                                                    val bitmap = android.graphics.Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                                                    try {
                                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                                        image.outputStream().use { stream ->
                                                            require(bitmap.compress(if (extension == "png") android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)) { "Could not encode this page." }
                                                        }
                                                    } finally { bitmap.recycle() }
                                                }
                                                images += image
                                                zip.putNextEntry(java.util.zip.ZipEntry("page-${(pageIndex + 1).toString().padStart(3, '0')}.$extension"))
                                                image.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                                            }
                                        }
                                    }
                                }
                            } finally { renderSource.delete() }
                            count = selected.size; sourcePages = selected.map { it + 1 }; preview = images.first()
                        }
                        PdfOperation.TEXT -> {
                            val stripper = PDFTextStripper().apply { sortByPosition = true }
                            var hasText = false
                            out.bufferedWriter(Charsets.UTF_8).use { writer ->
                                selected.forEach { index ->
                                    currentCoroutineContext().ensureActive()
                                    stripper.startPage = index + 1; stripper.endPage = index + 1
                                    val text = stripper.getText(doc); if (text.isNotBlank()) hasText = true
                                    if (request.textFormat == "Markdown") { writer.write("## Page ${index + 1}\n\n"); writer.write(text.trim().replace("\n", "  \n")); writer.write("\n\n") }
                                    else { writer.write(text); writer.newLine() }
                                }
                            }
                            require(hasText) { "No text layer was found. Scanned pages need OCR, which is outside this tool’s scope." }
                            count = selected.size
                        }
                        else -> error("Unsupported operation")
                    }
                }
                currentCoroutineContext().ensureActive()
                if (request.operation !in setOf(PdfOperation.TEXT, PdfOperation.IMAGES)) PDDocument.load(out, if (request.operation == PdfOperation.PROTECT) request.newPassword else "", memory()).use {
                    require(it.numberOfPages == count && count > 0) { "The output page count could not be verified." }
                }
                PdfResult(out, preview, count, when (request.operation) { PdfOperation.TEXT -> if (request.textFormat == "Markdown") "text/markdown" else "text/plain"; PdfOperation.IMAGES -> "application/zip"; else -> "application/pdf" }, images, sourcePages)
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { created.forEach { it.delete() } }
            throw error
        }
    }
}
