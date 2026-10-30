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

    @Test fun texturedJpegStaysCompactAndPreservesExifRotationWhenRendered() = runBlocking {
        // High-frequency texture exposes accidental expansion of JPEG photos into lossless RGB.
        // The filename deliberately does not identify the format: selection is MIME-based.
        val file = File(root, "textured-photo.data")
        val source = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            val random = java.util.Random(42)
            val row = IntArray(640)
            for (y in 0 until 480) {
                for (x in row.indices) row[x] = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                source.setPixels(row, 0, 640, 0, y, 640, 1)
            }
            val canvas = android.graphics.Canvas(source)
            canvas.drawRect(0f, 0f, 160f, 120f, android.graphics.Paint().apply { color = Color.RED })
            canvas.drawRect(0f, 360f, 160f, 480f, android.graphics.Paint().apply { color = Color.BLUE })
            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { source.recycle() }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val original = hash(file)
        val output = engine.create(listOf(selected(file)), PdfOptions()) { _, _ -> }
        assertTrue("A 640 by 480 JPEG photo should stay below 400 KB in the PDF; got ${output.length()}", output.length() < 400_000)
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { page ->
                assertEquals(595, page.width)
                assertEquals(842, page.height)
                val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                try {
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val topLeft = rendered.getPixel(90, 150)
                    val topRight = rendered.getPixel(500, 150)
                    assertTrue(Color.blue(topLeft) > 220 && Color.red(topLeft) < 30)
                    assertTrue(Color.red(topRight) > 220 && Color.blue(topRight) < 30)
                } finally { rendered.recycle() }
            }
        }
        assertEquals(original, hash(file))
        assertEquals(listOf(output), staging.listFiles()?.toList())
    }

    @Test fun fiveHundredImagesRenderInSelectionOrderAndReleaseScratchStorage() = runBlocking {
        val colors = List(500) { index -> Color.rgb(index % 256, index / 256 * 127, 255 - index % 256) }
        val inputs = colors.mapIndexed { index, color -> image("page-$index.png", 12, 24, color) }
        var completedPages = 0
        var diskStorageObserved = false
        val output = engine.create(inputs.map(::selected), PdfOptions()) { done, total ->
            assertEquals(500, total)
            completedPages = done
            if (done > 0) {
                diskStorageObserved = diskStorageObserved || staging.walkTopDown().any { it.isFile && it.extension != "pdf" && it.length() > 0 }
            }
        }
        assertEquals(500, completedPages)
        assertTrue("Image content must spill to temporary disk storage during assembly", diskStorageObserved)
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertEquals(500, pdf.pageCount)
            colors.forEachIndexed { index, expected ->
                pdf.openPage(index).use { page ->
                    val rendered = Bitmap.createBitmap(60, 85, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals("Selected image order at page ${index + 1}", expected, rendered.getPixel(30, 42))
                        assertEquals(Color.WHITE, rendered.getPixel(0, 0))
                    } finally { rendered.recycle() }
                }
            }
        }
        assertEquals(listOf(output), staging.listFiles()?.toList())
    }

    @Test fun transparentImagesAndAsymmetricMarginsRenderOverWhite() = runBlocking {
        val file = File(root, "transparent.png")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawRect(50f, 0f, 100f, 100f, android.graphics.Paint().apply { color = Color.BLUE })
            canvas.drawRect(0f, 50f, 50f, 100f, android.graphics.Paint().apply { color = Color.argb(128, 255, 0, 0) })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        val output = engine.create(listOf(selected(file)), PdfOptions(orientation = Orientation.PORTRAIT, marginsPx = MarginsPx(96, 0, 0, 96))) { _, _ -> }
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { page ->
                val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                try {
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertEquals(Color.WHITE, rendered.getPixel(100, 300))
                    assertEquals(Color.rgb(255, 127, 127), rendered.getPixel(100, 600))
                    assertEquals(Color.BLUE, rendered.getPixel(500, 300))
                    assertEquals(Color.WHITE, rendered.getPixel(500, 190))
                    assertEquals(Color.BLUE, rendered.getPixel(500, 200))
                } finally { rendered.recycle() }
            }
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

    @Test fun cancellationAfterFormerLimitRemovesDiskBackedPagesAndOutput() = runBlocking {
        val file = image("cancel-large-batch.png", 1000, 1500, Color.BLUE)
        val before = hash(file)
        var completedPages = 0
        var diskStorageObserved = false
        val conversion = launch {
            val caller: Job = coroutineContext[Job]!!
            engine.create(List(500) { selected(file) }, PdfOptions()) { page, _ ->
                completedPages = page
                if (page == 25) {
                    diskStorageObserved = staging.walkTopDown().any { it.isFile && it.extension != "pdf" && it.length() > 0 }
                    caller.cancel()
                }
            }
        }
        conversion.join()
        assertEquals(25, completedPages)
        assertTrue(diskStorageObserved)
        assertTrue(conversion.isCancelled)
        assertTrue("Scratch files and partial output must both be removed", staging.listFiles().isNullOrEmpty())
        assertEquals(before, hash(file))
    }

    @Test fun invalidImageAfterFormerLimitRemovesDiskBackedPagesAndOutput() = runBlocking {
        val valid = image("valid.png", 12, 24, Color.RED)
        val corrupt = File(root, "broken-later.png").apply { writeText("not an image") }
        var completedPages = 0
        try {
            engine.create(List(21) { selected(valid) } + selected(corrupt), PdfOptions()) { done, _ -> completedPages = done }
            fail("Should reject the corrupt image")
        } catch (_: IOException) {
            assertEquals(21, completedPages)
            assertTrue(staging.listFiles().isNullOrEmpty())
        }
    }
}
