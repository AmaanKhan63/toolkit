package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amaankhan.toolkit.documents.PdfResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun ImageResultPreview(result: PdfResult, modifier: Modifier = Modifier) {
    var page by rememberSaveable(result.file.path) { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }; var y by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val bitmap by produceState<Bitmap?>(null, result.file.path, page) {
        value = null
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.images[page].path) }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page--; zoom = 1f; x = 0f; y = 0f }, enabled = page > 0) { GlyphIcon(Glyph.BACK, "Previous image") }
            Text("${page + 1} / ${result.images.size}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { page++; zoom = 1f; x = 0f; y = 0f }, enabled = page < result.images.lastIndex) { GlyphIcon(Glyph.CHEVRON, "Next image") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { zoom = 1f; x = 0f; y = 0f }) { Text("Fit") }
            IconButton(onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }, enabled = zoom < 3f) { GlyphIcon(Glyph.ZOOM, "Zoom image") }
        }
        Text("Source page ${result.sourcePages[page]} · ${result.images[page].extension.uppercase()}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds().onSizeChanged { viewport = it }
            .previewPageSwipe(zoom <= 1f && bitmap != null) { delta ->
                page = (page + delta).coerceIn(0, result.images.lastIndex); x = 0f; y = 0f
            }
            .transformable(rememberTransformableState { factor, pan, _ ->
                zoom = (zoom * factor).coerceIn(1f, 3f)
                val maxX = viewport.width * (zoom - 1f) / 2f; val maxY = viewport.height * (zoom - 1f) / 2f
                x = (x + pan.x).coerceIn(-maxX, maxX); y = (y + pan.y).coerceIn(-maxY, maxY)
            }, canPan = { zoom > 1f }), contentAlignment = Alignment.Center) {
            if (bitmap == null) CircularProgressIndicator() else Image(bitmap!!.asImageBitmap(), "Exported image ${page + 1}", Modifier.fillMaxSize().padding(16.dp).graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = x; translationY = y })
        }
    }
}
