package com.markdrop.app.model

import com.markdrop.app.data.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemotePrecisionModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val sizeMb: Long,
    val minRamGb: Double,
    val runtime: String = "android_litert",
    val active: Boolean = true
)

@Serializable
data class ByokProvidersEndpoints(
    val gemini_discovery_endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models",
    val openrouter_discovery_endpoint: String = "https://openrouter.ai/api/v1/models"
)

@Serializable
data class RemoteModelRegistry(
    val precision_models: List<RemotePrecisionModel> = emptyList(),
    val byok_providers: ByokProvidersEndpoints = ByokProvidersEndpoints(),
    val deprecated_model_ids: List<String> = emptyList()
)

object RemoteModelRegistryManager {
    private const val REMOTE_REGISTRY_URL = "https://raw.githubusercontent.com/litert-community/models-registry/main/models_v1.json"

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val EMBEDDED_FALLBACK = RemoteModelRegistry(
        precision_models = listOf(
            RemotePrecisionModel(
                id = "gemma-4-E2B-it",
                displayName = "Gemma 4 E2B (Multimodal, Official Google AI Edge)",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                sizeMb = 2641L,
                minRamGb = 8.0,
                runtime = "android_litert",
                active = true
            ),
            RemotePrecisionModel(
                id = "gemma-3n-E2B-it",
                displayName = "Gemma 3n E2B (Multimodal Vision + Audio)",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm?download=true",
                sizeMb = 3737L,
                minRamGb = 8.0,
                runtime = "android_litert",
                active = true
            ),
            RemotePrecisionModel(
                id = "gemma3-270m-it",
                displayName = "Gemma 3 270M (Text-only, Fast)",
                downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task?download=true",
                sizeMb = 290L,
                minRamGb = 3.0,
                runtime = "android_litert",
                active = true
            ),
            RemotePrecisionModel(
                id = "gemma2-2b-it-int4",
                displayName = "Gemma 2 2B (Text-only)",
                downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2_q8_multi-prefill-seq_ekv1280.task?download=true",
                sizeMb = 2577L,
                minRamGb = 6.0,
                runtime = "android_litert",
                active = true
            ),
            RemotePrecisionModel(
                id = "gemma3-4b-it-int4",
                displayName = "Gemma 3 4B (Multimodal, Mirror: Gemma 3n E4B)",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm?download=true",
                sizeMb = 5026L,
                minRamGb = 8.0,
                runtime = "android_litert",
                active = true
            )
        ),
        byok_providers = ByokProvidersEndpoints(),
        deprecated_model_ids = listOf(
            "paligemma2-3b-pt-224",
            "paligemma2-3b-pt-448",
            "smolvlm2-500m",
            "google/gemma-3-4b-it-litert",
            "gemini-1.0-pro",
            "gemini-1.5-pro-preview-0409",
            "text-bison-001",
            "chat-bison-001",
            "embedding-gecko-001"
        )
    )

    suspend fun getRegistry(
        settingsRepository: SettingsRepository,
        httpClient: HttpClient? = null,
        forceRefresh: Boolean = false
    ): RemoteModelRegistry = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cachedJson = settingsRepository.cachedRemoteModelRegistryFlow.firstOrNull()
            if (!cachedJson.isNullOrBlank()) {
                try {
                    return@withContext jsonConfig.decodeFromString<RemoteModelRegistry>(cachedJson)
                } catch (_: Exception) {}
            }
        }

        if (httpClient != null) {
            try {
                val response = httpClient.get(REMOTE_REGISTRY_URL)
                if (response.status.value in 200..299) {
                    val body = response.bodyAsText()
                    val parsed = jsonConfig.decodeFromString<RemoteModelRegistry>(body)
                    settingsRepository.setCachedRemoteModelRegistry(body)
                    return@withContext parsed
                }
            } catch (_: Exception) {}
        }

        val cachedJson = settingsRepository.cachedRemoteModelRegistryFlow.firstOrNull()
        if (!cachedJson.isNullOrBlank()) {
            try {
                return@withContext jsonConfig.decodeFromString<RemoteModelRegistry>(cachedJson)
            } catch (_: Exception) {}
        }

        EMBEDDED_FALLBACK
    }
}
