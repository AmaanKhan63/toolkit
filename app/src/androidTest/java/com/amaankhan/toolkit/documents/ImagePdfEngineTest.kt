package com.amaankhan.toolkit.documents

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePdfEngineTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var root: File
    private lateinit var staging: File
    private lateinit var engine: ImagePdfEngine
    @Before fun setUp() {
        root = File(context.cacheDir, "engine-test-${System.nanoTime()}").apply { mkdirs() }
        staging = File(root, "staging")
        engine = ImagePdfEngine(context.contentResolver, staging)
    }
    @After fun tearDown() { root.deleteRecursively() }
    private fun image(name: String, width: Int, height: Int, color: Int, jpeg: Boolean = false): File {
        val file = File(root, name)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            file.outputStream().use { compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
            recycle()
        }
        return file
    }
    private fun selected(file: File) = SelectedImage(Uri.fromFile(file).toString(), file.name)
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    @Test fun realPdfPreservesInputOrderPageSizeAndOriginals() = runBlocking {
        val red = image("red.png", 100, 200, Color.RED)
        val blue = image("blue.png", 200, 100, Color.BLUE)
        val hashes = listOf(hash(red), hash(blue))
        val progress = mutableListOf<Int>()
        val output = engine.create(listOf(selected(blue), selected(red)), PdfOptions()) { done, _ -> progress += done }
        assertTrue(output.readBytes().take(5).toByteArray().decodeToString().startsWith("%PDF-"))
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                assertEquals(2, pdf.pageCount)
                listOf(Color.BLUE, Color.RED).forEachIndexed { index, expected ->
                    pdf.openPage(index).use { page ->
                        assertEquals(if (index == 0) 842 else 595, page.width)
                        assertEquals(if (index == 0) 595 else 842, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(expected, bitmap.getPixel(page.width / 2, page.height / 2))
                        assertEquals(Color.WHITE, bitmap.getPixel(2, 2))
                        bitmap.recycle()
                    }
                }
            }
        }
        assertEquals(listOf(0, 1, 1, 2), progress)
        assertEquals(hashes, listOf(hash(red), hash(blue)))
    }

    @Test fun exifRotationChangesPageOrientation() = runBlocking {
        val file = image("rotated.jpg", 200, 100, Color.GREEN, jpeg = true)
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val output = engine.create(listOf(selected(file)), PdfOptions()) { _, _ -> }
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { assertEquals(595, it.width); assertEquals(842, it.height) }
        }
    }

    @Test fun allExifTransformsMatchCornerMapping() {
        val file = File(root, "corners.jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
            paint.color = color
            canvas.drawRect((index % 2 * 40).toFloat(), (index / 2 * 20).toFloat(), (index % 2 * 40 + 40).toFloat(), (index / 2 * 20 + 20).toFloat(), paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }; bitmap.recycle()
        val expected = mapOf(1 to 0, 2 to 1, 3 to 3, 4 to 2, 5 to 0, 6 to 2, 7 to 3, 8 to 1)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        expected.forEach { (orientation, corner) ->
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            val decoded = ImagePdfEngine.decodeImage(context.contentResolver, Uri.fromFile(file), 200)
            val actual = decoded.getPixel(decoded.width / 4, decoded.height / 4)
            val expectedColor = colors[corner]
            assertTrue("Orientation $orientation red", kotlin.math.abs(Color.red(actual) - Color.red(expectedColor)) < 15)
            assertTrue("Orientation $orientation green", kotlin.math.abs(Color.green(actual) - Color.green(expectedColor)) < 15)
            assertTrue("Orientation $orientation blue", kotlin.math.abs(Color.blue(actual) - Color.blue(expectedColor)) < 15)
            assertEquals(if (orientation >= 5) 40 else 80, decoded.width)
            decoded.recycle()
        }
    }

    @Test fun corruptInputLeavesNoPartialPdf() = runBlocking {
        val file = File(root, "broken.png").apply { writeText("not an image") }
        try { engine.create(listOf(selected(file)), PdfOptions()) { _, _ -> }; fail("Should reject invalid data") }
        catch (_: IOException) { assertTrue(staging.listFiles().isNullOrEmpty()) }
    }

    @Test fun cancelledConversionRemovesStagingAndPreservesInput() = runBlocking {
        val file = image("source.png", 120, 240, Color.RED)
        val before = hash(file)
        val conversion = launch {
            val caller: Job = coroutineContext[Job]!!
            engine.create(listOf(selected(file), selected(file)), PdfOptions()) { page, _ -> if (page == 1) caller.cancel() }
        }
        conversion.join()
        assertTrue(conversion.isCancelled)
        assertTrue(staging.listFiles().isNullOrEmpty())
        assertEquals(before, hash(file))
    }
}
