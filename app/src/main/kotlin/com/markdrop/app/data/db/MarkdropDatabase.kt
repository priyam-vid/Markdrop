package com.markdrop.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversionEntity::class, PageResultEntity::class],
    version = 4,
    exportSchema = false
)
abstract class MarkdropDatabase : RoomDatabase() {

    abstract fun conversionDao(): ConversionDao
    abstract fun pageResultDao(): PageResultDao

    companion object {
        @Volatile
        private var INSTANCE: MarkdropDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `page_results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `jobId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `markdown` TEXT,
                        `error` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `engineUsed` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`jobId`) REFERENCES `conversions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_results_jobId` ON `page_results` (`jobId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_results_jobId_pageIndex` ON `page_results` (`jobId`, `pageIndex`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversions` ADD COLUMN `selectedPages` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversions` ADD COLUMN `inputUris` TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): MarkdropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarkdropDatabase::class.java,
                    "markdrop.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                 .build()
                 .also { INSTANCE = it }
            }
        }
    }
}
