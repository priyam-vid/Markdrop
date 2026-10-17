package com.markdrop.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @Test
    fun `migration 3 to 4 adds inputUris column`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `conversions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `pdfUri` TEXT NOT NULL,
                            `outputFilePath` TEXT,
                            `markdownContent` TEXT,
                            `mode` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `pageCount` INTEGER NOT NULL,
                            `pagesCompleted` INTEGER NOT NULL,
                            `progress` REAL NOT NULL,
                            `errorMessage` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `completedAt` INTEGER,
                            `fileSizeBytes` INTEGER NOT NULL,
                            `durationMs` INTEGER NOT NULL,
                            `selectedPages` TEXT
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_3_4
        MarkdropDatabase.MIGRATION_3_4.migrate(db)

        // Verify column inputUris exists
        val cursor = db.query("PRAGMA table_info(conversions)")
        var foundInputUris = false
        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex != -1 && cursor.getString(nameIndex) == "inputUris") {
                foundInputUris = true
                break
            }
        }
        cursor.close()
        db.close()
        dbFile.delete()

        assertTrue("Column 'inputUris' should exist after migration 3 to 4", foundInputUris)
    }
}
