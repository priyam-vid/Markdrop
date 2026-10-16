package com.markdrop.app.model

import java.io.File
import java.util.zip.ZipFile

data class LiteRtArchiveValidation(
    val isValid: Boolean,
    val hasValidZipHeader: Boolean,
    val hasSentencePieceTokenizer: Boolean,
    val hasHfBpeTokenizer: Boolean,
    val hasModelWeightsOrMetadata: Boolean,
    val entriesSummary: List<String> = emptyList(),
    val errorMessage: String? = null
)

object LiteRtArchiveValidator {

    /**
     * Checks if the first 4 bytes of [file] correspond to the PKZip magic number (0x50, 0x4B, 0x03, 0x04).
     */
    fun isValidZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val b0 = input.read()
                val b1 = input.read()
                val b2 = input.read()
                val b3 = input.read()
                b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspects the ZIP archive to verify it contains a SentencePiece binary tokenizer (`tokenizer.model` or `*.spm`).
     */
    fun hasSentencePieceTokenizer(file: File): Boolean {
        return validate(file).hasSentencePieceTokenizer
    }

    /**
     * Performs a comprehensive manifest pre-check on a LiteRT `.task` or `.litertlm` archive.
     */
    fun validate(file: File): LiteRtArchiveValidation {
        if (!file.exists() || file.length() < 4) {
            return LiteRtArchiveValidation(
                isValid = false,
                hasValidZipHeader = false,
                hasSentencePieceTokenizer = false,
                hasHfBpeTokenizer = false,
                hasModelWeightsOrMetadata = false,
                errorMessage = "File does not exist or contains insufficient bytes."
            )
        }

        if (!isValidZipHeader(file)) {
            return LiteRtArchiveValidation(
                isValid = false,
                hasValidZipHeader = false,
                hasSentencePieceTokenizer = false,
                hasHfBpeTokenizer = false,
                hasModelWeightsOrMetadata = false,
                errorMessage = "Archive validation failed: file is corrupted or not a valid LiteRT task archive (invalid ZIP header)."
            )
        }

        var hasSpTokenizer = false
        var hasBpeTokenizer = false
        var hasWeightsOrMetadata = false
        val entries = mutableListOf<String>()

        try {
            ZipFile(file).use { zip ->
                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    val name = entry.name
                    val lower = name.lowercase()
                    if (entries.size < 25) {
                        entries.add(name)
                    }

                    if (lower.endsWith("tokenizer.model") || lower.endsWith(".spm") || lower.contains("spm.model")) {
                        hasSpTokenizer = true
                    }
                    if (lower.endsWith("tokenizer.json") || lower.endsWith("vocab.json") || lower.endsWith("merges.txt")) {
                        hasBpeTokenizer = true
                    }
                    if (lower.endsWith(".tflite") || lower.endsWith(".bin") || lower.endsWith("metadata.json") || lower.endsWith(".litertlm")) {
                        hasWeightsOrMetadata = true
                    }
                }
            }
        } catch (e: Exception) {
            return LiteRtArchiveValidation(
                isValid = false,
                hasValidZipHeader = true,
                hasSentencePieceTokenizer = false,
                hasHfBpeTokenizer = false,
                hasModelWeightsOrMetadata = false,
                errorMessage = "Failed to inspect ZIP archive entries: ${e.localizedMessage}"
            )
        }

        val isValid = hasSpTokenizer && hasWeightsOrMetadata
        val errorMsg = when {
            !hasSpTokenizer && hasBpeTokenizer ->
                "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma)."
            !hasSpTokenizer ->
                "Archive missing SentencePiece tokenizer (tokenizer.model or *.spm)."
            !hasWeightsOrMetadata ->
                "Archive missing model weights or metadata flatbuffer (*.tflite, *.bin, metadata.json)."
            else -> null
        }

        return LiteRtArchiveValidation(
            isValid = isValid,
            hasValidZipHeader = true,
            hasSentencePieceTokenizer = hasSpTokenizer,
            hasHfBpeTokenizer = hasBpeTokenizer,
            hasModelWeightsOrMetadata = hasWeightsOrMetadata,
            entriesSummary = entries,
            errorMessage = errorMsg
        )
    }
}
