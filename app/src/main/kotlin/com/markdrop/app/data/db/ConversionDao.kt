package com.markdrop.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversion: ConversionEntity): Long

    @Update
    suspend fun update(conversion: ConversionEntity)

    @Delete
    suspend fun delete(conversion: ConversionEntity)

    @Query("DELETE FROM conversions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM conversions WHERE id = :id")
    suspend fun getById(id: Long): ConversionEntity?

    @Query("SELECT * FROM conversions WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ConversionEntity?>

    @Query("SELECT * FROM conversions WHERE status NOT IN ('DONE', 'FAILED', 'CANCELLED') ORDER BY createdAt ASC")
    fun getActiveQueueFlow(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE status = 'DONE' ORDER BY completedAt DESC")
    fun getCompletedFlow(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE status = 'DONE' AND fileName LIKE '%' || :query || '%' ORDER BY completedAt DESC")
    fun searchCompletedFlow(query: String): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<ConversionEntity>>

    @Query("UPDATE conversions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ConversionStatus)

    @Query("UPDATE conversions SET pagesCompleted = :pages, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, pages: Int, progress: Float)

    @Query("UPDATE conversions SET status = :status, completedAt = :completedAt, markdownContent = :markdown, durationMs = :duration WHERE id = :id")
    suspend fun markCompleted(id: Long, status: ConversionStatus, completedAt: Long, markdown: String, duration: Long)

    @Query("UPDATE conversions SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE conversions SET status = 'PAUSED' WHERE status IN ('PENDING', 'WAITING', 'PROCESSING')")
    suspend fun pauseAll()

    @Query("UPDATE conversions SET status = 'PENDING' WHERE status = 'PAUSED'")
    suspend fun resumeAll()
}
