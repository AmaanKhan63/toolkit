package com.amaankhan.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** Android disallows selecting storage/Download roots; begin inside Documents. */
class DestinationFolderContract : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent = super.createIntent(context, input).apply {
        if (input == null) putExtra(DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"))
    }
}

const val FOLDER_GUIDANCE = "Choose Documents or create a Toolkit subfolder. Android does not allow the storage root or the main Download folder. Open a subfolder, then tap Use this folder."

@Composable fun FolderGuidance() = EmptyNote(FOLDER_GUIDANCE)

fun destinationFolderName(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifBlank { "Selected folder" }
}.getOrDefault("Selected folder")
