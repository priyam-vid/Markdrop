package com.markdrop.app.conversion.cloud

import android.graphics.Bitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.TimeUnit
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.delay

sealed class VisionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Auth(message: String) : VisionException(message)
    class RateLimited(val retryAfterMs: Long, message: String) : VisionException(message)
    class Overloaded(message: String) : VisionException(message)
    class ContentBlocked(val reason: String) : VisionException("Content blocked by provider safety filters: $reason")
    class Truncated(val partialText: String, message: String) : VisionException(message)
    class Network(message: String, cause: Throwable? = null) : VisionException(message, cause)
    class BadRequest(message: String) : VisionException(message)
}

class CloudVisionClient : Closeable {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(180, TimeUnit.SECONDS)
                writeTimeout(120, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }

    suspend fun transcribePage(
        bitmap: Bitmap,
        provider: CloudProvider,
        apiKey: String,
        model: String,
        pageNumber: Int = 1,
        totalPages: Int = 1,
        previousTail: String? = null
    ): String = withContext(Dispatchers.IO) {
        val base64Image = bitmapToBase64(bitmap)
        val userPrompt = VisionPrompts.buildUserPrompt(pageNumber, totalPages, previousTail)

        when (provider) {
            CloudProvider.GEMINI -> callGeminiVision(base64Image, apiKey, model, userPrompt)
            CloudProvider.OPENAI -> callOpenAiVision(base64Image, apiKey, model, userPrompt, isCustomEndpoint = false)
            CloudProvider.ANTHROPIC -> callAnthropicVision(base64Image, apiKey, model, userPrompt)
            CloudProvider.OPENROUTER -> callOpenAiVision(base64Image, apiKey, model, userPrompt, isCustomEndpoint = true)
        }
    }

    private fun createTestBitmap(): Bitmap {
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
    }

