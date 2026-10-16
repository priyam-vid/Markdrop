package com.markdrop.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.conversion.cloud.CloudProvider
import com.markdrop.app.conversion.cloud.CloudVisionClient
import com.markdrop.app.data.security.SecureKeyStore
import com.markdrop.app.data.settings.SettingsRepository
import com.markdrop.app.model.RemoteModelRegistryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.firstOrNull

sealed class TestConnectionStatus {
    object Idle : TestConnectionStatus()
    object Testing : TestConnectionStatus()
    data class Success(val message: String) : TestConnectionStatus()
    data class Error(val error: String) : TestConnectionStatus()
}

data class ProviderState(
    val provider: CloudProvider,
    val apiKey: String,
    val selectedModel: String,
    val hasKey: Boolean,
    val availableModels: List<String> = provider.availableModels,
    val isSyncingModels: Boolean = false,
    val syncTimestamp: Long? = null,
    val syncError: String? = null,
    val testStatus: TestConnectionStatus = TestConnectionStatus.Idle
)

data class AdvancedModeUiState(
    val activeProvider: CloudProvider = CloudProvider.GEMINI,
    val providerStates: Map<CloudProvider, ProviderState> = emptyMap(),
    val showExplainerDialog: Boolean = false,
    val isAdvancedModeEnabled: Boolean = false
)

class AdvancedModeViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) : AndroidViewModel(application) {

    private val visionClient = CloudVisionClient()

    private val _uiState = MutableStateFlow(AdvancedModeUiState())
    val uiState: StateFlow<AdvancedModeUiState> = _uiState.asStateFlow()

    init {
        loadStates()
    }

    private fun loadStates() {
        viewModelScope.launch {
            val states = CloudProvider.entries.associateWith { provider ->
                val cached = settingsRepository.getCachedModels(provider.name).firstOrNull()
                val timestamp = settingsRepository.getCachedModelsTimestamp(provider.name).firstOrNull()
                val models = if (!cached.isNullOrEmpty()) cached else provider.availableModels

                ProviderState(
                    provider = provider,
                    apiKey = secureKeyStore.getApiKey(provider) ?: "",
                    selectedModel = secureKeyStore.getSelectedModel(provider),
                    hasKey = secureKeyStore.hasValidKey(provider),
                    availableModels = models,
                    syncTimestamp = timestamp
                )
            }

            val active = secureKeyStore.getSelectedProvider()
            val hasAnyKey = secureKeyStore.hasAnyConfiguredProvider()

            _uiState.value = AdvancedModeUiState(
                activeProvider = active,
                providerStates = states,
                isAdvancedModeEnabled = hasAnyKey
            )
        }
    }

    fun syncModels(provider: CloudProvider) {
        val state = _uiState.value.providerStates[provider] ?: return
        val key = state.apiKey.ifBlank { secureKeyStore.getApiKey(provider) ?: "" }
        if (key.isBlank()) return

        val currentStates = _uiState.value.providerStates.toMutableMap()
        currentStates[provider] = state.copy(isSyncingModels = true, syncError = null)
        _uiState.value = _uiState.value.copy(providerStates = currentStates)

        viewModelScope.launch {
            val registry = RemoteModelRegistryManager.getRegistry(settingsRepository)
            val result = visionClient.fetchAvailableModelsWithPricing(provider, key, registry.deprecated_model_ids)
            val updated = _uiState.value.providerStates.toMutableMap()
            val item = updated[provider] ?: return@launch

            result.onSuccess { discovery ->
                val fetchedModels = discovery.models
                val now = System.currentTimeMillis()
                settingsRepository.setCachedModels(provider.name, fetchedModels)
                settingsRepository.setCachedModelsTimestamp(provider.name, now)

                // Save pricing per token
                discovery.pricing.forEach { (modelId, promptPrice) ->
                    settingsRepository.setModelPricing(modelId, promptPrice)
                }

                // If selectedModel is not in the new list, pick the first one or default
                val newSelected = if (fetchedModels.contains(item.selectedModel)) {
                    item.selectedModel
                } else {
                    fetchedModels.firstOrNull() ?: item.selectedModel
                }
                secureKeyStore.saveSelectedModel(provider, newSelected)

                updated[provider] = item.copy(
                    availableModels = fetchedModels,
                    selectedModel = newSelected,
                    isSyncingModels = false,
                    syncTimestamp = now,
                    syncError = null
                )
                _uiState.value = _uiState.value.copy(providerStates = updated)
            }.onFailure { error ->
                updated[provider] = item.copy(
                    isSyncingModels = false,
                    syncError = error.localizedMessage ?: "Sync failed"
                )
                _uiState.value = _uiState.value.copy(providerStates = updated)
            }
        }
    }

