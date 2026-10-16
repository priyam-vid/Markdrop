package com.markdrop.app.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.data.db.ConversionRepository
import com.markdrop.app.queue.ConversionQueueManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueScreenUiState(
    val queueItems: List<ConversionEntity> = emptyList(),
    val isPaused: Boolean = false
)

class QueueViewModel(
    private val repository: ConversionRepository,
    private val queueManager: ConversionQueueManager
) : ViewModel() {

    val queueItems: StateFlow<List<ConversionEntity>> = repository.activeQueueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseItem(id: Long) {
        queueManager.pauseItem(id)
    }

    fun resumeItem(id: Long) {
        queueManager.resumeItem(id)
    }

    fun retryItem(id: Long) {
        queueManager.retryItem(id)
    }

    fun cancelItem(id: Long) {
        queueManager.cancelItem(id)
    }

    fun deleteItem(id: Long) {
        queueManager.deleteItem(id)
    }

    fun pauseAll() {
        queueManager.pauseAll()
    }

    fun resumeAll() {
        queueManager.resumeAll()
    }

    class Factory(
        private val repository: ConversionRepository,
        private val queueManager: ConversionQueueManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QueueViewModel(repository, queueManager) as T
        }
    }
}
