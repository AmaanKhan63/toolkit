package com.amaankhan.toolkit.documents

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SelectedImage(val uri: String, val name: String)

class ImagePdfEngine(private val resolver: ContentResolver, private val stagingDirectory: File) {
    suspend fun create(images: List<SelectedImage>, options: PdfOptions, progress: (Int, Int) -> Unit): File {
        var staged: File? = null
        try { return withContext(Dispatchers.IO) {
        require(images.size in 1..PdfLayout.MAX_IMAGES) { "Choose between 1 and ${PdfLayout.MAX_IMAGES} images." }
        if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) throw IOException("Temporary storage is unavailable.")
        if (stagingDirectory.usableSpace < 64L * 1024 * 1024) throw IOException("Free at least 64 MB of storage and try again.")
        val output = File.createTempFile("images-", ".pdf", stagingDirectory)
        staged = output
        var completed = false
        try {
            val document = PdfDocument()
            try {
                images.forEachIndexed { index, image ->
                    currentCoroutineContext().ensureActive()
                    progress(index, images.size)
                    val bitmap = decodeImage(resolver, Uri.parse(image.uri), PdfLayout.decodeEdge(images.size))
                    try {
                        val layout = PdfLayout.fit(bitmap.width, bitmap.height, options)
                        val page = document.startPage(PdfDocument.PageInfo.Builder(layout.width, layout.height, index + 1).create())
                        try {
                            page.canvas.drawColor(Color.WHITE)
                            page.canvas.drawBitmap(bitmap, null, RectF(layout.left, layout.top, layout.left + layout.imageWidth, layout.top + layout.imageHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        } finally { document.finishPage(page) }
                    } finally { bitmap.recycle() }
                    progress(index + 1, images.size)
                }
                currentCoroutineContext().ensureActive()
                output.outputStream().use { document.writeTo(it) }
            } finally { document.close() }
            currentCoroutineContext().ensureActive()
            // A completed file must also reopen as a PDF with every expected page.
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount != images.size) throw IOException("The PDF could not be verified. Try again.")
                }
            }
            completed = true
            output
        } finally { if (!completed) output.delete() }
        } } catch (failure: Throwable) {
            // Includes cancellation during the dispatch back to the caller after the file was written.
            withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
            throw failure
        }
    }

    companion object {
        fun decodeImage(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
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
            if (matrix.isIdentity) return bitmap
            return try { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) }
            finally { bitmap.recycle() }
        }
    }
}
