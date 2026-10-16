package com.markdrop.app.conversion.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.markdrop.app.conversion.cloud.VisionPrompts
import com.markdrop.app.model.LiteRtArchiveValidator
import com.markdrop.app.model.ModelManifest
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
    private val modelPath: String,
    private val manifest: ModelManifest? = null
) : Closeable {

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (manifest != null && !manifest.supportsVision) {
                return@withContext Result.failure(
                    IllegalStateException("Model '${manifest.displayName}' is text-only and does not support image transcription. Please select a vision-capable model.")
                )
            }

            val file = File(modelPath).absoluteFile
            if (!file.exists() || !file.canRead() || file.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Model file not found or unreadable at: ${file.absolutePath}"))
            }

            val isZipCandidate = file.name.endsWith(".task", ignoreCase = true) ||
                    file.name.endsWith(".litertlm", ignoreCase = true)

            if (isZipCandidate) {
                val validation = LiteRtArchiveValidator.validate(file)
                if (!validation.isValid) {
                    if (!validation.hasSentencePieceTokenizer && validation.hasHfBpeTokenizer) {
                        return@withContext Result.failure(
                            IncompatibleTokenizerException(
                                validation.errorMessage
                                    ?: "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma)."
                            )
                        )
                    }
                    return@withContext Result.failure(
                        IllegalStateException(validation.errorMessage ?: "Invalid LiteRT model archive.")
                    )
                }
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(4096)
                .setMaxNumImages(1)
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

    suspend fun transcribePage(
        bitmap: Bitmap,
        pageNumber: Int = 1,
        totalPages: Int = 1,
        previousTail: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (!isInitialized || llmInference == null) {
            initialize().getOrThrow()
        }

        val inference = llmInference
            ?: throw IllegalStateException("Local AI engine is not initialized.")

        try {
            val graphOptions = GraphOptions.builder()
                .setEnableVisionModality(true)
                .build()

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(graphOptions)
                .setTemperature(0.0f)
                .build()

            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)

            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                try {
                    session.addImage(mpImage)
                } finally {
                    mpImage.close()
                }

                val userPrompt = VisionPrompts.buildUserPrompt(pageNumber, totalPages, previousTail)
                val prompt = "${VisionPrompts.SYSTEM_INSTRUCTION}\n\n$userPrompt"
                session.addQueryChunk(prompt)

                val response = session.generateResponse()
                    ?: throw IllegalStateException("Local AI model returned null response.")

                response.trim()
            } finally {
                session.close()
            }
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

    suspend fun runSmokeTest(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized || llmInference == null) {
                initialize().getOrThrow()
            }

            val testBitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(testBitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 32f
                isAntiAlias = true
            }
            canvas.drawText("Hello Markdrop 123", 20f, 60f, paint)

            val result = try {
                transcribePage(testBitmap)
            } finally {
                testBitmap.recycle()
            }

            if (result.contains("Markdrop", ignoreCase = true) && result.contains("123")) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Smoke test output did not contain expected text 'Markdrop' and '123'. Got: '$result'")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
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
