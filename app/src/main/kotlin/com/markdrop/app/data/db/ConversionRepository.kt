package com.markdrop.app.data.db

import kotlinx.coroutines.flow.Flow

class ConversionRepository(
    private val dao: ConversionDao
) {
    val activeQueueFlow: Flow<List<ConversionEntity>> = dao.getActiveQueueFlow()
    val completedFlow: Flow<List<ConversionEntity>> = dao.getCompletedFlow()
    val allFlow: Flow<List<ConversionEntity>> = dao.getAllFlow()

    fun searchCompletedFlow(query: String): Flow<List<ConversionEntity>> =
        dao.searchCompletedFlow(query)

    fun getByIdFlow(id: Long): Flow<ConversionEntity?> =
        dao.getByIdFlow(id)

    suspend fun getById(id: Long): ConversionEntity? =
        dao.getById(id)

    suspend fun insert(conversion: ConversionEntity): Long =
        dao.insert(conversion)

    suspend fun update(conversion: ConversionEntity) =
        dao.update(conversion)

    suspend fun updateStatus(id: Long, status: ConversionStatus) =
        dao.updateStatus(id, status)

    suspend fun updateProgress(id: Long, pages: Int, progress: Float) =
        dao.updateProgress(id, pages, progress)

    suspend fun markCompleted(id: Long, markdown: String, duration: Long) =
        dao.markCompleted(id, ConversionStatus.DONE, System.currentTimeMillis(), markdown, duration)

    suspend fun markFailed(id: Long, error: String) =
        dao.markFailed(id, error)

    suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    suspend fun pauseAll() =
        dao.pauseAll()

    suspend fun resumeAll() =
        dao.resumeAll()
}
