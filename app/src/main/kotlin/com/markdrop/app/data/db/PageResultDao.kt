package com.markdrop.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PageResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pageResult: PageResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pageResults: List<PageResultEntity>)

    @Update
    suspend fun update(pageResult: PageResultEntity)

    @Query("SELECT * FROM page_results WHERE jobId = :jobId ORDER BY pageIndex ASC")
    suspend fun getByJobId(jobId: Long): List<PageResultEntity>

    @Query("SELECT * FROM page_results WHERE jobId = :jobId ORDER BY pageIndex ASC")
    fun getByJobIdFlow(jobId: Long): Flow<List<PageResultEntity>>

    @Query("SELECT * FROM page_results WHERE jobId = :jobId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getByJobAndPage(jobId: Long, pageIndex: Int): PageResultEntity?

    @Query("DELETE FROM page_results WHERE jobId = :jobId")
    suspend fun deleteByJobId(jobId: Long)
}
