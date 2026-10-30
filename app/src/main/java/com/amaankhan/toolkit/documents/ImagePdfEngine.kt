package com.amaankhan.toolkit.documents

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SelectedImage(val uri: String, val name: String)

class ImagePdfEngine(private val resolver: ContentResolver, private val stagingDirectory: File) {
    suspend fun create(images: List<SelectedImage>, options: PdfOptions, progress: (Int, Int) -> Unit): File {
        var staged: File? = null
        try {
            return withContext(Dispatchers.IO) {
                require(images.size in 1..PdfLayout.MAX_IMAGES) { "Choose between 1 and ${PdfLayout.MAX_IMAGES} images." }
                if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) throw IOException("Temporary storage is unavailable.")
                if (stagingDirectory.usableSpace < 64L * 1024 * 1024) throw IOException("Free at least 64 MB of storage and try again.")
                val output = File.createTempFile("images-", ".pdf", stagingDirectory)
                staged = output
                val scratch = Files.createTempDirectory(stagingDirectory.toPath(), "images-work-").toFile()
                var completed = false
                try {
                    // All accumulated image/content streams use private disk storage. Only the
                    // current bounded bitmap and its scanline buffers stay in image memory.
                    PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(scratch)).use { document ->
                        images.forEachIndexed { index, image ->
                            currentCoroutineContext().ensureActive()
                            progress(index, images.size)
                            val decoded = decodeImageData(resolver, Uri.parse(image.uri), PdfLayout.decodeEdge(images.size))
                            val bitmap = decoded.bitmap
                            try {
                                val layout = PdfLayout.fit(bitmap.width, bitmap.height, options)
                                val page = PDPage(PDRectangle(layout.width.toFloat(), layout.height.toFloat()))
                                document.addPage(page)
                                val embedded = embedImage(document, bitmap, decoded.jpeg)
                                PDPageContentStream(document, page).use { content ->
                                    content.setNonStrokingColor(1f, 1f, 1f)
                                    content.addRect(0f, 0f, layout.width.toFloat(), layout.height.toFloat())
                                    content.fill()
                                    // Layout uses top-left coordinates; PDF uses bottom-left.
                                    content.drawImage(embedded, layout.left, layout.height - layout.top - layout.imageHeight, layout.imageWidth, layout.imageHeight)
                                }
                            } finally { bitmap.recycle() }
                            progress(index + 1, images.size)
                        }
                        val jobContext = currentCoroutineContext()
                        jobContext.ensureActive()
                        output.outputStream().buffered().use { destination ->
                            document.save(object : FilterOutputStream(destination) {
                                override fun write(value: Int) { jobContext.ensureActive(); out.write(value) }
                                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                    jobContext.ensureActive()
                                    out.write(bytes, offset, length)
                                }
                            })
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    // A completed file must also reopen as a PDF with every expected page.
                    ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            if (renderer.pageCount != images.size) throw IOException("The PDF could not be verified. Try again.")
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    completed = true
                    output
                } finally {
                    scratch.deleteRecursively()
                    if (!completed) output.delete()
                }
            }
        } catch (failure: Throwable) {
            // Includes cancellation during the dispatch back to the caller after the file was written.
            withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
            throw failure
        }
    }

    private suspend fun embedImage(document: PDDocument, bitmap: Bitmap, jpeg: Boolean): PDImageXObject {
        val image = PDImageXObject(document).apply {
            width = bitmap.width
            height = bitmap.height
            bitsPerComponent = 8
            colorSpace = PDDeviceRGB.INSTANCE
            interpolate = true
        }
        if (jpeg) {
            // Keep photos compressed without buffering the JPEG in a byte array. Decoding has
            // already applied the size limit and EXIF transform, so these dimensions are final.
            image.cosObject.setItem(COSName.FILTER, COSName.DCT_DECODE)
            val jobContext = currentCoroutineContext()
            jobContext.ensureActive()
            image.cosObject.createRawOutputStream().use { destination ->
                val encoded = object : FilterOutputStream(destination) {
                    override fun write(value: Int) { jobContext.ensureActive(); out.write(value) }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        jobContext.ensureActive()
                        out.write(bytes, offset, length)
                    }
                }
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, encoded)) throw IOException("An image could not be encoded. Try another image.")
            }
            jobContext.ensureActive()
            return image
        }
        val pixels = IntArray(bitmap.width)
        val rgb = ByteArray(bitmap.width * 3)
        image.cosObject.createOutputStream(COSName.FLATE_DECODE).use { encoded ->
            for (row in 0 until bitmap.height) {
                currentCoroutineContext().ensureActive()
                bitmap.getPixels(pixels, 0, bitmap.width, 0, row, bitmap.width, 1)
                var offset = 0
                for (pixel in pixels) {
                    val alpha = Color.alpha(pixel)
                    if (alpha == 255) {
                        rgb[offset++] = Color.red(pixel).toByte()
                        rgb[offset++] = Color.green(pixel).toByte()
                        rgb[offset++] = Color.blue(pixel).toByte()
                    } else {
                        // Match the former white canvas, including partially transparent PNGs.
                        val white = 255 * (255 - alpha) + 127
                        rgb[offset++] = ((Color.red(pixel) * alpha + white) / 255).toByte()
                        rgb[offset++] = ((Color.green(pixel) * alpha + white) / 255).toByte()
                        rgb[offset++] = ((Color.blue(pixel) * alpha + white) / 255).toByte()
                    }
                }
                encoded.write(rgb)
            }
        }
        return image
    }

    private data class DecodedImage(val bitmap: Bitmap, val jpeg: Boolean)

    companion object {
        fun decodeImage(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap = decodeImageData(resolver, uri, maxEdge).bitmap

        private fun decodeImageData(resolver: ContentResolver, uri: Uri, maxEdge: Int): DecodedImage {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); Unit }
                ?: throw IOException("An image is no longer accessible. Select it again.")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType !in setOf("image/png", "image/jpeg"))
                throw IOException("One of the selected files is not a readable PNG or JPEG image.")
            val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: throw IOException("An image is no longer accessible. Select it again.")
            val decode = BitmapFactory.Options().apply {
                inSampleSize = PdfLayout.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
                ?: throw IOException("An image could not be decoded. Select another file.")
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val jpeg = bounds.outMimeType == "image/jpeg"
            if (matrix.isIdentity) return DecodedImage(bitmap, jpeg)
            return try { DecodedImage(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true), jpeg) }
            finally { bitmap.recycle() }
        }
    }
}
