package com.markdrop.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.data.db.ConversionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: ConversionRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val completedConversions: StateFlow<List<ConversionEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.completedFlow
            } else {
                repository.searchCompletedFlow(query.trim())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    class Factory(
        private val repository: ConversionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(repository) as T
        }
    }
}
