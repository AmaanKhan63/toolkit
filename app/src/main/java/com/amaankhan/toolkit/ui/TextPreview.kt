package com.amaankhan.toolkit.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.amaankhan.toolkit.documents.documentReadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun TextViewerScreen(uri: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { ScreenHeader("Text viewer", onBack) }) { padding -> TextPreview(uri, Modifier.padding(padding).fillMaxSize()) }
}
@Composable fun TextPreview(uri: String, modifier: Modifier = Modifier, onReadError: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val reportError by rememberUpdatedState(onReadError)
    var chunk by androidx.compose.runtime.saveable.rememberSaveable(uri) { mutableIntStateOf(0) }
    val result by produceState<Pair<String, Boolean>?>(null, uri, chunk) {
        value = null
        value = try { com.amaankhan.toolkit.documents.LocalDocumentReader(context).textChunk(uri, chunk) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: OutOfMemoryError) { documentReadError(e).let { reportError?.invoke(it); it to false } }
        catch (e: Exception) { documentReadError(e).let { reportError?.invoke(it); it to false } }
    }
    Column(modifier.previewPageSwipe(result != null) { delta ->
        if (delta < 0 && chunk > 0 || delta > 0 && result?.second == true) chunk += delta
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { chunk-- }, enabled = chunk > 0 && result != null) { Text("Previous part") }
            Text("Part ${chunk + 1}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { chunk++ }, enabled = result?.second == true) { Text("Next part") }
        }
        if (result == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else key(chunk) { SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) { Text(result!!.first, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) } }
    }
}
