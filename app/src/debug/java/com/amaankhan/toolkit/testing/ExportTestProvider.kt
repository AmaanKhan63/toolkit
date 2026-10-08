package com.amaankhan.toolkit.testing

import android.database.MatrixCursor
import android.os.*
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises SAF failures without granting broad storage access or touching user files. */
class ExportTestProvider : DocumentsProvider() {
    private val failWrite = AtomicBoolean(false)
    private val root get() = File(requireNotNull(context).cacheDir, "export-provider-test").canonicalFile.apply { mkdirs() }
    private val columns = arrayOf("document_id", "_display_name", "mime_type", "flags", "_size", "last_modified")
    private fun file(id: String): File {
        val folder = root.canonicalFile
        return if (id == "root") folder else File(folder, id).canonicalFile.also { require(it.path.startsWith(folder.path + File.separator)) }
    }
    private fun row(cursor: MatrixCursor, f: File) {
        val id = if (f == root.canonicalFile) "root" else f.relativeTo(root).invariantSeparatorsPath
        val values = mapOf<String, Any>("document_id" to id, "_display_name" to f.name, "mime_type" to if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream", "flags" to (DocumentsContract.Document.FLAG_SUPPORTS_DELETE or if (f.isDirectory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE), "_size" to f.length(), "last_modified" to f.lastModified())
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(arrayOf("root_id", "document_id", "title", "flags")).apply { addRow(arrayOf<Any>("root", "root", "Toolkit test output", DocumentsContract.Root.FLAG_SUPPORTS_CREATE)) }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = MatrixCursor(projection ?: columns).apply { val f = file(documentId); if (f.exists()) row(this, f) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = MatrixCursor(projection ?: columns).apply { file(parentDocumentId).listFiles()?.forEach { row(this, it) } }
    override fun getDocumentType(documentId: String) = if (file(documentId).isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
    override fun isChildDocument(parentDocumentId: String, documentId: String) = file(documentId).path.startsWith(file(parentDocumentId).path + File.separator)
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(displayName.isNotBlank() && !displayName.contains('/') && !displayName.contains('\\'))
        val parent = file(parentDocumentId); var target = File(parent, displayName); var index = 1
        while (target.exists()) target = File(parent, "${index++}-$displayName")
        check(if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile())
        return target.relativeTo(root).invariantSeparatorsPath
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode.contains('w') && failWrite.compareAndSet(true, false)) throw java.io.FileNotFoundException("Synthetic write failure")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
    override fun deleteDocument(documentId: String) { require(documentId != "root"); check(file(documentId).delete()) }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "failNextWrite") { failWrite.set(true); return Bundle() }
        return super.call(method, arg, extras)
    }
}
