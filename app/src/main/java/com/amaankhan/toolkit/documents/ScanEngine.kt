package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.hypot

data class ScanPoint(val x: Float, val y: Float)
class ScanEngine(private val context: Context) {
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        fun stream() = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("Choose the image again.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid image." }
        var sample = 1; while (bounds.outWidth / sample > 2800 || bounds.outHeight / sample > 2800) sample *= 2
        val bitmap = stream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Unable to decode image.")
        val orientation = stream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f); ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f); ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f); ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        } }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
    suspend fun correct(bitmap: Bitmap, points: List<ScanPoint>, grayscale: Boolean, target: File): File = withContext(Dispatchers.IO) {
        require(points.size == 4 && points.all { it.x in 0f..1f && it.y in 0f..1f }) { "Keep all four corners inside the image." }
        val cross = points.indices.map { i -> val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]; (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) }
        require(cross.all { it > .001f }) { "Corners must surround the document without crossing." }
        val p = points.map { PointF(it.x * bitmap.width, it.y * bitmap.height) }
        fun length(a: Int, b: Int) = hypot(p[a].x - p[b].x, p[a].y - p[b].y)
        var width = maxOf(length(0, 1), length(3, 2)); var height = maxOf(length(0, 3), length(1, 2))
        val scale = minOf(2400f / maxOf(width, height), 1f); width *= scale; height *= scale
        require(width >= 64 && height >= 64) { "Select a larger document area." }
        val output = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        try {
            val matrix = Matrix(); check(matrix.setPolyToPoly(p.flatMap { listOf(it.x, it.y) }.toFloatArray(), 0, floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height), 0, 4))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (grayscale) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            val canvas = Canvas(output); canvas.drawColor(Color.WHITE); canvas.drawBitmap(bitmap, matrix, paint)
            currentCoroutineContext().ensureActive(); target.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            target
        } catch (failure: Throwable) { target.delete(); throw failure } finally { output.recycle() }
    }
}
