package com.markdrop.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.markdrop.app.conversion.cloud.CloudProvider

class SecureKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "markdrop_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Handle potential Keystore corruption on some OEM updates
        context.deleteSharedPreferences("markdrop_secure_prefs")
        EncryptedSharedPreferences.create(
            context,
            "markdrop_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(provider: CloudProvider, apiKey: String) {
        sharedPreferences.edit()
            .putString(getProviderKey(provider), apiKey.trim())
            .apply()
    }

    fun getApiKey(provider: CloudProvider): String? {
        val key = sharedPreferences.getString(getProviderKey(provider), null)
        return if (!key.isNullOrBlank()) key else null
    }

    fun removeApiKey(provider: CloudProvider) {
        sharedPreferences.edit()
            .remove(getProviderKey(provider))
            .apply()
    }

    fun hasValidKey(provider: CloudProvider): Boolean {
        return !getApiKey(provider).isNullOrBlank()
    }

    fun saveSelectedModel(provider: CloudProvider, model: String) {
        sharedPreferences.edit()
            .putString(getModelKey(provider), model)
            .apply()
    }

    fun getSelectedModel(provider: CloudProvider): String {
        return sharedPreferences.getString(getModelKey(provider), null) ?: provider.defaultModel
    }

    fun saveSelectedProvider(provider: CloudProvider) {
        sharedPreferences.edit()
            .putString("selected_cloud_provider", provider.name)
            .apply()
    }

    fun getSelectedProvider(): CloudProvider {
        val name = sharedPreferences.getString("selected_cloud_provider", null)
        return name?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() } ?: CloudProvider.GEMINI
    }

    fun hasAnyConfiguredProvider(): Boolean {
        return CloudProvider.entries.any { hasValidKey(it) }
    }

    private fun getProviderKey(provider: CloudProvider): String = "api_key_${provider.name.lowercase()}"
    private fun getModelKey(provider: CloudProvider): String = "selected_model_${provider.name.lowercase()}"
}
