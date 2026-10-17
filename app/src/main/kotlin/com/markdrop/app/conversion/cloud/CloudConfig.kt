package com.markdrop.app.conversion.cloud

import com.markdrop.app.data.security.SecureKeyStore

data class ResolvedCloudConfig(
    val provider: CloudProvider,
    val model: String,
    val apiKey: String
)

sealed class CloudConfigError(message: String) : IllegalStateException(message) {
    class MissingKey(val provider: CloudProvider) :
        CloudConfigError("API key for ${provider.displayName} is missing. Please add your key in Settings.")
    class EmptyKey(val provider: CloudProvider) :
        CloudConfigError("API key for ${provider.displayName} is empty. Please configure a valid key in Settings.")
}

object CloudConfig {
    fun resolveKey(provider: CloudProvider, key: String?): Result<String> {
        val trimmed = key?.trim()
        return if (trimmed.isNullOrEmpty()) {
            Result.failure(CloudConfigError.MissingKey(provider))
        } else {
            Result.success(trimmed)
        }
    }

    fun resolve(secureKeyStore: SecureKeyStore): Result<ResolvedCloudConfig> {
        val provider = secureKeyStore.getSelectedProvider()
        val keyResult = resolveKey(provider, secureKeyStore.getApiKey(provider))
        return keyResult.map { validKey ->
            val model = secureKeyStore.getSelectedModel(provider)
            ResolvedCloudConfig(provider, model, validKey)
        }
    }
}
