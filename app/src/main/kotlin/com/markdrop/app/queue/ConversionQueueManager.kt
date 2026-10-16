package com.markdrop.app.queue

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.ConversionEngine
import com.markdrop.app.conversion.ConversionMode
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.data.db.ConversionRepository
import com.markdrop.app.data.db.ConversionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap

class ConversionQueueManager(
    private val context: Context,
    private val repository: ConversionRepository,
    private val conversionEngine: ConversionEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    // Separate semaphores for local compute vs external cloud API
    private var localSemaphore = Semaphore(5)
    private var cloudSemaphore = Semaphore(2)

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var isPaused = false

    init {
        // Observe queue from DB and trigger workers
        scope.launch {
            repository.activeQueueFlow.collectLatest { queueItems ->
                if (!isPaused) {
                    processQueueItems(queueItems)
                }
            }
        }
    }

    fun setLocalConcurrency(limit: Int) {
        localSemaphore = Semaphore(limit.coerceIn(1, 10))
    }

    fun setCloudConcurrency(limit: Int) {
        cloudSemaphore = Semaphore(limit.coerceIn(1, 5))
    }

    fun enqueue(
        fileName: String,
        pdfUri: Uri,
        mode: ConversionMode,
        pageCount: Int,
        fileSizeBytes: Long
    ) {
        scope.launch {
            val entity = ConversionEntity(
                fileName = fileName,
                pdfUri = pdfUri.toString(),
                mode = mode,
                status = ConversionStatus.PENDING,
                pageCount = pageCount,
                fileSizeBytes = fileSizeBytes
            )
            repository.insert(entity)
        }
    }

    fun pauseItem(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            repository.updateStatus(id, ConversionStatus.PAUSED)
        }
    }

    fun resumeItem(id: Long) {
        scope.launch {
            repository.updateStatus(id, ConversionStatus.PENDING)
        }
    }

    fun retryItem(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            repository.updateProgress(id, 0, 0f)
            repository.updateStatus(id, ConversionStatus.PENDING)
        }
    }

    fun cancelItem(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            repository.updateStatus(id, ConversionStatus.CANCELLED)
        }
    }

    fun deleteItem(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            repository.deleteById(id)
        }
    }

    fun pauseAll() {
        isPaused = true
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.launch {
            repository.pauseAll()
        }
    }

    fun resumeAll() {
        isPaused = false
        scope.launch {
            repository.resumeAll()
        }
    }

    private fun processQueueItems(items: List<ConversionEntity>) {
        for (item in items) {
            if (item.status == ConversionStatus.PAUSED || item.status == ConversionStatus.CANCELLED) {
                continue
            }
            if (activeJobs.containsKey(item.id)) {
                continue // Already running
            }

            // Launch worker for this item
            val job = scope.launch {
                val semaphore = if (item.mode == ConversionMode.ADVANCED) cloudSemaphore else localSemaphore

                // Mark as WAITING while waiting for a concurrency slot
                if (semaphore.availablePermits == 0) {
                    repository.updateStatus(item.id, ConversionStatus.WAITING)
                }

                semaphore.acquire()
                try {
                    repository.updateStatus(item.id, ConversionStatus.PROCESSING)

                    val result = conversionEngine.convertPdf(
                        pdfUri = Uri.parse(item.pdfUri),
                        mode = item.mode,
                        onProgress = { current, total ->
                            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                            scope.launch {
                                repository.updateProgress(item.id, current, progress)
                            }
                        }
                    )

                    repository.markCompleted(
                        id = item.id,
                        markdown = result.markdown,
                        duration = result.durationMs
                    )
                } catch (e: CancellationException) {
                    // Task was cancelled or paused
                } catch (e: Exception) {
                    repository.markFailed(item.id, e.localizedMessage ?: "Conversion failed")
                } finally {
                    semaphore.release()
                    activeJobs.remove(item.id)
                }
            }

            activeJobs[item.id] = job
        }
    }
}
