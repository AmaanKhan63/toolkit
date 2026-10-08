package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.util.UUID

@Composable fun SignaturePad(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var strokes by remember { mutableStateOf(emptyList<List<Offset>>()) }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Draw your signature", style = MaterialTheme.typography.titleLarge)
            Text("Use your finger or stylus. The saved mark has a transparent background.", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color.White).pointerInput(Unit) {
                detectDragGestures(onDragStart = { p -> strokes = strokes + listOf(listOf(Offset(p.x / size.width, p.y / size.height))) }, onDrag = { change, _ ->
                    change.consume(); if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) + listOf(strokes.last() + Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)))
                })
            }) {
                strokes.forEach { stroke -> stroke.zipWithNext().forEach { (a, b) -> drawLine(Color(0xff202020), Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), 3.dp.toPx(), StrokeCap.Round) } }
            }
            Row { TextButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }; TextButton(onClick = { strokes = emptyList() }) { Text("Clear") } }
            PrimaryAction("Use signature", strokes.any { it.size > 1 }) {
                val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(32, 32, 32); strokeWidth = 6f; strokeCap = android.graphics.Paint.Cap.ROUND; style = android.graphics.Paint.Style.STROKE }
                strokes.forEach { points -> val path = android.graphics.Path(); points.forEachIndexed { i, point -> if (i == 0) path.moveTo(point.x * 1200, point.y * 600) else path.lineTo(point.x * 1200, point.y * 600) }; canvas.drawPath(path, paint) }
                val file = File(context.cacheDir, "signature-${UUID.randomUUID()}.png")
                try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; onDone(Uri.fromFile(file).toString()) } finally { bitmap.recycle() }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        } }
    }
}
