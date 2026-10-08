package com.amaankhan.toolkit.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaankhan.toolkit.data.WorkflowStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class OfficeWorkflowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private lateinit var staging: File
    private lateinit var engine: OfficeEngine
    @Before fun setup() {
        root = File(context.cacheDir, "office-test-${System.nanoTime()}").apply { mkdirs() }
        staging = File(root, "stage").apply { mkdirs() }
        engine = OfficeEngine(context, staging)
    }
    @After fun cleanup() { root.deleteRecursively() }
    private fun uri(file: File) = Uri.fromFile(file).toString()
    private fun fixture(): File {
        val output = File(root, "sample.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook.createSheet("Budget").apply {
                createRow(0).apply { createCell(0).setCellValue("Item"); createCell(1).setCellValue("Amount") }
                createRow(1).apply { createCell(0).setCellValue("Travel"); createCell(1).setCellValue(1250.0) }
                createRow(2).createCell(1).cellFormula = "B2*2"
            }
            workbook.createSheet("Notes").createRow(0).createCell(0).setCellValue("Private notes")
            workbook.creationHelper.createFormulaEvaluator().evaluateAll()
            output.outputStream().use { workbook.write(it) }
        }
        return output
    }
    @Test fun encryptionRoundTripPreservesExactWorkbookBytesAndRejectsWrongPassword() = runBlocking {
        val original = fixture(); val originalBytes = original.readBytes()
        val locked = engine.execute(OfficeRequest(uri(original), OfficeAction.LOCK_FILE, newPassword = "correct-test-password"))
        assertEquals(XLSX_MIME, locked.mime); assertEquals(2, locked.pages)
        assertFalse(originalBytes.contentEquals(locked.file.readBytes()))
        val before = staging.listFiles()!!.size
        try { engine.execute(OfficeRequest(uri(locked.file), OfficeAction.UNLOCK_FILE, password = "wrong")); fail("Wrong password accepted") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("incorrect")) }
        assertEquals(before, staging.listFiles()!!.size)
        val unlocked = engine.execute(OfficeRequest(uri(locked.file), OfficeAction.UNLOCK_FILE, password = "correct-test-password"))
        assertArrayEquals(originalBytes, unlocked.file.readBytes()); assertArrayEquals(originalBytes, original.readBytes())
        XSSFWorkbook(unlocked.file).use { assertEquals("B2*2", it.getSheetAt(0).getRow(2).getCell(1).cellFormula) }
    }
    @Test fun editingProtectionPreservesCellsAndRemovesOnlyProtectionElements() = runBlocking {
        val original = fixture()
        val locked = engine.execute(OfficeRequest(uri(original), OfficeAction.LOCK_EDITING, newPassword = "edit-test-password"))
        XSSFWorkbook(locked.file).use { workbook ->
            assertTrue(workbook.getSheetAt(0).protect)
            assertTrue(workbook.isStructureLocked)
            assertTrue(workbook.validateWorkbookPassword("edit-test-password"))
            assertTrue(workbook.getSheetAt(0).validateSheetPassword("edit-test-password"))
            assertFalse(workbook.getSheetAt(0).validateSheetPassword("wrong"))
        }
        val unlocked = engine.execute(OfficeRequest(uri(locked.file), OfficeAction.UNLOCK_EDITING))
        XSSFWorkbook(unlocked.file).use { workbook ->
            assertFalse(workbook.isStructureLocked); assertFalse(workbook.getSheetAt(0).protect)
            assertEquals("Travel", workbook.getSheetAt(0).getRow(1).getCell(0).stringCellValue)
        }
        ZipFile(original).use { before -> ZipFile(unlocked.file).use { after ->
            for (entry in before.entries()) if (entry.name != "xl/workbook.xml" && !entry.name.startsWith("xl/worksheets/"))
                assertArrayEquals(entry.name, before.getInputStream(entry).readBytes(), after.getInputStream(after.getEntry(entry.name)).readBytes())
        } }
    }
    @Test fun conversionAppliesSelectedSheetsAndPagesAndRejectsInvalidRange() = runBlocking {
        val original = fixture()
        val result = engine.execute(OfficeRequest(uri(original), OfficeAction.TO_PDF, sheets = "2"))
        PDDocument.load(result.file).use { document ->
            assertEquals(1, document.numberOfPages)
            val text = PDFTextStripper().getText(document).replace(Regex("\\s+"), " ")
            assertTrue(text.contains("Private notes")); assertFalse(text.contains("Travel"))
        }
        val subset = engine.execute(OfficeRequest(uri(original), OfficeAction.TO_PDF, pages = "2", allPages = false))
        assertEquals(1, subset.pages)
        val before = staging.listFiles()!!.size
        try { engine.execute(OfficeRequest(uri(original), OfficeAction.TO_PDF, pages = "8", allPages = false)); fail("Invalid range accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(before, staging.listFiles()!!.size)
    }
    @Test fun excelUnlockConvertLockWorkflowProducesPasswordProtectedPdfAndCleansIntermediates() = runBlocking {
        val original = fixture()
        val locked = engine.execute(OfficeRequest(uri(original), OfficeAction.LOCK_FILE, newPassword = "workbook-secret"))
        val sourceCopy = File(root, "locked.xlsx").apply { locked.file.copyTo(this) }; locked.delete()
        val steps = listOf(WorkflowStep(WorkflowOperation.EXCEL_UNLOCK), WorkflowStep(WorkflowOperation.EXCEL_PDF), WorkflowStep(WorkflowOperation.PDF_LOCK))
        val template = WorkflowTemplate("Workbook to protected PDF", steps)
        val secrets = mapOf(steps[0].id to StepPasswords(current = "workbook-secret"), steps[2].id to StepPasswords(new = "pdf-secret"))
        assertFalse(template.toJson().toString().contains("secret"))
        val result = WorkflowEngine(context, staging).execute(template, listOf(uri(sourceCopy)), secrets)
        try { PDDocument.load(result.file).close(); fail("PDF opened without password") } catch (_: InvalidPasswordException) { }
        PDDocument.load(result.file, "pdf-secret").use { document ->
            assertEquals(2, document.numberOfPages); assertTrue(PDFTextStripper().getText(document).contains("Travel"))
        }
        assertEquals(2, staging.listFiles()!!.size)
        result.delete(); assertEquals(0, staging.listFiles()!!.size)
    }
    @Test fun workflowCompatibilityAndCancellationDoNotLeavePartialResults() = runBlocking {
        try { WorkflowTemplate("Invalid", listOf(WorkflowStep(WorkflowOperation.PDF_METADATA), WorkflowStep(WorkflowOperation.EXCEL_PDF))).validate(); fail("Incompatible chain accepted") } catch (_: IllegalArgumentException) { }
        val source = fixture()
        val template = WorkflowTemplate("Cancel", listOf(WorkflowStep(WorkflowOperation.EXCEL_PDF), WorkflowStep(WorkflowOperation.PDF_LOCK)))
        var reachedSecondStep = false
        try { WorkflowEngine(context, staging).execute(template, listOf(uri(source)), emptyMap()) {
            if (it.startsWith("Step 2/")) { reachedSecondStep = true; throw CancellationException("test") }
        }; fail("Cancellation ignored") }
        catch (_: CancellationException) { }
        assertTrue(reachedSecondStep)
        assertTrue(source.exists()); assertEquals(0, staging.listFiles()!!.size)
    }
    @Test fun editingRejectsDoctypeBeforeParsingWithoutKeepingAnOutput() = runBlocking {
        val original = fixture(); val hostile = File(root, "doctype.xlsx")
        ZipFile(original).use { zip -> java.util.zip.ZipOutputStream(hostile.outputStream()).use { output ->
            for (entry in zip.entries()) {
                output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (entry.name == "xl/workbook.xml") {
                    val text = bytes.toString(Charsets.UTF_8).replace(Regex("<\\?xml[^?]*\\?>"), "")
                    output.write(("<!DOCTYPE workbook [<!ENTITY probe SYSTEM 'file:///data/local/tmp/toolkit-test-entity'>]>" + text).toByteArray())
                } else output.write(bytes)
                output.closeEntry()
            }
        } }
        try { engine.execute(OfficeRequest(uri(hostile), OfficeAction.UNLOCK_EDITING)); fail("DTD accepted") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("document type")) }
        assertEquals(0, staging.listFiles()!!.size)
    }
    @Test fun independentEncryptedFixtureOpensAndProducesAnInteropArtifact() = runBlocking {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val source = File(root, "independent.xlsx")
        assets.open("office-interop/encrypted.xlsx").use { input -> source.outputStream().use { input.copyTo(it) } }
        val expected = assets.open("office-interop/original.xlsx").use { it.readBytes() }
        val unlocked = engine.execute(OfficeRequest(uri(source), OfficeAction.UNLOCK_FILE, password = "toolkit-interop-test"))
        assertArrayEquals(expected, unlocked.file.readBytes())
        val locked = engine.execute(OfficeRequest(uri(unlocked.file), OfficeAction.LOCK_FILE, newPassword = "toolkit-interop-test"))
        // Synthetic artifacts only, retained for an independent msoffcrypto/openpyxl check.
        val evidence = File(context.cacheDir, "office-interop-test").apply { mkdirs() }
        locked.file.copyTo(File(evidence, "encrypted.xlsx"), overwrite = true)
        locked.preview.copyTo(File(evidence, "preview.pdf"), overwrite = true)
        File(evidence, "original.xlsx").writeBytes(expected)
    }
    @Test fun longCellContinuesAcrossPagesWithoutDroppingItsLastLine() = runBlocking {
        val source = File(root, "long.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook.createSheet("Long cell").createRow(0).createCell(0).setCellValue((1..120).joinToString("\n") { "TOKEN$it" })
            source.outputStream().use { workbook.write(it) }
        }
        val result = engine.execute(OfficeRequest(uri(source), OfficeAction.TO_PDF))
        assertTrue(result.pages > 1)
        PDDocument.load(result.file).use { doc ->
            val text = PDFTextStripper().getText(doc)
            (1..120).forEach { assertTrue("Missing cell line $it", text.contains("TOKEN$it")) }
        }
    }
    @Test fun savedImageWorkflowUsesLayoutOptionsAndSelectedPagesInItsImageOutput() = runBlocking {
        val inputs = listOf(android.graphics.Color.RED, android.graphics.Color.BLUE).mapIndexed { index, color ->
            File(root, "source-$index.png").also { file ->
                android.graphics.Bitmap.createBitmap(60, 80, android.graphics.Bitmap.Config.ARGB_8888).apply {
                    eraseColor(color); file.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; recycle()
                }
            }
        }
        val template = WorkflowTemplate("Images to selected page", listOf(
            WorkflowStep(WorkflowOperation.IMAGES_PDF, paper = "LETTER", orientation = "PORTRAIT", marginPx = "8"),
            WorkflowStep(WorkflowOperation.PDF_EXTRACT, pages = "2"),
            WorkflowStep(WorkflowOperation.PDF_IMAGES, imageEdge = 1200)
        ))
        val restored = WorkflowTemplate.fromJson(template.toJson())
        val result = WorkflowEngine(context, staging).execute(restored, inputs.map(::uri), emptyMap())
        assertEquals("application/zip", result.mime); assertEquals(1, result.images.size)
        android.graphics.BitmapFactory.decodeFile(result.images.single().path).let { bitmap ->
            assertEquals(927, bitmap.width); assertEquals(1200, bitmap.height)
            assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)); bitmap.recycle()
        }
        assertTrue(inputs.all { it.exists() })
        result.delete(); assertEquals(0, staging.listFiles()!!.size)
    }
}
