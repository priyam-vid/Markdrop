package com.markdrop.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MarkdropDatabase : RoomDatabase() {

    abstract fun conversionDao(): ConversionDao

    companion object {
        @Volatile
        private var INSTANCE: MarkdropDatabase? = null

        fun getInstance(context: Context): MarkdropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarkdropDatabase::class.java,
                    "markdrop.db"
                ).fallbackToDestructiveMigration()
                 .build()
                 .also { INSTANCE = it }
            }
        }
    }
}
