package com.amaankhan.toolkit.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageMigrationTest {
    @Test fun existingPdfHistorySurvivesMimeColumnMigration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL("CREATE TABLE exports (uri TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, pages INTEGER NOT NULL, bytes INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO exports VALUES ('content://test/preserved', 'Existing.pdf', 3, 1234, 1000)")
                db.version = 1
            }
            val db = Room.databaseBuilder(context, ToolkitDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
            try {
                db.openHelper.writableDatabase.query("SELECT name, pages, mime FROM exports WHERE uri = 'content://test/preserved'").use {
                    assertTrue(it.moveToFirst()); assertEquals("Existing.pdf", it.getString(0)); assertEquals(3, it.getInt(1)); assertEquals("application/pdf", it.getString(2))
                }
            }
            finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
