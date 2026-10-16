package com.markdrop.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.markdrop.app.conversion.ConversionMode

@Entity(tableName = "conversions")
data class ConversionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val pdfUri: String,
    val outputFilePath: String? = null,
    val markdownContent: String? = null,
    val mode: ConversionMode,
    val status: ConversionStatus,
    val pageCount: Int,
    val pagesCompleted: Int = 0,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L
)
