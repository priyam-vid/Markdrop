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

    // Repository Tree & Asset Picker State
    private val _repoAssets = MutableStateFlow<List<RepoAssetItem>>(emptyList())
    val repoAssets: StateFlow<List<RepoAssetItem>> = _repoAssets.asStateFlow()

    private val _isLoadingAssets = MutableStateFlow(false)
    val isLoadingAssets: StateFlow<Boolean> = _isLoadingAssets.asStateFlow()

    private val _assetInspectionError = MutableStateFlow<String?>(null)
    val assetInspectionError: StateFlow<String?> = _assetInspectionError.asStateFlow()

    private val _activeRepositoryTarget = MutableStateFlow(DefaultModelManifest.DEFAULT.assetConfig.primaryTarget())
    val activeRepositoryTarget: StateFlow<RepositoryTarget> = _activeRepositoryTarget.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.conversionSettingsFlow.collect { settings ->
                val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
                val model = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT
                _selectedModel.value = model
                _activeRepositoryTarget.value = model.assetConfig.primaryTarget()
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
            _activeRepositoryTarget.value = model.assetConfig.primaryTarget()
            _repoAssets.value = emptyList()
            _assetInspectionError.value = null
        }
    }

    fun selectRepositoryTarget(target: RepositoryTarget, hfToken: String? = null) {
        _activeRepositoryTarget.value = target
        inspectRepositoryTree(target, hfToken)
    }

    fun inspectRepositoryTree(target: RepositoryTarget? = null, hfToken: String? = null) {
        val repoTarget = target ?: _activeRepositoryTarget.value
        _activeRepositoryTarget.value = repoTarget
        viewModelScope.launch {
            _isLoadingAssets.value = true
            _assetInspectionError.value = null
            val result = modelManager.fetchRepoAssets(repoTarget, hfToken)
            if (result.isSuccess) {
                _repoAssets.value = result.getOrThrow()
                _assetInspectionError.value = null
            } else {
                val ex = result.exceptionOrNull()
                _repoAssets.value = emptyList()
                _assetInspectionError.value = ex?.localizedMessage ?: "Failed to inspect repository tree"
            }
            _isLoadingAssets.value = false
        }
    }

    fun saveHfToken(token: String?) {
        viewModelScope.launch {
            settingsRepository.setHuggingFaceToken(token)
        }
    }

    fun saveCustomDownloadUrl(url: String?) {
        viewModelScope.launch {
            settingsRepository.setCustomModelDownloadUrl(url)
        }
    }

    fun isRamSufficient(): Boolean {
        return modelManager.isDeviceRamSufficient(_selectedModel.value)
    }

    fun startDownload(hfToken: String? = null, customUrl: String? = null) {
        modelManager.startDownload(_selectedModel.value, hfToken, customUrl)
    }

    fun startDownloadWithAsset(asset: RepoAssetItem, target: RepositoryTarget, hfToken: String? = null) {
        modelManager.startDownload(
            manifest = _selectedModel.value,
            hfToken = hfToken,
            customUrlOverride = null,
            selectedAsset = asset,
            selectedTarget = target
        )
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
