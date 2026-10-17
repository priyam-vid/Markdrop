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

    private val _isModelVerified = MutableStateFlow(false)
    val isModelVerified: StateFlow<Boolean> = _isModelVerified.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _verificationMessage = MutableStateFlow<String?>(null)
    val verificationMessage: StateFlow<String?> = _verificationMessage.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.conversionSettingsFlow.collect { settings ->
                val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
                val model = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT
                _selectedModel.value = model
                modelManager.checkLocalModel(model)
                settingsRepository.isLocalModelVerified(modelId).collect { verified ->
                    _isModelVerified.value = verified
                }
            }
        }
        viewModelScope.launch {
            modelManager.downloadState.collect { state ->
                if (state is ModelDownloadState.Downloaded) {
                    settingsRepository.setPrecisionModeEnabled(true)
                    settingsRepository.setPrecisionModelId(_selectedModel.value.modelId)
                    runSmokeTest()
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

    fun runSmokeTest() {
        val model = _selectedModel.value
        val modelPath = modelManager.getModelPath(model)
        viewModelScope.launch {
            _isVerifying.value = true
            _verificationMessage.value = "Running vision smoke test on ${model.displayName}..."
            try {
                val engine = com.markdrop.app.conversion.local.LocalLlmEngine(
                    context = getApplication(),
                    modelPath = modelPath,
                    manifest = model
                )
                val testResult = engine.runSmokeTest()
                engine.close()
                if (testResult.isSuccess) {
                    settingsRepository.setLocalModelVerified(model.modelId, verified = true)
                    _isModelVerified.value = true
                    _verificationMessage.value = "Verified! Smoke test passed successfully."
                } else {
                    val err = testResult.exceptionOrNull()?.message ?: "Smoke test failed"
                    settingsRepository.setLocalModelVerified(model.modelId, verified = false, failureReason = err)
                    _isModelVerified.value = false
                    _verificationMessage.value = "Verification failed: $err"
                }
            } catch (e: Exception) {
                val err = e.message ?: "Verification error"
                settingsRepository.setLocalModelVerified(model.modelId, verified = false, failureReason = err)
                _isModelVerified.value = false
                _verificationMessage.value = "Verification error: $err"
            } finally {
                _isVerifying.value = false
            }
        }
    }

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
