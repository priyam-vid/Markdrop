package com.markdrop.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.data.settings.SettingsRepository
import com.markdrop.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrecisionModeViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val modelManager: ModelManager
) : AndroidViewModel(application) {

    val availableModels = DefaultModelManifest.MODELS

    private val _selectedModel = MutableStateFlow(DefaultModelManifest.DEFAULT)
    val selectedModel: StateFlow<ModelManifest> = _selectedModel.asStateFlow()

    val downloadState: StateFlow<ModelDownloadState> = modelManager.downloadState

    val deviceRamGb: Double = modelManager.getDeviceRamGb()

    val hfTokenFlow = settingsRepository.conversionSettingsFlow

    init {
        viewModelScope.launch {
            settingsRepository.conversionSettingsFlow.collect { settings ->
                val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
                val model = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT
                _selectedModel.value = model
                modelManager.checkLocalModel(model)
            }
        }
        viewModelScope.launch {
            modelManager.downloadState.collect { state ->
                if (state is ModelDownloadState.Downloaded) {
                    settingsRepository.setPrecisionModeEnabled(true)
                    settingsRepository.setPrecisionModelId(_selectedModel.value.modelId)
                }
            }
        }
    }

    fun selectModel(modelId: String) {
        val model = DefaultModelManifest.getModelById(modelId) ?: return
        viewModelScope.launch {
            settingsRepository.setPrecisionModelId(modelId)
            _selectedModel.value = model
            modelManager.checkLocalModel(model)
        }
    }

    fun saveHfToken(token: String?) {
        viewModelScope.launch {
            settingsRepository.setHuggingFaceToken(token)
        }
    }

    fun isRamSufficient(): Boolean {
        return modelManager.isDeviceRamSufficient(_selectedModel.value)
    }

    fun startDownload(hfToken: String? = null) {
        modelManager.startDownload(_selectedModel.value, hfToken)
    }

    fun cancelDownload() {
        modelManager.cancelDownload()
    }

    fun deleteModel() {
        modelManager.deleteModel(_selectedModel.value)
        viewModelScope.launch {
            settingsRepository.setPrecisionModeEnabled(false)
            settingsRepository.setPrecisionModelId(null)
        }
    }

    fun isModelReady(): Boolean = modelManager.isModelReady(_selectedModel.value)

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository,
        private val modelManager: ModelManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PrecisionModeViewModel(application, settingsRepository, modelManager) as T
        }
    }
}
