package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.documents.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable fun ScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model: PdfToolViewModel = viewModel(key = "scan")
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val directory = remember { File(context.cacheDir, "scan-camera").apply { mkdirs() } }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf(listOf(ScanPoint(.05f, .05f), ScanPoint(.95f, .05f), ScanPoint(.95f, .95f), ScanPoint(.05f, .95f))) }
    var gray by rememberSaveable { mutableStateOf(false) }
    fun load(uri: Uri) { preparing = true; scope.launch {
        try { bitmap = ScanEngine(context).decode(uri); points = listOf(ScanPoint(.05f, .05f), ScanPoint(.95f, .05f), ScanPoint(.95f, .95f), ScanPoint(.05f, .95f)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { model.error(e.message ?: "Unable to read scan.") } finally { preparing = false }
    } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> cameraFile?.let { path -> if (success) load(Uri.fromFile(File(path))) else File(path).delete() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) load(uri) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), model::save)
    val back = { if (!state.busy && !preparing) { if (bitmap != null) { bitmap = null } else if (state.result != null) model.editResult() else onBack() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(if (bitmap == null) "Scan to PDF" else "Adjust document corners", back, !state.busy && !preparing) }, bottomBar = { ActionBar {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        when {
            state.busy || preparing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress.ifBlank { "Preparing image…" }); if (state.busy) TextButton(onClick = model::cancel) { Text("Cancel") } }
            bitmap != null -> PrimaryAction("Keep corrected page") {
                preparing = true; scope.launch {
                    try {
                        val target = File(directory, "page-${UUID.randomUUID()}.jpg")
                        ScanEngine(context).correct(bitmap!!, points, gray, target)
                        model.select(listOf(Uri.fromFile(target)), true, false); bitmap = null
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { model.error(e.message ?: "Unable to correct perspective.") } finally { preparing = false }
                }
            }
            state.result != null -> { PrimaryAction("Save PDF") { save.launch("Scan-${System.currentTimeMillis()}.pdf") }; TextButton(onClick = model::editResult) { Text("Back to edit") } }
            state.saved != null -> PrimaryAction("Start again", onClick = model::reset)
            else -> PrimaryAction("Preview PDF", state.inputs.isNotEmpty()) { model.runWorkflow(WorkflowTemplate("Scan to PDF", listOf(WorkflowStep(WorkflowOperation.IMAGES_PDF, marginPx = "0"))), emptyMap()) }
        }
    } }) { padding ->
        when {
            state.result != null -> PdfPreview(Uri.fromFile(state.result!!.preview).toString(), Modifier.padding(padding).fillMaxSize())
            bitmap != null -> Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Drag the four corners around the document. Perspective is corrected when you keep the page.", style = MaterialTheme.typography.bodyMedium)
                val image = bitmap!!
                Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(image) {
                    var selected = -1
                    fun bounds(): Triple<Float, Float, Float> { val scale = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height); return Triple(scale, (size.width - image.width * scale) / 2, (size.height - image.height * scale) / 2) }
                    detectDragGestures(onDragStart = { at ->
                        val (scale, left, top) = bounds()
                        selected = points.indices.minByOrNull { i -> (Offset(left + points[i].x * image.width * scale, top + points[i].y * image.height * scale) - at).getDistance() } ?: -1
                    }, onDragEnd = { selected = -1 }, onDragCancel = { selected = -1 }, onDrag = { change, _ ->
                        if (selected >= 0) { change.consume(); val (scale, left, top) = bounds(); points = points.toMutableList().also { it[selected] = ScanPoint(((change.position.x - left) / (image.width * scale)).coerceIn(0f, 1f), ((change.position.y - top) / (image.height * scale)).coerceIn(0f, 1f)) } }
                    })
                }) {
                    val scale = minOf(size.width / image.width, size.height / image.height); val w = image.width * scale; val h = image.height * scale; val left = (size.width - w) / 2; val top = (size.height - h) / 2
                    drawImage(image.asImageBitmap(), dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
                    val corners = points.map { Offset(left + it.x * w, top + it.y * h) }
                    corners.indices.forEach { i -> drawLine(Color(0xff50d9ae), corners[i], corners[(i + 1) % 4], 3.dp.toPx()); drawCircle(Color.White, 11.dp.toPx(), corners[i]); drawCircle(Color(0xff155e4c), 11.dp.toPx(), corners[i], style = Stroke(2.dp.toPx())) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(gray, { gray = it }); Text("Grayscale") }
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.saved != null) item { InfoCard("Scan saved", state.saved!!.name); DocumentActions(state.saved!!) }
                else {
                    item { InfoCard("A document from your camera", "Capture up to 20 pages, adjust their corners, arrange them and review the PDF before saving.") }
                    item { PrimaryAction("Capture a page", !state.busy && !preparing && state.inputs.size < 20) {
                        try { val file = File(directory, "camera-${UUID.randomUUID()}.jpg"); cameraFile = file.path; camera.launch(FileProvider.getUriForFile(context, context.packageName + ".camera", file)) }
                        catch (_: Exception) { model.error("No camera app is available. Choose a photo instead.") }
                    } }
                    item { OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = !state.busy && !preparing && state.inputs.size < 20, modifier = Modifier.fillMaxWidth()) { Text("Choose a photo") } }
                    itemsIndexed(state.inputs) { index, _ -> Surface(shape = MaterialTheme.shapes.medium) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Page ${index + 1}", Modifier.weight(1f)); IconButton(onClick = { model.move(index, -1) }, enabled = index > 0) { GlyphIcon(Glyph.UP, "Move page up") }; IconButton(onClick = { model.move(index, 1) }, enabled = index < state.inputs.lastIndex) { GlyphIcon(Glyph.DOWN, "Move page down") }; IconButton(onClick = { model.remove(index) }) { GlyphIcon(Glyph.CLOSE, "Remove page") }
                    } } }
                }
            }
        }
    }
}
