package com.amaankhan.toolkit.documents

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Keep the exact URI returned by ACTION_OPEN_DOCUMENT; never derive a provider path. */
fun retainDocumentReadPermission(resolver: ContentResolver, uri: Uri): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
    return try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        // Some providers offer only a temporary grant. The current selection can still open.
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

fun documentDisplayName(resolver: ContentResolver, uri: Uri, fallback: String = "Document"): String {
    if (uri.scheme == ContentResolver.SCHEME_FILE) return File(uri.path.orEmpty()).name.ifBlank { fallback }
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column)?.takeIf { it.isNotBlank() } else null
        } ?: fallback
    } catch (_: Exception) {
        // Metadata may be unavailable even when the provider can supply the document bytes.
        fallback
    }
}

fun isPdfDocument(mimeType: String?, displayName: String): Boolean =
    mimeType?.substringBefore(';')?.trim().equals("application/pdf", ignoreCase = true) || displayName.endsWith(".pdf", ignoreCase = true)

fun isPdfDocument(resolver: ContentResolver, uri: Uri): Boolean {
    val mime = try { resolver.getType(uri) } catch (_: Exception) { null }
    return isPdfDocument(mime, documentDisplayName(resolver, uri))
}

/** Error text is deliberately independent of exception messages and provider internals. */
fun documentReadError(error: Throwable, pdf: Boolean = false): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is SecurityException } -> "Access to this file has expired. Choose it again to allow Toolkit to read it."
        causes.any { it is FileNotFoundException } -> "This file was moved, deleted, or is unavailable. Retry or choose it again."
        causes.any { it is OutOfMemoryError } -> "Not enough memory to open this document. Choose a smaller file."
        pdf -> "This PDF could not be read. It may be damaged or use an unsupported format."
        else -> "This document could not be read. It may be damaged or unavailable. Retry or choose another file."
    }
}
