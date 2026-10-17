package com.markdrop.app.data.settings

import kotlinx.serialization.Serializable

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Serializable
data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seedColorHex: String = "#6750A4",
    val useDynamicColor: Boolean = true,
    val useAmoled: Boolean = false,
)

@Serializable
data class InteractionSettings(
    val hapticsEnabled: Boolean = true,
)

@Serializable 
data class ConversionSettings(
    val defaultMode: String = "FAST",  // FAST, PRECISION, ADVANCED
    val localConcurrency: Int = 5,
    val cloudConcurrency: Int = 2,
    val precisionModeEnabled: Boolean = false,
    val precisionModelId: String? = null,
    val huggingFaceToken: String? = null,
    val customModelDownloadUrl: String? = null,
    val advancedModeEnabled: Boolean = false,
    val advancedModeExplainerShown: Boolean = false,
    val stripHeaderFooterEnabled: Boolean = true,
    val aiUseEmbeddedTextLayer: Boolean = false,
    val aiSource: String = "AUTO", // AUTO, CLOUD, LOCAL
    val fallbackToOcrOnError: Boolean = false
)
