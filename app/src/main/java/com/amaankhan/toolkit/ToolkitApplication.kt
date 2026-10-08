package com.amaankhan.toolkit

import android.app.Application
import androidx.room.Room
import com.amaankhan.toolkit.data.ToolkitDatabase
import com.amaankhan.toolkit.data.ToolkitPreferences

class ToolkitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        // Only abandoned private processing files from an earlier process are removed.
        java.io.File(cacheDir, "pdf-tools").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        java.io.File(cacheDir, "office-tmp").deleteRecursively()
        java.io.File(cacheDir, "scan-camera").deleteRecursively()
        cacheDir.listFiles()?.filter { it.isFile && (it.name.startsWith("signature-") || it.name.startsWith("reader-")) }?.forEach { it.delete() }
    }
    val preferences by lazy { ToolkitPreferences(this) }
    val downloads by lazy { com.amaankhan.toolkit.downloads.DownloadStore(this) }
    val telegram by lazy { com.amaankhan.toolkit.downloads.TelegramClient(this, downloads) }
    val workflows by lazy { com.amaankhan.toolkit.data.WorkflowStore(this) }
    val database by lazy { Room.databaseBuilder(this, ToolkitDatabase::class.java, "toolkit.db").addMigrations(com.amaankhan.toolkit.data.MIGRATION_1_2).build() }
}