    private suspend fun postWithRetry(
        url: String,
        builder: HttpRequestBuilder.() -> Unit
    ): io.ktor.client.statement.HttpResponse {
        val maxRetries = 4
        var attempt = 0
        while (true) {
            val response = try {
                httpClient.post(url, builder)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (attempt < maxRetries) {
                    attempt++
                    val delayMs = (1000L shl attempt) + (0..500).random()
                    Log.w("CloudVisionClient", "Network error: ${e.message}. Retrying in ${delayMs}ms ($attempt/$maxRetries)")
                    delay(delayMs)
                    continue
                }
                throw VisionException.Network("Network failure connecting to vision provider: ${e.message}", e)
            }

            when (response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                    val body = response.bodyAsText().take(200)
                    throw VisionException.Auth("Authentication failed (${response.status.value}): $body")
                }
                HttpStatusCode.BadRequest -> {
                    val body = response.bodyAsText().take(200)
                    throw VisionException.BadRequest("Bad request error (${response.status.value}): $body")
                }
                HttpStatusCode.TooManyRequests -> {
                    if (attempt < maxRetries) {
                        attempt++
                        val retryAfterSec = response.headers["Retry-After"]?.toLongOrNull() ?: (1L shl attempt)
                        val delayMs = (retryAfterSec.coerceIn(1, 60)) * 1000L + (0..500).random()
                        Log.w("CloudVisionClient", "429 Rate limited. Waiting ${delayMs}ms before retry $attempt of $maxRetries")
                        delay(delayMs)
                        continue
                    }
                    throw VisionException.RateLimited(60000L, "Rate limit exceeded after $maxRetries attempts")
                }
                HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway,
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> {
                    if (attempt < maxRetries) {
                        attempt++
                        val delayMs = (1000L shl attempt) + (0..500).random()
                        Log.w("CloudVisionClient", "Provider overloaded (${response.status.value}). Waiting ${delayMs}ms before retry $attempt of $maxRetries")
                        delay(delayMs)
                        continue
                    }
                    throw VisionException.Overloaded("Provider server temporarily overloaded (${response.status.value})")
                }
                else -> return response
            }
        }
    }

    suspend fun testConnection(
        provider: CloudProvider,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val testBase64 = bitmapToBase64(createTestBitmap())
            val response = when (provider) {
                CloudProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                    val requestBody = buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", VisionPrompts.CONNECTION_TEST_PROMPT) }
                                    addJsonObject {
                                        putJsonObject("inline_data") {
                                            put("mime_type", "image/jpeg")
                                            put("data", testBase64)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val httpResponse = postWithRetry(url) {
                        header("x-goog-api-key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.OPENAI -> {
                    val httpResponse = postWithRetry("https://api.openai.com/v1/chat/completions") {
                        header("Authorization", "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", VisionPrompts.CONNECTION_TEST_PROMPT)
                                        }
                                        addJsonObject {
                                            put("type", "image_url")
                                            putJsonObject("image_url") {
                                                put("url", "data:image/jpeg;base64,$testBase64")
                                                put("detail", "low")
                                            }
                                        }
                                    }
                                }
                            }
                            if (model.startsWith("o1") || model.startsWith("o3")) {
                                put("max_completion_tokens", 10)
                            } else {
                                put("max_tokens", 10)
                            }
                        }.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.ANTHROPIC -> {
                    val httpResponse = postWithRetry("https://api.anthropic.com/v1/messages") {
                        header("x-api-key", apiKey)
                        header("anthropic-version", "2023-06-01")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            put("max_tokens", 10)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", "image/jpeg")
                                                put("data", testBase64)
                                            }
                                        }
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", VisionPrompts.CONNECTION_TEST_PROMPT)
                                        }
                                    }
                                }
                            }
                        }.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.OPENROUTER -> {
                    val httpResponse = postWithRetry("https://openrouter.ai/api/v1/chat/completions") {
                        header("Authorization", "Bearer $apiKey")
                        header("HTTP-Referer", "https://markdrop.app")
                        header("X-Title", "Markdrop")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", VisionPrompts.CONNECTION_TEST_PROMPT)
                                        }
                                        addJsonObject {
                                            put("type", "image_url")
                                            putJsonObject("image_url") {
                                                put("url", "data:image/jpeg;base64,$testBase64")
                                                put("detail", "low")
                                            }
                                        }
                                    }
                                }
                            }
                            put("max_tokens", 10)
                        }.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callGeminiVision(
        base64Image: String,
        apiKey: String,
        model: String,
        userPrompt: String = VisionPrompts.PAGE_PROMPT,
        maxTokens: Int = 4096
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val payload = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", VisionPrompts.SYSTEM_INSTRUCTION) }
                }
            }
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", userPrompt) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.0)
                put("maxOutputTokens", maxTokens)
            }
        }

        val response = postWithRetry(url) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val responseText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Gemini API error (${response.status}): $responseText")
        }

        val json = jsonConfig.parseToJsonElement(responseText).jsonObject
        val blockReason = json["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (blockReason != null) {
            throw VisionException.ContentBlocked(blockReason)
        }

        val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
        if (finishReason == "MAX_TOKENS") {
            if (maxTokens < 8192) {
                Log.w("CloudVisionClient", "Gemini output truncated. Re-requesting with 8192 token limit...")
                return callGeminiVision(base64Image, apiKey, model, userPrompt, maxTokens = 8192)
            } else {
                Log.w("CloudVisionClient", "Gemini output truncated at maximum 8192 token limit.")
            }
        }

        val text = candidate?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from Gemini")
    }

    private suspend fun callOpenAiVision(
        base64Image: String,
        apiKey: String,
        model: String,
        userPrompt: String = VisionPrompts.PAGE_PROMPT,
        isCustomEndpoint: Boolean,
        maxTokens: Int = 4096
    ): String {
        val endpoint = if (isCustomEndpoint) "https://openrouter.ai/api/v1/chat/completions" else "https://api.openai.com/v1/chat/completions"
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.0)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", VisionPrompts.SYSTEM_INSTRUCTION)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64Image")
                                put("detail", "high")
                            }
                        }
                    }
                }
            }
            if (!isCustomEndpoint && (model.startsWith("o1") || model.startsWith("o3"))) {
                put("max_completion_tokens", maxTokens)
            } else {
                put("max_tokens", maxTokens)
            }
        }

        val response = postWithRetry(endpoint) {
            header("Authorization", "Bearer $apiKey")
            if (isCustomEndpoint) {
                header("HTTP-Referer", "https://markdrop.app")
                header("X-Title", "Markdrop")
            }
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val responseText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("API error (${response.status}): $responseText")
        }

        val json = jsonConfig.parseToJsonElement(responseText).jsonObject
        val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
        if (finishReason == "length") {
            if (maxTokens < 8192) {
                Log.w("CloudVisionClient", "OpenAI/OpenRouter output truncated. Re-requesting with 8192 token limit...")
                return callOpenAiVision(base64Image, apiKey, model, userPrompt, isCustomEndpoint, maxTokens = 8192)
            } else {
                Log.w("CloudVisionClient", "OpenAI/OpenRouter output truncated at maximum 8192 token limit.")
            }
        }
        val text = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from provider")
    }

    private suspend fun callAnthropicVision(
        base64Image: String,
        apiKey: String,
        model: String,
        userPrompt: String = VisionPrompts.PAGE_PROMPT,
        maxTokens: Int = 4096
    ): String {
        val endpoint = "https://api.anthropic.com/v1/messages"
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.0)
            put("system", VisionPrompts.SYSTEM_INSTRUCTION)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
                        }
                    }
                }
            }
        }

        val response = postWithRetry(endpoint) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val responseText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Anthropic API error (${response.status}): $responseText")
        }

        val json = jsonConfig.parseToJsonElement(responseText).jsonObject
        val stopReason = json["stop_reason"]?.jsonPrimitive?.contentOrNull
        if (stopReason == "max_tokens") {
            if (maxTokens < 8192) {
                Log.w("CloudVisionClient", "Anthropic output truncated. Re-requesting with 8192 token limit...")
                return callAnthropicVision(base64Image, apiKey, model, userPrompt, maxTokens = 8192)
            } else {
                Log.w("CloudVisionClient", "Anthropic output truncated at maximum 8192 token limit.")
            }
        }
        val contentArray = json["content"]?.jsonArray
        val textBlock = contentArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
        val text = textBlock?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from Anthropic")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    data class ModelDiscoveryResult(
        val models: List<String>,
        val pricing: Map<String, Double> = emptyMap()
    )

    suspend fun fetchAvailableModels(
        provider: CloudProvider,
        apiKey: String,
        deprecatedModelIds: List<String> = emptyList()
    ): Result<List<String>> = fetchAvailableModelsWithPricing(provider, apiKey, deprecatedModelIds).map { it.models }

    suspend fun fetchAvailableModelsWithPricing(
        provider: CloudProvider,
        apiKey: String,
        deprecatedModelIds: List<String> = emptyList()
    ): Result<ModelDiscoveryResult> = withContext(Dispatchers.IO) {
        try {
            val pricingMap = mutableMapOf<String, Double>()
            val models = when (provider) {
                CloudProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models"
                    val response = httpClient.get(url) {
                        header("x-goog-api-key", apiKey)
                    }
                    if (response.status.value !in 200..299) {
                        throw IllegalStateException("Gemini API error ${response.status}: ${response.bodyAsText().take(120)}")
                    }
                    val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                    val modelsArray = json["models"]?.jsonArray ?: JsonArray(emptyList())
                    val list = modelsArray.mapNotNull { element ->
                        val obj = element.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
                        val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                        if (name != null && methods.contains("generateContent")) {
                            name
                        } else null
                    }.filter { name ->
                        !name.contains("embedding", ignoreCase = true) &&
                        !name.contains("aqa", ignoreCase = true) &&
                        !name.contains("imagen", ignoreCase = true) &&
                        !name.contains("bison", ignoreCase = true) &&
                        !deprecatedModelIds.contains(name)
                    }
                    list.ifEmpty { provider.availableModels }
                }
                CloudProvider.OPENAI -> {
                    val url = "https://api.openai.com/v1/models"
                    val response = httpClient.get(url) {
                        header("Authorization", "Bearer $apiKey")
                    }
                    if (response.status.value !in 200..299) {
                        throw IllegalStateException("OpenAI API error ${response.status}: ${response.bodyAsText().take(120)}")
                    }
                    val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                    val dataArray = json["data"]?.jsonArray ?: JsonArray(emptyList())
                    val list = dataArray.mapNotNull { element ->
                        element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    }.filter { id ->
                        (id.startsWith("gpt-4o") || id.startsWith("chatgpt-4o") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("gpt-4-turbo")) &&
                        !deprecatedModelIds.contains(id)
                    }.sortedDescending()
                    list.ifEmpty { provider.availableModels }
                }
                CloudProvider.OPENROUTER -> {
                    val url = "https://openrouter.ai/api/v1/models"
                    val response = httpClient.get(url) {
                        header("Authorization", "Bearer $apiKey")
                    }
                    if (response.status.value !in 200..299) {
                        throw IllegalStateException("OpenRouter API error ${response.status}: ${response.bodyAsText().take(120)}")
                    }
                    val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                    val dataArray = json["data"]?.jsonArray ?: JsonArray(emptyList())
                    val list = dataArray.mapNotNull { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        if (deprecatedModelIds.contains(id)) return@mapNotNull null

                        val arch = obj["architecture"]?.jsonObject
                        val modality = arch?.get("modality")?.jsonPrimitive?.contentOrNull ?: ""
                        val isMultimodal = modality.contains("image->text", ignoreCase = true) ||
                                           modality.contains("text+image->text", ignoreCase = true) ||
                                           modality.contains("multimodal", ignoreCase = true) ||
                                           id.contains("vision", ignoreCase = true) ||
                                           id.contains("gemini", ignoreCase = true) ||
                                           id.contains("claude", ignoreCase = true) ||
                                           id.contains("gpt-4", ignoreCase = true) ||
                                           id.contains("flash", ignoreCase = true) ||
                                           id.contains("vl", ignoreCase = true)

                        if (isMultimodal) {
                            val promptPrice = obj["pricing"]?.jsonObject?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            if (promptPrice != null) {
                                pricingMap[id] = promptPrice
                            }
                            id
                        } else null
                    }
                    list.ifEmpty { provider.availableModels }
                }
                CloudProvider.ANTHROPIC -> {
                    try {
                        val url = "https://api.anthropic.com/v1/models"
                        val response = httpClient.get(url) {
                            header("x-api-key", apiKey)
                            header("anthropic-version", "2023-06-01")
                        }
                        if (response.status.value in 200..299) {
                            val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                            val data = json["data"]?.jsonArray ?: JsonArray(emptyList())
                            val list = data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                                .filter { it.contains("claude", ignoreCase = true) && !deprecatedModelIds.contains(it) }
                            if (list.isNotEmpty()) list else provider.availableModels
                        } else {
                            provider.availableModels
                        }
                    } catch (_: Exception) {
                        provider.availableModels
                    }
                }
            }
            Result.success(ModelDiscoveryResult(models = models, pricing = pricingMap))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun String.cleanMarkdownOutput(): String {
        return cleanMarkdownOuterWrapper(this)
    }

    override fun close() {
        httpClient.close()
    }
}

/**
 * Strips outer markdown code fences IF AND ONLY IF they enclose the entire response.
 * Legitimate code fences within the document or at the top of a page (without an enclosing wrapper)
 * are carefully preserved.
 */
fun cleanMarkdownOuterWrapper(text: String): String {
    val trimmed = text.trim()
    val match = Regex("""^```(?:markdown)?\r?\n([\s\S]*?)\r?\n```$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    return if (match != null) {
        match.groupValues[1].trim()
    } else {
        trimmed
    }
}
