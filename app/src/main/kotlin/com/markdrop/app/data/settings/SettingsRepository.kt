package com.markdrop.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "markdrop_settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SEED_COLOR_HEX = stringPreferencesKey("seed_color_hex")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_AMOLED = booleanPreferencesKey("use_amoled")
        
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val LOCAL_CONCURRENCY = intPreferencesKey("local_concurrency")
        val CLOUD_CONCURRENCY = intPreferencesKey("cloud_concurrency")
        val PRECISION_MODE_ENABLED = booleanPreferencesKey("precision_mode_enabled")
        val PRECISION_MODEL_ID = stringPreferencesKey("precision_model_id")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val CUSTOM_MODEL_DOWNLOAD_URL = stringPreferencesKey("custom_model_download_url")
        val ADVANCED_MODE_ENABLED = booleanPreferencesKey("advanced_mode_enabled")
        val ADVANCED_MODE_EXPLAINER_SHOWN = booleanPreferencesKey("advanced_mode_explainer_shown")
        val STRIP_HEADER_FOOTER_ENABLED = booleanPreferencesKey("strip_header_footer_enabled")
        val CACHED_REMOTE_MODEL_REGISTRY = stringPreferencesKey("cached_remote_model_registry")
        val SAVE_DIAGNOSTICS_ENABLED = booleanPreferencesKey("save_diagnostics_enabled")
        val AI_USE_EMBEDDED_TEXT_LAYER = booleanPreferencesKey("ai_use_embedded_text_layer")
        val AI_SOURCE = stringPreferencesKey("ai_source")
    }

    val themeSettingsFlow: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            themeMode = ThemeMode.valueOf(preferences[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            seedColorHex = preferences[Keys.SEED_COLOR_HEX] ?: "#6750A4",
            useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: true,
            useAmoled = preferences[Keys.USE_AMOLED] ?: false
        )
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences -> preferences[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setSeedColor(seedColorHex: String) {
        dataStore.edit { preferences -> preferences[Keys.SEED_COLOR_HEX] = seedColorHex }
    }

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.USE_DYNAMIC_COLOR] = useDynamicColor }
    }

    suspend fun setUseAmoled(useAmoled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.USE_AMOLED] = useAmoled }
    }

    val interactionSettingsFlow: Flow<InteractionSettings> = dataStore.data.map { preferences ->
        InteractionSettings(
            hapticsEnabled = preferences[Keys.HAPTICS_ENABLED] ?: true
        )
    }

    suspend fun setHapticsEnabled(hapticsEnabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.HAPTICS_ENABLED] = hapticsEnabled }
    }

    val conversionSettingsFlow: Flow<ConversionSettings> = dataStore.data.map { preferences ->
        ConversionSettings(
            defaultMode = preferences[Keys.DEFAULT_MODE] ?: "FAST",
            localConcurrency = preferences[Keys.LOCAL_CONCURRENCY] ?: 5,
            cloudConcurrency = preferences[Keys.CLOUD_CONCURRENCY] ?: 2,
            precisionModeEnabled = preferences[Keys.PRECISION_MODE_ENABLED] ?: false,
            precisionModelId = preferences[Keys.PRECISION_MODEL_ID],
            huggingFaceToken = preferences[Keys.HUGGING_FACE_TOKEN],
            customModelDownloadUrl = preferences[Keys.CUSTOM_MODEL_DOWNLOAD_URL],
            advancedModeEnabled = preferences[Keys.ADVANCED_MODE_ENABLED] ?: false,
            advancedModeExplainerShown = preferences[Keys.ADVANCED_MODE_EXPLAINER_SHOWN] ?: false,
            stripHeaderFooterEnabled = preferences[Keys.STRIP_HEADER_FOOTER_ENABLED] ?: true,
            aiUseEmbeddedTextLayer = preferences[Keys.AI_USE_EMBEDDED_TEXT_LAYER] ?: false,
            aiSource = preferences[Keys.AI_SOURCE] ?: "AUTO"
        )
    }

    val stripHeaderFooterEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.STRIP_HEADER_FOOTER_ENABLED] ?: true
    }

    suspend fun setStripHeaderFooterEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.STRIP_HEADER_FOOTER_ENABLED] = enabled }
    }

    val cachedRemoteModelRegistryFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.CACHED_REMOTE_MODEL_REGISTRY]
    }

    suspend fun setCachedRemoteModelRegistry(json: String) {
        dataStore.edit { preferences -> preferences[Keys.CACHED_REMOTE_MODEL_REGISTRY] = json }
    }

    val saveDiagnosticsEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SAVE_DIAGNOSTICS_ENABLED] ?: false
    }

    suspend fun setSaveDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SAVE_DIAGNOSTICS_ENABLED] = enabled }
    }

    fun getModelPricing(modelId: String): Flow<Double?> {
        val key = doublePreferencesKey("model_pricing_${modelId.replace("/", "_").lowercase()}")
        return dataStore.data.map { prefs -> prefs[key] }
    }

    suspend fun setModelPricing(modelId: String, promptPricePerToken: Double) {
        val key = doublePreferencesKey("model_pricing_${modelId.replace("/", "_").lowercase()}")
        dataStore.edit { prefs -> prefs[key] = promptPricePerToken }
    }

    suspend fun setDefaultMode(defaultMode: String) {
        dataStore.edit { preferences -> preferences[Keys.DEFAULT_MODE] = defaultMode }
    }

    suspend fun setLocalConcurrency(localConcurrency: Int) {
        dataStore.edit { preferences -> preferences[Keys.LOCAL_CONCURRENCY] = localConcurrency }
    }

    suspend fun setCloudConcurrency(cloudConcurrency: Int) {
        dataStore.edit { preferences -> preferences[Keys.CLOUD_CONCURRENCY] = cloudConcurrency }
    }

    suspend fun setPrecisionModeEnabled(precisionModeEnabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.PRECISION_MODE_ENABLED] = precisionModeEnabled }
    }
    
    suspend fun setPrecisionModelId(precisionModelId: String?) {
        dataStore.edit { preferences -> 
            if (precisionModelId != null) {
                preferences[Keys.PRECISION_MODEL_ID] = precisionModelId
            } else {
                preferences.remove(Keys.PRECISION_MODEL_ID)
            }
        }
    }

    suspend fun setHuggingFaceToken(token: String?) {
        dataStore.edit { preferences ->
            if (token.isNullOrBlank()) {
                preferences.remove(Keys.HUGGING_FACE_TOKEN)
            } else {
                preferences[Keys.HUGGING_FACE_TOKEN] = token.trim()
            }
        }
    }

    suspend fun setCustomModelDownloadUrl(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(Keys.CUSTOM_MODEL_DOWNLOAD_URL)
            } else {
                preferences[Keys.CUSTOM_MODEL_DOWNLOAD_URL] = url.trim()
            }
        }
    }

    suspend fun setAdvancedModeEnabled(advancedModeEnabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.ADVANCED_MODE_ENABLED] = advancedModeEnabled }
    }

    suspend fun setAdvancedModeExplainerShown(advancedModeExplainerShown: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.ADVANCED_MODE_EXPLAINER_SHOWN] = advancedModeExplainerShown }
    }

    fun getCachedModels(providerName: String): Flow<List<String>?> {
        val key = stringPreferencesKey("cached_models_${providerName.lowercase()}")
        return dataStore.data.map { prefs ->
            prefs[key]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        }
    }

    suspend fun setCachedModels(providerName: String, models: List<String>) {
        val key = stringPreferencesKey("cached_models_${providerName.lowercase()}")
        dataStore.edit { prefs ->
            prefs[key] = models.joinToString(",")
        }
    }

    fun getCachedModelsTimestamp(providerName: String): Flow<Long?> {
        val key = longPreferencesKey("cached_models_timestamp_${providerName.lowercase()}")
        return dataStore.data.map { prefs -> prefs[key] }
    }

    suspend fun setCachedModelsTimestamp(providerName: String, timestamp: Long) {
        val key = longPreferencesKey("cached_models_timestamp_${providerName.lowercase()}")
        dataStore.edit { prefs -> prefs[key] = timestamp }
    }

    suspend fun setAiUseEmbeddedTextLayer(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_USE_EMBEDDED_TEXT_LAYER] = enabled }
    }

    suspend fun setAiSource(aiSource: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_SOURCE] = aiSource }
    }
}
