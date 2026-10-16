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
        model: String
    ): String = withContext(Dispatchers.IO) {
        val base64Image = bitmapToBase64(bitmap)

        when (provider) {
            CloudProvider.GEMINI -> callGeminiVision(base64Image, apiKey, model)
            CloudProvider.OPENAI -> callOpenAiVision(base64Image, apiKey, model, isCustomEndpoint = false)
            CloudProvider.ANTHROPIC -> callAnthropicVision(base64Image, apiKey, model)
            CloudProvider.OPENROUTER -> callOpenAiVision(base64Image, apiKey, model, isCustomEndpoint = true)
        }
    }

    suspend fun testConnection(
        provider: CloudProvider,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = when (provider) {
                CloudProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val requestBody = buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", VisionPrompts.CONNECTION_TEST_PROMPT) }
                                }
                            }
                        }
                    }
                    val httpResponse = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.OPENAI -> {
                    val httpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                        header("Authorization", "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    put("content", VisionPrompts.CONNECTION_TEST_PROMPT)
                                }
                            }
                            put("max_tokens", 10)
                        }.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.ANTHROPIC -> {
                    val httpResponse = httpClient.post("https://api.anthropic.com/v1/messages") {
                        header("x-api-key", apiKey)
                        header("anthropic-version", "2023-06-01")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            put("max_tokens", 10)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    put("content", VisionPrompts.CONNECTION_TEST_PROMPT)
                                }
                            }
                        }.toString())
                    }
                    if (httpResponse.status.value in 200..299) "Connected successfully!"
                    else "Error ${httpResponse.status}: ${httpResponse.bodyAsText().take(200)}"
                }
                CloudProvider.OPENROUTER -> {
                    val httpResponse = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                        header("Authorization", "Bearer $apiKey")
                        header("HTTP-Referer", "https://markdrop.app")
                        header("X-Title", "Markdrop")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("model", model)
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "user")
                                    put("content", VisionPrompts.CONNECTION_TEST_PROMPT)
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

    private suspend fun callGeminiVision(base64Image: String, apiKey: String, model: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val payload = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", VisionPrompts.SYSTEM_INSTRUCTION) }
                }
            }
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", VisionPrompts.PAGE_PROMPT) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
        }

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val responseText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Gemini API error (${response.status}): $responseText")
        }

        val json = jsonConfig.parseToJsonElement(responseText).jsonObject
        val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = candidate?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from Gemini")
    }

    private suspend fun callOpenAiVision(base64Image: String, apiKey: String, model: String, isCustomEndpoint: Boolean): String {
        val endpoint = if (isCustomEndpoint) "https://openrouter.ai/api/v1/chat/completions" else "https://api.openai.com/v1/chat/completions"
        val payload = buildJsonObject {
            put("model", model)
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
                            put("text", VisionPrompts.PAGE_PROMPT)
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
            put("max_tokens", 4096)
        }

        val response = httpClient.post(endpoint) {
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
        val text = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from provider")
    }

    private suspend fun callAnthropicVision(base64Image: String, apiKey: String, model: String): String {
        val endpoint = "https://api.anthropic.com/v1/messages"
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
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
                            put("text", VisionPrompts.PAGE_PROMPT)
                        }
                    }
                }
            }
        }

        val response = httpClient.post(endpoint) {
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
        val contentArray = json["content"]?.jsonArray
        val textBlock = contentArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
        val text = textBlock?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

        return text?.cleanMarkdownOutput() ?: throw IllegalStateException("Empty response from Anthropic")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
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
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                    val response = httpClient.get(url)
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

    suspend fun repairMarkdownSegment(
        segment: String,
        repairType: String,
        provider: CloudProvider,
        apiKey: String,
        model: String = provider.defaultModel
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (repairType.equals("latex", ignoreCase = true)) {
            "You are a LaTeX and math typesetting expert. Convert any corrupted or ASCII mathematical formula in the following text into standard LaTeX: use \$...$ for inline formulas and \$\$...$$ for display block equations. Output ONLY the repaired text with no conversational preamble or markdown code blocks:\n\n$segment"
        } else {
            "You are a Markdown formatting expert. Fix and format the following table into clean, perfectly aligned GitHub-Flavored Markdown table syntax with proper column alignments. Output ONLY the repaired markdown table with no conversational preamble or backticks:\n\n$segment"
        }

        when (provider) {
            CloudProvider.GEMINI -> {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val payload = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject { put("text", prompt) }
                            }
                        }
                    }
                }
                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
                val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull?.cleanMarkdownOutput() ?: segment
            }
            CloudProvider.OPENAI, CloudProvider.OPENROUTER -> {
                val endpoint = if (provider == CloudProvider.OPENROUTER) "https://openrouter.ai/api/v1/chat/completions" else "https://api.openai.com/v1/chat/completions"
                val response = httpClient.post(endpoint) {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("model", model)
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            }
                        }
                    }.toString())
                }
                val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull?.cleanMarkdownOutput() ?: segment
            }
            CloudProvider.ANTHROPIC -> {
                val response = httpClient.post("https://api.anthropic.com/v1/messages") {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("model", model)
                        put("max_tokens", 2048)
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            }
                        }
                    }.toString())
                }
                val json = jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                json["content"]?.jsonArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.cleanMarkdownOutput() ?: segment
            }
        }
    }

    private fun String.cleanMarkdownOutput(): String {
        var trimmed = this.trim()
        if (trimmed.startsWith("```markdown", ignoreCase = true)) {
            trimmed = trimmed.removePrefix("```markdown").removePrefix("```MARKDOWN").trim()
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.removeSuffix("```").trim()
            }
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.removeSuffix("```").trim()
            }
        }
        return trimmed
    }

    override fun close() {
        httpClient.close()
    }
}
