package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.amaankhan.toolkit.R
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.*
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExtraPdfRequest(val action: String, val source: String, val password: String = "", val options: JSONObject = JSONObject(), val asset: String = "")

class ExtraPdfEngine(private val context: Context, private val directory: File) {
    // A bundled TTF font is a file resource. openRawResource returns its bytes;
    // @RawRes is narrower than the resource types Android can open here.
    @android.annotation.SuppressLint("ResourceType")
    private fun embeddedFont(doc: PDDocument): PDType0Font = context.resources.openRawResource(R.font.inter).use { PDType0Font.load(doc, it) }
    private fun open(source: String) = Uri.parse(source).let { if (it.scheme == "file") File(requireNotNull(it.path)).inputStream() else context.contentResolver.openInputStream(it) ?: error("File access expired.") }
    private fun memory() = MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)
    suspend fun execute(request: ExtraPdfRequest, progress: (String) -> Unit = {}): PdfResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val created = mutableListOf<File>()
        fun file(extension: String = "pdf") = File(directory, "extra-${UUID.randomUUID()}.$extension").also { created += it }
        try {
            open(request.source).use { input -> PDDocument.load(input, request.password, memory()).use { doc ->
                require(doc.numberOfPages in 1..2000) { "Choose a document with 1–2,000 pages." }
                val options = request.options
                val pageText = options.optString("pages")
                val pages = if (pageText.isBlank()) (0 until doc.numberOfPages).toList() else PageSelection.parse(pageText, doc.numberOfPages)
                val output = file(if (request.action in setOf("SPLIT", "EMBEDDED")) "zip" else "pdf")
                var result = PdfResult(output, output, doc.numberOfPages)
                when (request.action) {
                    "COMPRESS" -> {
                        val edge = options.optInt("edge", 0)
                        val quality = options.optInt("quality", 80).coerceIn(25, 100) / 100f
                        require(edge in listOf(0, 800, 1200, 1800, 2400))
                        val visited = mutableSetOf<com.tom_roush.pdfbox.cos.COSBase>()
                        suspend fun images(resources: PDResources?) {
                            if (resources == null || !visited.add(resources.cosObject)) return
                            for (name in resources.xObjectNames.toList()) {
                                currentCoroutineContext().ensureActive()
                                when (val item = resources.getXObject(name)) {
                                    is PDImageXObject -> if (edge > 0 && item.width.toLong() * item.height <= 24_000_000 && !item.isStencil && item.softMask == null && item.mask == null) {
                                        val bitmap = item.image
                                        try {
                                            val scale = (edge.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                                            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                                            try {
                                                val replacement = JPEGFactory.createFromImage(doc, scaled, quality)
                                                if (replacement.cosObject.length < item.cosObject.length) resources.put(name, replacement)
                                            } finally { if (scaled !== bitmap) scaled.recycle() }
                                        } finally { bitmap.recycle() }
                                    }
                                    is PDFormXObject -> images(item.resources)
                                }
                            }
                        }
                        pages.forEachIndexed { i, p -> progress("Optimizing page ${i + 1}/${pages.size}"); images(doc.getPage(p).resources) }
                        // Rewrite content streams with Flate compression without rasterizing text or vectors.
                        pages.forEach { p ->
                            currentCoroutineContext().ensureActive()
                            val page = doc.getPage(p); val content = file("content")
                            try {
                                page.contents.use { source -> content.outputStream().use { source.copyTo(it) } }
                                val stream = com.tom_roush.pdfbox.pdmodel.common.PDStream(doc)
                                stream.createOutputStream(COSName.FLATE_DECODE).use { target -> content.inputStream().use { it.copyTo(target) } }
                                page.setContents(stream)
                            } finally { content.delete() }
                        }
                        doc.isAllSecurityToBeRemoved = true; doc.save(output)
                    }
                    "STAMP", "SIGN" -> {
                        val isImage = request.asset.isNotBlank()
                        val font = if (!isImage) embeddedFont(doc) else null
                        val image = if (isImage) open(request.asset).use { stream ->
                            val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
                            while (true) { val count = stream.read(chunk); if (count < 0) break
                                require(buffer.size() + count <= 16 * 1024 * 1024) { "Choose a smaller stamp image." }; buffer.write(chunk, 0, count)
                            }
                            val data = buffer.toByteArray()
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                            require(bounds.outWidth.toLong() * bounds.outHeight in 1..16_000_000) { "Choose a PNG or JPEG up to 16 megapixels." }
                            PDImageXObject.createFromByteArray(doc, data, "Stamp")
                        } else null
                        val size = options.optInt("size", 24).coerceIn(8, 96).toFloat()
                        val x = options.optDouble("x", 50.0).coerceIn(0.0, 100.0).toFloat() / 100
                        val y = options.optDouble("y", 50.0).coerceIn(0.0, 100.0).toFloat() / 100
                        val widthFraction = options.optDouble("width", 30.0).coerceIn(5.0, 90.0).toFloat() / 100
                        val opacity = options.optDouble("opacity", if (request.action == "SIGN") 1.0 else 0.35).coerceIn(0.05, 1.0).toFloat()
                        val text = options.optString("text", "CONFIDENTIAL").take(160)
                        pages.forEachIndexed { order, index ->
                            currentCoroutineContext().ensureActive(); progress("Adding mark ${order + 1}/${pages.size}")
                            val page = doc.getPage(index); val box = page.cropBox; val rotation = (page.rotation % 360 + 360) % 360
                            val width = if (rotation in listOf(90, 270)) box.height else box.width
                            val height = if (rotation in listOf(90, 270)) box.width else box.height
                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { canvas ->
                                canvas.saveGraphicsState()
                                val transform = when (rotation) {
                                    90 -> Matrix(0f, 1f, -1f, 0f, box.upperRightX, box.lowerLeftY)
                                    180 -> Matrix(-1f, 0f, 0f, -1f, box.upperRightX, box.upperRightY)
                                    270 -> Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, box.upperRightY)
                                    else -> Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY)
                                }
                                canvas.transform(transform)
                                canvas.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity; strokingAlphaConstant = opacity })
                                if (image != null) {
                                    val scale = minOf(width * widthFraction / image.width, height * .9f / image.height)
                                    val w = image.width * scale; val h = image.height * scale
                                    canvas.drawImage(image, ((width - w) * x), ((height - h) * (1 - y)), w, h)
                                } else {
                                    val label = text.replace("{page}", (index + 1).toString()).replace("{total}", doc.numberOfPages.toString())
                                    val measured = font!!.getStringWidth(label) / 1000 * size
                                    val effective = if (measured > width - 24) size * (width - 24) / measured else size
                                    canvas.beginText(); canvas.setFont(font, effective); canvas.setNonStrokingColor(30, 30, 30)
                                    canvas.newLineAtOffset(((width - measured.coerceAtMost(width - 24)) * x).coerceAtLeast(12f), ((height - effective - 24) * (1 - y) + 12))
                                    canvas.showText(label); canvas.endText()
                                }
                                canvas.restoreGraphicsState()
                            }
                        }
                        doc.isAllSecurityToBeRemoved = true; doc.save(output)
                    }
                    "SPLIT" -> {
                        val mode = options.optString("mode", "Each page")
                        val groups = if (mode == "Ranges") options.getString("groups").split(';').map { PageSelection.parse(it.trim(), doc.numberOfPages) }
                            else pages.chunked(options.optInt("every", 1).coerceIn(1, 2000))
                        require(groups.size in 1..500) { "Create up to 500 split documents at a time." }
                        val preview = file()
                        ZipOutputStream(output.outputStream().buffered()).use { zip ->
                            groups.forEachIndexed { i, indices ->
                                currentCoroutineContext().ensureActive(); progress("Creating document ${i + 1}/${groups.size}")
                                PDDocument(memory()).use { part ->
                                    indices.forEach { index -> val original = doc.getPage(index); part.importPage(original).resources = original.resources }
                                    val temp = if (i == 0) preview else file()
                                    part.save(temp); zip.putNextEntry(ZipEntry("part-${i + 1}.pdf")); temp.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                                    if (temp != preview) temp.delete()
                                }
                            }
                        }
                        result = PdfResult(output, preview, groups.sumOf { it.size }, "application/zip")
                    }
                    "EMBEDDED" -> {
                        val images = mutableListOf<File>(); val sourcePages = mutableListOf<Int>()
                        val seen = mutableSetOf<com.tom_roush.pdfbox.cos.COSBase>()
                        ZipOutputStream(output.outputStream().buffered()).use { zip ->
                            suspend fun extract(resources: PDResources?, page: Int) {
                                if (resources == null || !seen.add(resources.cosObject)) return
                                for (name in resources.xObjectNames) when (val obj = resources.getXObject(name)) {
                                    is PDFormXObject -> extract(obj.resources, page)
                                    is PDImageXObject -> if (seen.add(obj.cosObject)) {
                                        currentCoroutineContext().ensureActive()
                                        require(images.size < 200 && obj.width.toLong() * obj.height <= 24_000_000) { "This document exceeds the image extraction memory limit. Select fewer pages." }
                                        val image = file("png"); val bitmap = obj.image
                                        try { image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
                                        images += image; sourcePages += page + 1
                                        zip.putNextEntry(ZipEntry("page-${page + 1}-image-${images.size}.png")); image.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                                    }
                                }
                            }
                            pages.forEach { page -> progress("Extracting images from page ${page + 1}"); extract(doc.getPage(page).resources, page) }
                        }
                        require(images.isNotEmpty()) { "No embedded image objects were found in these pages." }
                        result = PdfResult(output, images.first(), images.size, "application/zip", images, sourcePages)
                    }
                    else -> error("Unknown PDF action")
                }
                currentCoroutineContext().ensureActive()
                if (result.mime == "application/pdf") PDDocument.load(output, memory()).use { require(it.numberOfPages == doc.numberOfPages) }
                result
            } }
        } catch (failure: Throwable) { withContext(NonCancellable) { created.forEach(File::delete) }; throw failure }
    }
    suspend fun metadata(source: String, password: String): String = withContext(Dispatchers.IO) {
        open(source).use { stream -> PDDocument.load(stream, password, memory()).use { doc ->
            val info = doc.documentInformation
            buildString {
                appendLine("Pages: ${doc.numberOfPages}"); appendLine("Encrypted: ${doc.isEncrypted}")
                info.metadataKeys.sorted().forEach { key -> appendLine("$key: ${info.getCustomMetadataValue(key).orEmpty()}") }
                appendLine("XMP metadata: ${if (doc.documentCatalog.metadata == null) "None" else "Present"}")
            }
        } }
    }
}
