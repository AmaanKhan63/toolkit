package com.amaankhan.toolkit.documents

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.ToolkitApplication
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageFolderExportTest {
    @Test fun generatedImagesSaveAsSeparateReadableFilesAndKeepExistingDestinationContent() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ToolkitApplication>()
        val rootUri = DocumentsContract.buildTreeDocumentUri("com.amaankhan.toolkit.export-test", "root")
        val root = requireNotNull(DocumentFile.fromTreeUri(app, rootUri))
        val folder = requireNotNull(root.createDirectory("images-${System.nanoTime()}"))
        val destination = DocumentsContract.buildTreeDocumentUri("com.amaankhan.toolkit.export-test", DocumentsContract.getDocumentId(folder.uri))
        app.grantUriPermission(app.packageName, destination, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val source = File(app.cacheDir, "image-folder-fixture.pdf")
        PDDocument().use { doc -> repeat(2) { doc.addPage(PDPage()) }; doc.save(source) }
        val existing = requireNotNull(folder.createFile("text/plain", "keep.txt"))
        app.contentResolver.openOutputStream(existing.uri)!!.use { it.write("Preserved".toByteArray()) }
        val holder = ViewModelStore()
        lateinit var model: PdfToolViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider.create(holder, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PdfToolViewModel::class.java]
                model.run(PdfRequest(PdfOperation.IMAGES, listOf(Uri.fromFile(source).toString()), imageEdge = 1200))
            }
            withTimeout(20000) { while (model.state.value.result == null && model.state.value.error == null) delay(50) }
            assertNotNull(model.state.value.error, model.state.value.result)
            instrumentation.runOnMainSync { model.saveImagesToFolder(destination) }
            withTimeout(20000) { while (model.state.value.saved == null && model.state.value.error == null) delay(50) }
            assertNotNull(model.state.value.error, model.state.value.saved)
            val files = folder.listFiles()
            assertEquals(3, files.size)
            assertEquals("Preserved", app.contentResolver.openInputStream(existing.uri)!!.bufferedReader().use { it.readText() })
            files.filter { it.name!!.endsWith(".png") }.forEach { file ->
                val bitmap = app.contentResolver.openInputStream(file.uri)!!.use(android.graphics.BitmapFactory::decodeStream)
                assertNotNull(bitmap); assertTrue(bitmap!!.height >= 500); bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { holder.clear() }
            source.delete()
            folder.listFiles().forEach { app.database.exports().remove(it.uri.toString()); it.delete() }; folder.delete()
        }
    }
}
