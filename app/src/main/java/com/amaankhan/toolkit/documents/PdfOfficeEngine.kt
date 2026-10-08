package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.BreakType
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
data class PdfOfficeRequest(val source: String, val format: String, val pages: String = "", val password: String = "")
private data class PdfLine(val text: String, val positions: List<TextPosition>)

class PdfOfficeEngine(private val context: Context, private val directory: File) {
    suspend fun execute(request: PdfOfficeRequest, progress: (String) -> Unit = {}): PdfResult = withContext(Dispatchers.IO) {
        require(request.format in listOf("docx", "xlsx", "pptx"))
        directory.mkdirs(); OfficeEngine(context, directory)
        val created = mutableListOf<File>()
        fun temp(extension: String) = File(directory, "pdf-office-${UUID.randomUUID()}.$extension").also { created += it }
        try {
            val source = Uri.parse(request.source)
            val input = if (source.scheme == "file") File(requireNotNull(source.path)).inputStream() else context.contentResolver.openInputStream(source) ?: error("Choose the PDF again.")
            input.use { stream -> PDDocument.load(stream, request.password, MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(directory)).use { doc ->
                require(doc.numberOfPages in 1..2000)
                val selected = if (request.pages.isBlank()) (0 until doc.numberOfPages).toList() else PageSelection.parse(request.pages, doc.numberOfPages)
                require(selected.size <= 200) { "Convert up to 200 pages at a time." }
                val output = temp(request.format)
                if (request.format == "pptx") {
                    val plain = temp("pdf"); doc.isAllSecurityToBeRemoved = true; doc.save(plain)
                    val pages = mutableListOf<File>()
                    var size = 1.414f
                    ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer ->
                        selected.forEachIndexed { i, index ->
                            currentCoroutineContext().ensureActive(); progress("Rendering slide ${i + 1}/${selected.size}")
                            val image = temp("png")
                            renderer.openPage(index).use { page ->
                                if (i == 0) size = page.height.toFloat() / page.width
                                val scale = 1800f / maxOf(page.width, page.height)
                                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                try { bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
                            }; pages += image
                        }
                    } }
                    writePresentation(output, pages, size)
                    pages.forEach(File::delete); plain.delete()
                } else {
                    val extracted = selected.mapIndexed { i, page ->
                        currentCoroutineContext().ensureActive(); progress("Reading page ${i + 1}/${selected.size}")
                        val lines = mutableListOf<PdfLine>()
                        val stripper = object : PDFTextStripper() {
                            override fun writeString(text: String, textPositions: MutableList<TextPosition>) { lines += PdfLine(text, textPositions.toList()) }
                        }.apply { sortByPosition = true; startPage = page + 1; endPage = page + 1 }
                        stripper.getText(doc); lines
                    }
                    require(extracted.any { page -> page.any { it.text.isNotBlank() } }) { "This PDF has no text layer. Scanned pages need OCR, which is not included." }
                    if (request.format == "docx") XWPFDocument().use { word ->
                        extracted.forEachIndexed { page, lines ->
                            if (page > 0) word.createParagraph().createRun().addBreak(BreakType.PAGE)
                            lines.forEach { line ->
                                val paragraph = word.createParagraph(); val first = line.positions.firstOrNull()
                                if (first != null) paragraph.indentationLeft = (first.xDirAdj * 20).toInt().coerceIn(0, 3000)
                                val run = paragraph.createRun(); run.setText(line.text)
                                run.setFontSize((first?.fontSizeInPt ?: 12f).toDouble().coerceIn(6.0, 72.0))
                                run.isBold = first?.font?.name?.contains("bold", true) == true
                            }
                        }
                        output.outputStream().use(word::write)
                    } else XSSFWorkbook().use { workbook ->
                        extracted.forEachIndexed { page, lines ->
                            val sheet = workbook.createSheet("Page ${selected[page] + 1}")
                            // Infer cell boundaries from horizontal glyph gaps, keeping leading-zero identifiers as text.
                            lines.forEachIndexed { index, line ->
                                currentCoroutineContext().ensureActive()
                                val row = sheet.createRow(index); val cells = mutableListOf<String>(); val text = StringBuilder(); var previous: TextPosition? = null
                                line.positions.forEach { position ->
                                    val before = previous
                                    if (before != null && position.xDirAdj - before.xDirAdj - before.widthDirAdj > maxOf(8f, position.widthOfSpace * 2)) { cells += text.toString(); text.clear() }
                                    text.append(position.unicode); previous = position
                                }
                                if (text.isNotEmpty()) cells += text.toString()
                                if (cells.isEmpty()) cells += line.text
                                cells.take(256).forEachIndexed { c, value -> val cell = row.createCell(c); val trimmed = value.trim()
                                    if (trimmed.matches(Regex("-?(0|[1-9]\\d*)(\\.\\d+)?")) && trimmed.length < 16) cell.setCellValue(trimmed.toDouble()) else cell.setCellValue(value)
                                    sheet.setColumnWidth(c, maxOf(sheet.getColumnWidth(c), (value.length.coerceAtMost(60) + 2) * 256))
                                }
                            }
                        }
                        output.outputStream().use(workbook::write)
                    }
                }
                currentCoroutineContext().ensureActive(); progress("Preparing the generated document preview")
                val preview = if (request.format == "xlsx") OfficeEngine(context, directory).execute(OfficeRequest(Uri.fromFile(output).toString(), OfficeAction.TO_PDF))
                    else RichPdfEngine(context, directory).execute(RichPdfRequest(Uri.fromFile(output).toString()))
                created += preview.file
                PdfResult(output, preview.file, selected.size, when (request.format) { "docx" -> DOCX_MIME; "xlsx" -> XLSX_MIME; else -> PPTX_MIME })
            } }
        } catch (error: Throwable) { created.forEach(File::delete); throw error }
    }
    private fun writePresentation(output: File, images: List<File>, ratio: Float) {
        val p = "http://schemas.openxmlformats.org/presentationml/2006/main"
        val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
        val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val relNamespace = "http://schemas.openxmlformats.org/package/2006/relationships"
        val width = 9144000L; val height = (width * ratio).toLong().coerceIn(914400, 51206400)
        val group = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun put(path: String, xml: String) { zip.putNextEntry(ZipEntry(path)); zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + xml).toByteArray()); zip.closeEntry() }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"$PPTX_MIME.main+xml\"/><Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/><Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>" + images.indices.joinToString("") { "<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" } + "</Types>")
            put("_rels/.rels", "<Relationships xmlns=\"$relNamespace\"><Relationship Id=\"rId1\" Type=\"$r/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>")
            put("ppt/presentation.xml", "<p:presentation xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$r\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" + images.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 2}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"$width\" cy=\"$height\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
            put("ppt/_rels/presentation.xml.rels", "<Relationships xmlns=\"$relNamespace\"><Relationship Id=\"rId1\" Type=\"$r/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>" + images.indices.joinToString("") { "<Relationship Id=\"rId${it + 2}\" Type=\"$r/slide\" Target=\"slides/slide${it + 1}.xml\"/>" } + "</Relationships>")
            put("ppt/slideMasters/slideMaster1.xml", "<p:sldMaster xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$r\"><p:cSld><p:spTree>$group</p:spTree></p:cSld><p:clrMap accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" bg1=\"lt1\" bg2=\"lt2\" folHlink=\"folHlink\" hlink=\"hlink\" tx1=\"dk1\" tx2=\"dk2\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>")
            put("ppt/slideMasters/_rels/slideMaster1.xml.rels", "<Relationships xmlns=\"$relNamespace\"><Relationship Id=\"rId1\" Type=\"$r/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/></Relationships>")
            put("ppt/slideLayouts/slideLayout1.xml", "<p:sldLayout xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$r\" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>$group</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>")
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", "<Relationships xmlns=\"$relNamespace\"><Relationship Id=\"rId1\" Type=\"$r/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>")
            images.forEachIndexed { i, image ->
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(image.path, bounds)
                val scale = minOf(width.toDouble() / bounds.outWidth, height.toDouble() / bounds.outHeight)
                val w = (bounds.outWidth * scale).toLong(); val h = (bounds.outHeight * scale).toLong()
                put("ppt/slides/slide${i + 1}.xml", "<p:sld xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$r\"><p:cSld><p:spTree>$group<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Page ${i + 1}\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"${(width - w) / 2}\" y=\"${(height - h) / 2}\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")
                put("ppt/slides/_rels/slide${i + 1}.xml.rels", "<Relationships xmlns=\"$relNamespace\"><Relationship Id=\"rId1\" Type=\"$r/image\" Target=\"../media/page${i + 1}.png\"/><Relationship Id=\"rId2\" Type=\"$r/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/></Relationships>")
                zip.putNextEntry(ZipEntry("ppt/media/page${i + 1}.png")); image.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
}
