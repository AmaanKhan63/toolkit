package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ExtraPdfEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    @Before fun setup() { root = File(context.cacheDir, "extra-test-${System.nanoTime()}").apply { mkdirs() } }
    @After fun cleanup() { root.deleteRecursively() }
    private fun fixture(): File {
        val file = File(root, "source.pdf")
        PDDocument().use { doc ->
            repeat(3) { index -> val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { canvas ->
                    canvas.beginText(); canvas.setFont(PDType1Font.HELVETICA, 18f); canvas.newLineAtOffset(40f, 700f); canvas.showText("Searchable page ${index + 1}"); canvas.endText()
                    val image = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
                    try { image.eraseColor(android.graphics.Color.rgb(80 + index * 20, 130, 200)); canvas.drawImage(LosslessFactory.createFromImage(doc, image), 40f, 300f, 400f, 300f) } finally { image.recycle() }
                }
            }
            doc.documentInformation.title = "Synthetic metadata"; doc.save(file)
        }; return file
    }
    @Test fun compressionPreservesTextAndPagesAndOriginal() = runBlocking {
        val source = fixture(); val original = source.readBytes()
        val result = ExtraPdfEngine(context, root).execute(ExtraPdfRequest("COMPRESS", Uri.fromFile(source).toString(), options = JSONObject().put("edge", 800)))
        PDDocument.load(result.file).use { doc -> assertEquals(3, doc.numberOfPages); val text = PDFTextStripper().getText(doc); repeat(3) { assertTrue(text.contains("Searchable page ${it + 1}")) } }
        assertArrayEquals(original, source.readBytes())
    }
    @Test fun numberingAddsRealTextOnlyOnSelectedPages() = runBlocking {
        val source = fixture()
        val result = ExtraPdfEngine(context, root).execute(ExtraPdfRequest("STAMP", Uri.fromFile(source).toString(), options = JSONObject().put("pages", "2").put("text", "Page {page} of {total}").put("opacity", 1)))
        PDDocument.load(result.file).use { doc -> val text = PDFTextStripper().getText(doc); assertTrue(text.contains("Page 2 of 3")); assertFalse(text.contains("Page 1 of 3")) }
    }
    @Test fun splitRangesAreSeparateValidDocumentsAndEmbeddedImagesAreReal() = runBlocking {
        val source = fixture(); val engine = ExtraPdfEngine(context, root)
        val result = engine.execute(ExtraPdfRequest("SPLIT", Uri.fromFile(source).toString(), options = JSONObject().put("mode", "Ranges").put("groups", "1; 2-3")))
        ZipFile(result.file).use { zip -> assertEquals(2, zip.size()); zip.getInputStream(zip.getEntry("part-2.pdf")).use { input -> PDDocument.load(input).use { assertEquals(2, it.numberOfPages) } } }
        val images = engine.execute(ExtraPdfRequest("EMBEDDED", Uri.fromFile(source).toString(), options = JSONObject().put("pages", "2")))
        assertEquals(1, images.images.size)
        val bitmap = android.graphics.BitmapFactory.decodeFile(images.images.single().path)
        try { assertEquals(1600, bitmap.width); assertEquals(1200, bitmap.height) } finally { bitmap.recycle() }
        assertTrue(engine.metadata(Uri.fromFile(source).toString(), "").contains("Synthetic metadata"))
    }
    @Test fun mergeSubsetsInsertPagesCropEdgesAndPermissionsAreApplied() = runBlocking {
        val first = fixture(); val second = File(root, "insert.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(300f, 400f))); doc.save(second) }
        val engine = PdfToolEngine(context, root)
        val merged = engine.execute(PdfRequest(PdfOperation.MERGE, listOf(Uri.fromFile(first).toString(), Uri.fromFile(second).toString()), sourcePages = listOf("2", "1")))
        PDDocument.load(merged.file).use { doc -> assertEquals(2, doc.numberOfPages); assertTrue(PDFTextStripper().getText(doc).contains("Searchable page 2")) }
        val inserted = engine.execute(PdfRequest(PdfOperation.ORGANIZE, listOf(Uri.fromFile(first).toString(), Uri.fromFile(second).toString()), insertAfter = 1))
        PDDocument.load(inserted.file).use { doc -> assertEquals(4, doc.numberOfPages); assertEquals(300f, doc.getPage(1).mediaBox.width, .1f) }
        val cropped = engine.execute(PdfRequest(PdfOperation.CROP, listOf(Uri.fromFile(second).toString()), cropEdges = MarginsPx(top = 8, right = 16, bottom = 24, left = 32)))
        PDDocument.load(cropped.file).use { doc -> val box = doc.getPage(0).cropBox; assertEquals(264f, box.width, .1f); assertEquals(376f, box.height, .1f); assertEquals(24f, box.lowerLeftX, .1f); assertEquals(18f, box.lowerLeftY, .1f) }
        val locked = engine.execute(PdfRequest(PdfOperation.PROTECT, listOf(Uri.fromFile(first).toString()), newPassword = "fixture-pass", allowPrint = false, allowModify = false, allowExtract = false))
        PDDocument.load(locked.file, "fixture-pass").use { doc -> assertFalse(doc.currentAccessPermission.canPrint()); assertFalse(doc.currentAccessPermission.canModify()); assertFalse(doc.currentAccessPermission.canExtractContent()) }
    }
}
