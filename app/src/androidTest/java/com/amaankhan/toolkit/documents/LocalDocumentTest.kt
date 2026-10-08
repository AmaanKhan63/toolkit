package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LocalDocumentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    @Before fun setup() { root = File(context.cacheDir, "reader-test-${System.nanoTime()}").apply { mkdirs() }; OfficeEngine(context, root) }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun markdownHasTablesAndPagedPdfRetainsTheEnd() = runBlocking {
        val source = File(root, "notes.md").apply { writeText("# Title\n\n| Item | Value |\n| --- | --- |\n| First | 42 |\n\n" + (1..250).joinToString("\n\n") { "Paragraph $it: A searchable document with content that must not disappear at page breaks." }) }
        val doc = LocalDocumentReader(context).read(Uri.fromFile(source).toString())
        assertTrue(doc.sections.single().html.contains("<table>"))
        val result = RichPdfEngine(context, root).execute(RichPdfRequest(Uri.fromFile(source).toString()))
        PDDocument.load(result.file).use { pdf -> assertTrue(pdf.numberOfPages > 1); val text = PDFTextStripper().getText(pdf); assertTrue(text.contains("Paragraph 250")); assertTrue(text.contains("First")) }
        val subset = RichPdfEngine(context, root).execute(RichPdfRequest(Uri.fromFile(source).toString(), pages = "2"))
        assertEquals(1, subset.pages)
    }
    @Test fun wordParagraphsAndTablesProduceSearchablePdf() = runBlocking {
        val source = File(root, "document.docx")
        XWPFDocument().use { doc ->
            doc.createParagraph().createRun().apply { isBold = true; setText("Toolkit document fixture") }
            doc.createTable(2, 2).apply { getRow(0).getCell(0).text = "Item"; getRow(0).getCell(1).text = "Amount"; getRow(1).getCell(0).text = "Travel"; getRow(1).getCell(1).text = "42.5" }
            source.outputStream().use(doc::write)
        }
        val original = source.readBytes()
        val result = RichPdfEngine(context, root).execute(RichPdfRequest(Uri.fromFile(source).toString()))
        PDDocument.load(result.file).use { assertTrue(PDFTextStripper().getText(it).contains("Travel")) }
        assertArrayEquals(original, source.readBytes())
    }
    @Test fun pdfCreatesRealWordExcelAndPresentationPackages() = runBlocking {
        val source = File(root, "input.pdf")
        PDDocument().use { doc -> repeat(2) { i -> val page = PDPage(); doc.addPage(page); PDPageContentStream(doc, page).use { stream ->
            stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 14f); stream.newLineAtOffset(36f, 700f); stream.showText("Fixture page ${i + 1}"); stream.endText()
        } }; doc.save(source) }
        val engine = PdfOfficeEngine(context, root)
        val word = engine.execute(PdfOfficeRequest(Uri.fromFile(source).toString(), "docx", "2"))
        XWPFDocument(word.file.inputStream()).use { assertTrue(it.paragraphs.joinToString { p -> p.text }.contains("Fixture page 2")) }
        val excel = engine.execute(PdfOfficeRequest(Uri.fromFile(source).toString(), "xlsx"))
        XSSFWorkbook(excel.file).use { assertEquals(2, it.numberOfSheets); assertTrue(it.getSheetAt(0).getRow(0).getCell(0).stringCellValue.contains("Fixture")) }
        val slides = engine.execute(PdfOfficeRequest(Uri.fromFile(source).toString(), "pptx"))
        ZipFile(slides.file).use { zip -> assertNotNull(zip.getEntry("ppt/slides/slide2.xml")); assertNotNull(zip.getEntry("ppt/media/page2.png")) }
        val read = LocalDocumentReader(context).read(Uri.fromFile(slides.file).toString())
        assertEquals(2, read.sections.size); assertTrue(read.sections[0].html.contains("data:image/png"))
        val evidence = File(context.cacheDir, "generated-office-interop").apply { mkdirs() }
        word.file.copyTo(File(evidence, "result.docx"), true); excel.file.copyTo(File(evidence, "result.xlsx"), true); slides.file.copyTo(File(evidence, "result.pptx"), true)
        Unit
    }
    @Test fun epubUsesSpineOrderAndHtmlCannotLoadExternalContent() = runBlocking {
        val book = File(root, "fixture.epub")
        ZipOutputStream(book.outputStream()).use { zip ->
            fun put(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
            put("META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>")
            put("OPS/book.opf", "<package><manifest><item id=\"a\" href=\"a.xhtml\"/><item id=\"b\" href=\"b.xhtml\"/></manifest><spine><itemref idref=\"b\"/><itemref idref=\"a\"/></spine></package>")
            put("OPS/a.xhtml", "<html><head><title>A</title></head><body><p>Second chapter</p></body></html>")
            put("OPS/b.xhtml", "<html><head><title>B</title></head><body><p>First chapter</p><script>fetch('https://example.com')</script><img src=\"https://example.com/a.jpg\" onerror=\"alert(1)\"/></body></html>")
        }
        val doc = LocalDocumentReader(context).read(Uri.fromFile(book).toString())
        assertEquals("B", doc.sections[0].title); assertTrue(doc.sections[0].html.contains("First chapter"))
        assertFalse(doc.sections[0].html.contains("<script")); assertFalse(doc.sections[0].html.contains("https://example")); assertFalse(doc.sections[0].html.contains("onerror"))
    }
    @Test fun textChunksExposeContentBeyondInitialPreviewAndScanCorrectsPerspective() = runBlocking {
        val text = File(root, "large.txt").apply { writeText("a".repeat(65536) + "FINAL CONTENT") }
        val reader = LocalDocumentReader(context)
        assertTrue(reader.textChunk(Uri.fromFile(text).toString(), 0).second)
        assertEquals("FINAL CONTENT", reader.textChunk(Uri.fromFile(text).toString(), 1).first)
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        try {
            val out = ScanEngine(context).correct(bitmap, listOf(ScanPoint(.1f, .1f), ScanPoint(.9f, .2f), ScanPoint(.8f, .9f), ScanPoint(.2f, .8f)), false, File(root, "scan.jpg"))
            val image = android.graphics.BitmapFactory.decodeFile(out.path)
            try { assertTrue(image.width >= 64); assertTrue(android.graphics.Color.red(image.getPixel(image.width / 2, image.height / 2)) > 240) } finally { image.recycle() }
            try { ScanEngine(context).correct(bitmap, listOf(ScanPoint(0f, 0f), ScanPoint(1f, 1f), ScanPoint(1f, 0f), ScanPoint(0f, 1f)), false, File(root, "bad.jpg")); fail("Crossing corners accepted") } catch (_: IllegalArgumentException) { }
        } finally { bitmap.recycle() }
    }
    @Test fun legacyWordPresentationAndSpreadsheetFilesOpenLocally() = runBlocking {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        for (name in listOf("simple.doc", "basic_test_ppt_file.ppt")) {
            val file = File(root, name); assets.open("legacy-office/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
            val document = LocalDocumentReader(context).read(Uri.fromFile(file).toString())
            assertTrue(document.sections.isNotEmpty()); assertTrue(document.sections.any { org.jsoup.Jsoup.parse(it.html).text().length > 10 })
        }
        val xls = File(root, "legacy.xls")
        org.apache.poi.hssf.usermodel.HSSFWorkbook().use { workbook -> workbook.createSheet("Legacy").createRow(0).createCell(0).setCellValue("Legacy workbook value"); xls.outputStream().use(workbook::write) }
        val doc = LocalDocumentReader(context).read(Uri.fromFile(xls).toString())
        assertTrue(doc.sections.single().html.contains("Legacy workbook value"))
    }
    @Test fun markdownToPdfToWordPipelineSerializesAndExecutes() = runBlocking {
        val source = File(root, "workflow.md").apply { writeText("# Workflow fixture\n\nEditable pipeline output") }
        val template = WorkflowTemplate("Markdown to Word", listOf(WorkflowStep(WorkflowOperation.TEXT_PDF), WorkflowStep(WorkflowOperation.PDF_WORD)))
        val restored = WorkflowTemplate.fromJson(template.toJson())
        val result = WorkflowEngine(context, root).execute(restored, listOf(Uri.fromFile(source).toString()), emptyMap())
        assertEquals(DOCX_MIME, result.mime)
        XWPFDocument(result.file.inputStream()).use { assertTrue(it.paragraphs.joinToString { p -> p.text }.contains("Editable pipeline output")) }
    }
    @Test fun batchWorkflowsKeepIndependentOutputsAndCleanFailedRuns() = runBlocking {
        val inputs = listOf("First", "Second").map { word -> File(root, "$word.md").apply { writeText("# $word document\n\nSeparate output $word") } }
        val template = WorkflowTemplate("Batch text", listOf(WorkflowStep(WorkflowOperation.TEXT_PDF)))
        val result = WorkflowEngine(context, root).executeBatch(template, inputs.map { Uri.fromFile(it).toString() }, emptyMap())
        ZipFile(result.file).use { zip ->
            val files = zip.entries().asSequence().toList(); assertEquals(2, files.size)
            files.forEachIndexed { index, entry -> zip.getInputStream(entry).use { PDDocument.load(it).use { pdf -> assertTrue(PDFTextStripper().getText(pdf).contains(if (index == 0) "First" else "Second")) } } }
        }
        assertTrue(result.preview.isFile); result.delete()
        val before = root.listFiles()!!.map { it.name }.toSet()
        try { WorkflowEngine(context, root).executeBatch(template, listOf(Uri.fromFile(inputs.first()).toString(), Uri.fromFile(File(root, "missing.md")).toString()), emptyMap()); fail("Missing source accepted") } catch (_: java.io.FileNotFoundException) { }
        assertEquals(before, root.listFiles()!!.map { it.name }.toSet())
    }
    @Test fun legacyExcelProtectionAndPdfRoundTripRetainValues() = runBlocking {
        val source = File(root, "legacy-protection.xls")
        org.apache.poi.hssf.usermodel.HSSFWorkbook().use { workbook -> workbook.createSheet("Legacy").createRow(0).createCell(0).setCellValue("Legacy protected content"); source.outputStream().use(workbook::write) }
        val original = source.readBytes(); val engine = OfficeEngine(context, root)
        val locked = engine.execute(OfficeRequest(Uri.fromFile(source).toString(), OfficeAction.LOCK_FILE, newPassword = "legacy-fixture"))
        assertEquals(XLS_MIME, locked.mime)
        try { LocalDocumentReader(context).read(Uri.fromFile(locked.file).toString(), "wrong"); fail("Wrong password accepted") } catch (_: org.apache.poi.EncryptedDocumentException) { }
        val unlocked = engine.execute(OfficeRequest(Uri.fromFile(locked.file).toString(), OfficeAction.UNLOCK_FILE, password = "legacy-fixture"))
        val editing = engine.execute(OfficeRequest(Uri.fromFile(unlocked.file).toString(), OfficeAction.LOCK_EDITING, newPassword = "edit-fixture"))
        org.apache.poi.hssf.usermodel.HSSFWorkbook(editing.file.inputStream()).use { assertTrue(it.getSheetAt(0).protect); assertTrue(it.internalWorkbook.records.filterIsInstance<org.apache.poi.hssf.record.ProtectRecord>().single().protect) }
        val editable = engine.execute(OfficeRequest(Uri.fromFile(editing.file).toString(), OfficeAction.UNLOCK_EDITING))
        org.apache.poi.hssf.usermodel.HSSFWorkbook(editable.file.inputStream()).use { assertFalse(it.getSheetAt(0).protect); assertEquals("Legacy protected content", it.getSheetAt(0).getRow(0).getCell(0).stringCellValue) }
        val pdf = engine.execute(OfficeRequest(Uri.fromFile(unlocked.file).toString(), OfficeAction.TO_PDF))
        PDDocument.load(pdf.file).use { val text = PDFTextStripper().getText(it).replace(Regex("\\s+"), " "); assertTrue(text, text.contains("Legacy protected content")) }
        assertArrayEquals(original, source.readBytes())
        val evidence = File(context.cacheDir, "generated-office-interop").apply { mkdirs() }
        locked.file.copyTo(File(evidence, "locked.xls"), true); unlocked.file.copyTo(File(evidence, "unlocked.xls"), true)
        Unit
    }
    @Test fun presentationPdfPreservesSlideGeometryAndSearchablePositionedText() = runBlocking {
        val source = File(root, "positioned-slide.pptx")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(source.name).use { input -> source.outputStream().use { input.copyTo(it) } }
        val result = RichPdfEngine(context, root).execute(RichPdfRequest(Uri.fromFile(source).toString()))
        PDDocument.load(result.file).use { doc ->
            assertEquals(1, doc.numberOfPages); assertEquals(720f, doc.getPage(0).mediaBox.width, 1f); assertEquals(405f, doc.getPage(0).mediaBox.height, 1f)
            var x = 0f; var y = 0f
            val reader = object : PDFTextStripper() { override fun writeString(text: String, positions: MutableList<com.tom_roush.pdfbox.text.TextPosition>) { if (text.contains("Positioned")) { x = positions.first().xDirAdj; y = positions.first().yDirAdj }; super.writeString(text, positions) } }
            assertTrue(reader.getText(doc).contains("Positioned slide text")); assertTrue("Text x=$x", x in 430f..460f); assertTrue("Text y=$y", y in 250f..295f)
        }
        android.os.ParcelFileDescriptor.open(result.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> android.graphics.pdf.PdfRenderer(fd).use { renderer -> renderer.openPage(0).use { page ->
            val bitmap = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888)
            try { page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); val color = bitmap.getPixel(100, 100); assertTrue(android.graphics.Color.red(color) > 190); assertTrue(android.graphics.Color.green(color) < 60) } finally { bitmap.recycle() }
        } } }
    }
}
