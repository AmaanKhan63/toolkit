package com.amaankhan.toolkit.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.R
import com.amaankhan.toolkit.data.ExportedDocument
import com.amaankhan.toolkit.data.Preferences
import com.amaankhan.toolkit.data.ThemeChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
@Composable fun EmptyNote(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
@Composable fun InfoCard(title: String, body: String) {
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun DocumentActions(file: ExportedDocument, onPreview: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (onPreview != null) onPreview() else scope.launch { launchDocument(context, file, false) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Open") }
        OutlinedButton(onClick = { scope.launch { launchDocument(context, file, true) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Share") }
    }
}
private suspend fun launchDocument(context: Context, file: ExportedDocument, share: Boolean) {
    try {
        val uri = Uri.parse(file.uri)
        withContext(Dispatchers.IO) { context.contentResolver.openFileDescriptor(uri, "r")?.close() ?: error("Missing file") }
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) { type = file.mime; putExtra(Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, file.mime)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(if (share) Intent.createChooser(intent, "Share file") else intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app is installed for this file type. You can still share the file.", Toast.LENGTH_LONG).show()
    } catch (_: Exception) { Toast.makeText(context, "This file is unavailable or its access has expired. Open it from your file manager.", Toast.LENGTH_LONG).show() }
}
fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(1)} KB" else String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
