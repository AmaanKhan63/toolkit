package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class RenderedPage(val bitmap: Bitmap? = null, val count: Int = 0, val error: String? = null)

/** Renders the actual generated file. Only one bounded page bitmap is retained per viewer. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun PdfPreview(uri: String, modifier: Modifier = Modifier, jump: Pair<Int, Int>? = null, onPageChanged: (Int) -> Unit = {}) {
    var page by rememberSaveable(uri) { mutableIntStateOf(0) }
    LaunchedEffect(page) { onPageChanged(page) }
    var zoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val resolver = LocalContext.current.contentResolver
    val rendered by produceState(RenderedPage(), uri, page) {
        value = RenderedPage()
        value = try {
            withContext(Dispatchers.IO) {
                val source = Uri.parse(uri)
                val descriptor = if (source.scheme == "file") ParcelFileDescriptor.open(File(requireNotNull(source.path)), ParcelFileDescriptor.MODE_READ_ONLY)
                    else resolver.openFileDescriptor(source, "r") ?: error("No file")
                descriptor.use { PdfRenderer(it).use { renderer ->
                    require(renderer.pageCount > 0)
                    renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                        val scale = 1600f / maxOf(pdfPage.width, pdfPage.height)
                        val bitmap = Bitmap.createBitmap((pdfPage.width * scale).roundToInt().coerceAtLeast(1), (pdfPage.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        try { pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        catch (error: Throwable) { bitmap.recycle(); throw error }
                        RenderedPage(bitmap, renderer.pageCount)
                    }
                } }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: SecurityException) { RenderedPage(error = "This PDF is password protected or access has expired. Unlock it first, or select it again.") }
        catch (_: Exception) { RenderedPage(error = "This PDF could not be opened. It may be damaged, encrypted, or no longer accessible.") }
    }
    fun setZoom(next: Float) { zoom = next.coerceIn(1f, 3f); if (zoom == 1f) { panX = 0f; panY = 0f } }
    LaunchedEffect(jump) { jump?.let { page = it.first.coerceAtLeast(0); setZoom(1f) } }
    Column(modifier) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page--; setZoom(1f) }, enabled = page > 0 && rendered.count > 0) { GlyphIcon(Glyph.BACK, "Previous page") }
            Text(if (rendered.count > 0) "${page + 1} / ${rendered.count}" else "—", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { page++; setZoom(1f) }, enabled = rendered.count > page + 1) { GlyphIcon(Glyph.CHEVRON, "Next page") }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { setZoom(zoom - .5f) }, enabled = zoom > 1f) { GlyphIcon(Glyph.ZOOM_OUT, "Zoom out") }
            TextButton(onClick = { setZoom(1f) }) { Text("${(zoom * 100).roundToInt()}%") }
            IconButton(onClick = { setZoom(zoom + .5f) }, enabled = zoom < 3f) { GlyphIcon(Glyph.ZOOM, "Zoom in") }
          }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds().onSizeChanged { viewport = it }
            .previewPageSwipe(zoom <= 1f && rendered.count > 0) { delta ->
                page = (page + delta).coerceIn(0, rendered.count - 1); setZoom(1f)
            }
            .transformable(rememberTransformableState { scale, pan, _ ->
                setZoom(zoom * scale)
                val maxX = viewport.width * (zoom - 1f) / 2f
                val maxY = viewport.height * (zoom - 1f) / 2f
                panX = (panX + pan.x).coerceIn(-maxX, maxX); panY = (panY + pan.y).coerceIn(-maxY, maxY)
            }, canPan = { zoom > 1f }), contentAlignment = Alignment.Center) {
            when {
                rendered.error != null -> Text(rendered.error!!, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                rendered.bitmap != null -> Image(rendered.bitmap!!.asImageBitmap(), "PDF page ${page + 1}", Modifier.fillMaxSize().padding(16.dp).graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY
                })
                else -> CircularProgressIndicator()
            }
        }
    }
}
