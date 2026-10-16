package com.markdrop.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.data.settings.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeSettings: ThemeSettings = ThemeSettings(),
    val interactionSettings: InteractionSettings = InteractionSettings(),
    val conversionSettings: ConversionSettings = ConversionSettings(),
    val saveDiagnosticsEnabled: Boolean = false,
)

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeSettingsFlow,
        settingsRepository.interactionSettingsFlow,
        settingsRepository.conversionSettingsFlow,
        settingsRepository.saveDiagnosticsEnabledFlow,
    ) { theme, interaction, conversion, saveDiagnostics ->
        SettingsUiState(theme, interaction, conversion, saveDiagnostics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
    
    fun setSaveDiagnosticsEnabled(enabled: Boolean) { 
        com.markdrop.app.diagnostics.PipelineTrace.isDiagnosticsEnabled = enabled
        viewModelScope.launch { settingsRepository.setSaveDiagnosticsEnabled(enabled) } 
    }
    
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settingsRepository.setThemeMode(mode) } }
    fun setSeedColor(hex: String) { viewModelScope.launch { settingsRepository.setSeedColor(hex) } }
    fun setUseDynamicColor(enabled: Boolean) { viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) } }
    fun setUseAmoled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setUseAmoled(enabled) } }
    fun setHapticsEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) } }
    fun setDefaultMode(mode: String) { viewModelScope.launch { settingsRepository.setDefaultMode(mode) } }
    fun setLocalConcurrency(value: Int) { viewModelScope.launch { settingsRepository.setLocalConcurrency(value) } }
    fun setCloudConcurrency(value: Int) { viewModelScope.launch { settingsRepository.setCloudConcurrency(value) } }
    fun setPrecisionModeEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setPrecisionModeEnabled(enabled) } }
    fun setAdvancedModeEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setAdvancedModeEnabled(enabled) } }
    fun setAdvancedModeExplainerShown(shown: Boolean) { viewModelScope.launch { settingsRepository.setAdvancedModeExplainerShown(shown) } }
    fun setStripHeaderFooterEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setStripHeaderFooterEnabled(enabled) } }
    
    class Factory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository) as T
        }
    }
}
