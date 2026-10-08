package com.amaankhan.toolkit.downloads

import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.ToolkitApplication
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadServiceTest {
    @Test fun realSafFailureAndResumeSaveEachOutputOnceWithoutOverwritingExistingFiles() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ToolkitApplication>()
        val store = app.downloads
        val treeUri = DocumentsContract.buildTreeDocumentUri("com.amaankhan.toolkit.export-test", "root")
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, treeUri))
        val folder = requireNotNull(tree.createDirectory("run-${System.nanoTime()}"))
        val destination = DocumentsContract.buildTreeDocumentUri("com.amaankhan.toolkit.export-test", DocumentsContract.getDocumentId(folder.uri))
        app.grantUriPermission(app.packageName, destination, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val existing = requireNotNull(folder.createFile("text/plain", "same.txt"))
        app.contentResolver.openOutputStream(existing.uri)!!.use { it.write("Existing document".toByteArray()) }
        val entry = DownloadEntry(kind = "DIRECT", source = "https://example.invalid/synthetic", name = "SAF integration fixture", destination = destination.toString(), ready = true,
            files = JSONArray().put(JSONObject().put("path", "one.txt").put("name", "same.txt")).put(JSONObject().put("path", "two.txt").put("name", "second.txt")).toString())
        File(store.directory(entry.id), "one.txt").writeText("First output"); File(store.directory(entry.id), "two.txt").writeText("Second output")
        store.dao.insert(entry)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            app.contentResolver.call(Uri.parse("content://com.amaankhan.toolkit.export-test"), "failNextWrite", null, null)
            scenario.onActivity { DownloadService.start(it) }
            val failed = withTimeout(20000) { while (true) { val value = store.dao.get(entry.id)!!; if (value.state == "FAILED") return@withTimeout value; delay(100) }; error("unreachable") }
            assertTrue("Staged files lost: $failed", failed.ready)
            assertTrue("Expected failure while writing a created output: $failed", failed.pendingUri.isNotBlank())
            assertTrue("Staged source lost: $failed", File(store.directory(entry.id), "one.txt").isFile)
            // The user may already have removed the partial before pressing Retry.
            DocumentsContract.deleteDocument(app.contentResolver, Uri.parse(failed.pendingUri))
            store.dao.state(entry.id, "QUEUED", "Retry")
            scenario.onActivity { DownloadService.start(it) }
            val completed = withTimeout(20000) { while (true) { val value = store.dao.get(entry.id)!!; if (value.state == "COMPLETE") return@withTimeout value; if (value.state == "FAILED") error(value.error); delay(100) }; error("unreachable") }
            assertEquals(2, JSONObject(completed.exports).length())
            val contents = folder.listFiles().map { file -> app.contentResolver.openInputStream(file.uri)!!.bufferedReader().use { it.readText() } }.sorted()
            assertEquals(listOf("Existing document", "First output", "Second output"), contents)
            assertFalse(File(app.noBackupFilesDir, "downloads/${entry.id}").exists())
        } finally {
            scenario.close()
            folder.listFiles().forEach { app.database.exports().remove(it.uri.toString()); it.delete() }; folder.delete()
            store.dao.remove(entry.id); File(app.noBackupFilesDir, "downloads/${entry.id}").deleteRecursively()
        }
    }
}
