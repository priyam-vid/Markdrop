package com.markdrop.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_results",
    foreignKeys = [
        ForeignKey(
            entity = ConversionEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("jobId"),
        Index(value = ["jobId", "pageIndex"])
    ]
)
data class PageResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val pageIndex: Int,
    val status: String, // "DONE", "FAILED", "SKIPPED"
    val markdown: String? = null,
    val error: String? = null,
    val durationMs: Long = 0L,
    val engineUsed: String? = null, // "TEXT_LAYER", "OFFLINE_OCR", "VISION_AI_CLOUD", "VISION_AI_LOCAL"
    val createdAt: Long = System.currentTimeMillis()
)
