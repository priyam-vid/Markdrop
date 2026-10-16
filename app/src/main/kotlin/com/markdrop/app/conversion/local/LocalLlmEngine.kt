package com.markdrop.app.conversion.local

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.markdrop.app.conversion.cloud.VisionPrompts
import com.markdrop.app.model.LiteRtArchiveValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class IncompatibleTokenizerException(
    message: String = "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma).",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class LocalLlmEngine(
    private val context: Context,
    private val modelPath: String
) : Closeable {

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath).absoluteFile
            if (!file.exists() || !file.canRead() || file.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Model file not found or unreadable at: ${file.absolutePath}"))
            }

            // Verify zip magic bytes and SentencePiece tokenizer for LiteRT tasks
            val isZipCandidate = file.name.endsWith(".task", ignoreCase = true) ||
                    file.name.endsWith(".litertlm", ignoreCase = true)

            if (isZipCandidate) {
                val validation = LiteRtArchiveValidator.validate(file)
                if (!validation.isValid) {
                    if (!validation.hasValidZipHeader) {
                        file.delete()
                        return@withContext Result.failure(
                            IllegalStateException(
                                "Model archive at ${file.absolutePath} is corrupted or not a valid LiteRT task ZIP archive. Corrupt file has been removed; please re-download."
                            )
                        )
                    }
                    if (!validation.hasSentencePieceTokenizer) {
                        return@withContext Result.failure(
                            IncompatibleTokenizerException(
                                validation.errorMessage
                                    ?: "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma)."
                            )
                        )
                    }
                    return@withContext Result.failure(
                        IllegalStateException(validation.errorMessage ?: "Invalid LiteRT task archive.")
                    )
                }
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(2048)
                .setMaxTopK(40)
                .build()

            try {
                llmInference = LlmInference.createFromOptions(context, options)
                isInitialized = true
                Result.success(Unit)
            } catch (e: IllegalArgumentException) {
                val msg = e.message.orEmpty()
                if (msg.contains("SentencePiece", ignoreCase = true) || msg.contains("tokenizer", ignoreCase = true)) {
                    Result.failure(
                        IncompatibleTokenizerException(
                            "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma).",
                            e
                        )
                    )
                } else {
                    Result.failure(IllegalStateException("Failed to initialize local AI engine: ${e.localizedMessage}", e))
                }
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                if (msg.contains("SentencePiece", ignoreCase = true) || msg.contains("tokenizer", ignoreCase = true)) {
                    Result.failure(
                        IncompatibleTokenizerException(
                            "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma).",
                            e
                        )
                    )
                } else {
                    Result.failure(IllegalStateException("Failed to initialize local AI engine: ${e.localizedMessage}", e))
                }
            }
        } catch (e: IncompatibleTokenizerException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(IllegalStateException("Failed to initialize local AI engine: ${e.localizedMessage}", e))
        }
    }

    suspend fun transcribePage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (!isInitialized || llmInference == null) {
            initialize().getOrThrow()
        }

        try {
            val prompt = "${VisionPrompts.SYSTEM_INSTRUCTION}\n\n${VisionPrompts.PAGE_PROMPT}"
            val response = llmInference?.generateResponse(prompt)
                ?: throw IllegalStateException("Local AI model returned null response.")

            response.trim()
        } catch (e: IncompatibleTokenizerException) {
            throw e
        } catch (e: Throwable) {
            val msg = e.message.orEmpty()
            if (msg.contains("SentencePiece", ignoreCase = true) || msg.contains("tokenizer", ignoreCase = true)) {
                throw IncompatibleTokenizerException(
                    "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma).",
                    e
                )
            }
            throw IllegalStateException("Local AI inference failed: ${e.localizedMessage}", e)
        }
    }

    override fun close() {
        try {
            llmInference?.close()
        } catch (_: Exception) {}
        llmInference = null
        isInitialized = false
    }
}