    fun setActiveProvider(provider: CloudProvider) {
        secureKeyStore.saveSelectedProvider(provider)
        _uiState.value = _uiState.value.copy(activeProvider = provider)
    }

    fun updateApiKeyInput(provider: CloudProvider, key: String) {
        val currentStates = _uiState.value.providerStates.toMutableMap()
        val current = currentStates[provider] ?: return
        currentStates[provider] = current.copy(apiKey = key, testStatus = TestConnectionStatus.Idle)
        _uiState.value = _uiState.value.copy(providerStates = currentStates)
    }

    fun selectModel(provider: CloudProvider, model: String) {
        secureKeyStore.saveSelectedModel(provider, model)
        val currentStates = _uiState.value.providerStates.toMutableMap()
        val current = currentStates[provider] ?: return
        currentStates[provider] = current.copy(selectedModel = model)
        _uiState.value = _uiState.value.copy(providerStates = currentStates)
    }

    fun testConnection(provider: CloudProvider) {
        val state = _uiState.value.providerStates[provider] ?: return
        val key = state.apiKey.ifBlank { secureKeyStore.getApiKey(provider) ?: "" }
        if (key.isBlank()) return

        val currentStates = _uiState.value.providerStates.toMutableMap()
        currentStates[provider] = state.copy(testStatus = TestConnectionStatus.Testing)
        _uiState.value = _uiState.value.copy(providerStates = currentStates)

        viewModelScope.launch {
            val result = visionClient.testConnection(provider, key, state.selectedModel)
            val updated = _uiState.value.providerStates.toMutableMap()
            val item = updated[provider] ?: return@launch

            result.onSuccess { msg ->
                secureKeyStore.saveApiKey(provider, key)
                secureKeyStore.saveSelectedModel(provider, state.selectedModel)
                updated[provider] = item.copy(
                    testStatus = TestConnectionStatus.Success(msg),
                    hasKey = true
                )
                _uiState.value = _uiState.value.copy(
                    providerStates = updated,
                    isAdvancedModeEnabled = true
                )
                settingsRepository.setAdvancedModeEnabled(true)
            }.onFailure { err ->
                updated[provider] = item.copy(
                    testStatus = TestConnectionStatus.Error(err.localizedMessage ?: "Connection failed")
                )
                _uiState.value = _uiState.value.copy(providerStates = updated)
            }
        }
    }

    fun removeKey(provider: CloudProvider) {
        secureKeyStore.removeApiKey(provider)
        val currentStates = _uiState.value.providerStates.toMutableMap()
        val current = currentStates[provider] ?: return
        currentStates[provider] = current.copy(
            apiKey = "",
            hasKey = false,
            testStatus = TestConnectionStatus.Idle
        )
        val hasAny = secureKeyStore.hasAnyConfiguredProvider()
        _uiState.value = _uiState.value.copy(
            providerStates = currentStates,
            isAdvancedModeEnabled = hasAny
        )
        viewModelScope.launch {
            settingsRepository.setAdvancedModeEnabled(hasAny)
        }
    }

    fun dismissExplainer(acknowledged: Boolean) {
        _uiState.value = _uiState.value.copy(showExplainerDialog = false)
        if (acknowledged) {
            viewModelScope.launch {
                settingsRepository.setAdvancedModeExplainerShown(true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        visionClient.close()
    }

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository,
        private val secureKeyStore: SecureKeyStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdvancedModeViewModel(application, settingsRepository, secureKeyStore) as T
        }
    }
}
