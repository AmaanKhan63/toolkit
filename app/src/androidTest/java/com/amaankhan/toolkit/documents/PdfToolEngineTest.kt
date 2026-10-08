package com.amaankhan.toolkit.documents

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfToolEngineTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var root: File
    private lateinit var staging: File
    private lateinit var engine: PdfToolEngine
    @Before fun setup() {
        root = File(context.cacheDir, "pdf-operation-test-${System.nanoTime()}").apply { mkdirs() }
        staging = File(root, "stage").apply { mkdirs() }
        PDFBoxResourceLoader.init(context)
        engine = PdfToolEngine(context, staging)
    }
    @After fun cleanup() { root.deleteRecursively() }
    private fun fixture(name: String, vararg labels: String): File {
        val file = File(root, "$name.pdf")
        PDDocument().use { doc ->
            labels.forEach { label ->
                val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 16f); stream.newLineAtOffset(60f, 700f); stream.showText(label); stream.endText()
                }
            }
            doc.documentInformation.author = "Private author"
            doc.save(file)
        }
        return file
    }
    private fun uri(file: File) = Uri.fromFile(file).toString()
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun text(file: File) = PDDocument.load(file).use { PDFTextStripper().getText(it) }
    @Test fun mergePreservesOrderedContentAndOriginals() = runBlocking {
        val first = fixture("first", "Alpha", "Beta"); val second = fixture("second", "Gamma")
        val hashes = listOf(hash(first), hash(second))
        val result = engine.execute(PdfRequest(PdfOperation.MERGE, listOf(uri(second), uri(first))))
        assertEquals(3, result.pages)
        val text = text(result.file)
        assertTrue(text.indexOf("Gamma") < text.indexOf("Alpha")); assertTrue(text.indexOf("Alpha") < text.indexOf("Beta"))
        assertEquals(hashes, listOf(hash(first), hash(second)))
    }
    @Test fun selectedPagesAndExplicitOrderProduceDifferentOutputs() = runBlocking {
        val source = fixture("pages", "Alpha", "Beta", "Gamma")
        val selected = engine.execute(PdfRequest(PdfOperation.EXTRACT, listOf(uri(source)), "3, 1, 1", false))
        assertEquals(2, selected.pages); assertFalse(text(selected.file).contains("Beta"))
        val ordered = engine.execute(PdfRequest(PdfOperation.ORGANIZE, listOf(uri(source)), "3, 1, 1", false, rotation = 90))
        assertEquals(3, ordered.pages)
        PDDocument.load(ordered.file).use { document ->
            // Validate geometry separately: text extraction from a rotated page can split words.
            document.pages.forEach { page -> assertEquals(90, page.rotation); page.rotation = 0 }
            listOf("Gamma", "Alpha", "Alpha").forEachIndexed { index, label ->
                val actual = PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }.getText(document).trim()
                assertEquals("Unexpected content at output page ${index + 1}", label, actual)
            }
        }
    }
    @Test fun passwordRoundTripRejectsWrongPasswordAndValidatesEncryptedOutput() = runBlocking {
        val source = fixture("private", "Secret content"); val originalHash = hash(source)
        val locked = engine.execute(PdfRequest(PdfOperation.PROTECT, listOf(uri(source)), newPassword = "correct-test-password"))
        try { PDDocument.load(locked.file).close(); fail("Encrypted PDF opened without a password") } catch (_: InvalidPasswordException) { }
        PDDocument.load(locked.file, "correct-test-password").use { assertTrue(it.isEncrypted); assertEquals(1, it.numberOfPages) }
        assertTrue(text(locked.preview).contains("Secret content"))
        val before = staging.listFiles()!!.size
        try { engine.execute(PdfRequest(PdfOperation.UNLOCK, listOf(uri(locked.file)), password = "wrong")); fail("Wrong password accepted") } catch (_: InvalidPasswordException) { }
        assertEquals(before, staging.listFiles()!!.size)
        val unlocked = engine.execute(PdfRequest(PdfOperation.UNLOCK, listOf(uri(locked.file)), password = "correct-test-password"))
        PDDocument.load(unlocked.file).use { assertFalse(it.isEncrypted) }
        assertTrue(text(unlocked.file).contains("Secret content")); assertEquals(originalHash, hash(source))
    }
    @Test fun cropMetadataAndTextOutputsAreReal() = runBlocking {
        val source = fixture("source", "Readable text")
        val crop = engine.execute(PdfRequest(PdfOperation.CROP, listOf(uri(source)), cropPx = 32))
        PDDocument.load(crop.file).use { assertEquals(24f, it.getPage(0).cropBox.lowerLeftX, .01f); assertEquals(564f, it.getPage(0).cropBox.width, .01f) }
        val clean = engine.execute(PdfRequest(PdfOperation.REMOVE_METADATA, listOf(uri(source))))
        PDDocument.load(clean.file).use { assertNull(it.documentInformation.author); assertNull(it.documentCatalog.metadata) }
        val extracted = engine.execute(PdfRequest(PdfOperation.TEXT, listOf(uri(source))))
        assertEquals("text/plain", extracted.mime); assertTrue(extracted.file.readText().contains("Readable text"))
    }
    @Test fun renderedImagesUseSelectedSourcePagesAndChosenFormat() = runBlocking {
        val source = fixture("gallery", "First", "Second", "Third")
        val result = engine.execute(PdfRequest(PdfOperation.IMAGES, listOf(uri(source)), "1, 3", false, imageFormat = "JPEG", imageEdge = 1200))
        assertEquals(listOf(1, 3), result.sourcePages)
        assertEquals(2, result.images.size)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(result.images.first().path, bounds)
        assertEquals(1200, maxOf(bounds.outWidth, bounds.outHeight))
        assertEquals("image/jpeg", bounds.outMimeType)
        java.util.zip.ZipFile(result.file).use { zip ->
            assertEquals(listOf("page-001.jpg", "page-003.jpg"), zip.entries().asSequence().map { it.name }.toList())
        }
        result.delete(); assertFalse(result.file.exists()); assertTrue(result.images.none { it.exists() })
    }
    @Test fun cancellationRemovesStagedOutput() = runBlocking {
        val source = fixture("cancel", "A", "B")
        val job = launch {
            engine.execute(PdfRequest(PdfOperation.MERGE, listOf(uri(source), uri(source)))) { cancel() }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, staging.listFiles()!!.size)
    }
}
