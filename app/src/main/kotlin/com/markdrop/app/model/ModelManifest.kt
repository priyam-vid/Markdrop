package com.markdrop.app.model

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryTarget(
    val repoId: String,
    val revision: String = "main",
    val defaultAssetPath: String? = null,
    val candidateFilenames: List<String> = emptyList(),
    val isMirror: Boolean = false,
    val description: String = ""
) {
    fun buildDownloadUrl(filename: String? = null): String {
        val target = filename ?: defaultAssetPath ?: ""
        return "https://huggingface.co/$repoId/resolve/$revision/$target?download=true"
    }

    fun repoTreeUrl(): String = "https://huggingface.co/$repoId/tree/$revision"
    fun apiTreeUrl(): String = "https://huggingface.co/api/models/$repoId/tree/$revision"
}

@Serializable
data class RepoAssetItem(
    val path: String,
    val sizeBytes: Long,
    val isAndroidCompatible: Boolean,
    val isWebOnly: Boolean,
    val repoId: String,
    val revision: String
) {
    val fileName: String get() = path.substringAfterLast('/')
    val directory: String get() = if (path.contains('/')) path.substringBeforeLast('/') else ""

    fun formattedSize(): String {
        if (sizeBytes <= 0) return "Unknown size"
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}

@Serializable
data class ModelAssetConfig(
    val modelId: String,
    val repoId: String,
    val revision: String = "main",
    val assetPath: String,
    val candidateFilenames: List<String> = emptyList(),
    val customUrlOverride: String? = null,
    val mirrors: List<RepositoryTarget> = emptyList()
) {
    fun primaryTarget(): RepositoryTarget = RepositoryTarget(
        repoId = repoId,
        revision = revision,
        defaultAssetPath = assetPath,
        candidateFilenames = candidateFilenames,
        isMirror = false,
        description = "Primary Repository"
    )

    fun allTargets(): List<RepositoryTarget> = listOf(primaryTarget()) + mirrors

    fun buildDownloadUrl(resolvedFilename: String? = null, targetRepo: RepositoryTarget? = null): String {
        if (!customUrlOverride.isNullOrBlank()) {
            return customUrlOverride.trim()
        }
        val target = targetRepo ?: primaryTarget()
        val filename = resolvedFilename ?: target.defaultAssetPath ?: assetPath
        return "https://huggingface.co/${target.repoId}/resolve/${target.revision}/$filename?download=true"
    }

    fun isBlacklistedWebTarget(path: String): Boolean = Companion.isBlacklistedWebTarget(path)
    fun isAndroidCompatible(path: String): Boolean = Companion.isAndroidCompatible(path)

    companion object {
        fun isBlacklistedWebTarget(path: String): Boolean {
            val fileName = path.substringAfterLast('/')
            val lower = fileName.lowercase()
            return lower.contains("-web.task") || lower.contains("web.task.bin") ||
                    lower.endsWith("-web.task") || lower.contains(".wasm") ||
                    lower.contains("-web.") || lower.endsWith(".js")
        }

        fun isAndroidCompatible(path: String): Boolean {
            val fileName = path.substringAfterLast('/')
            val lower = fileName.lowercase()
            if (isBlacklistedWebTarget(lower)) return false
            return lower.endsWith(".task") || lower.endsWith(".litertlm") || lower.endsWith(".bin")
        }
    }

    fun repoTreeUrl(): String = "https://huggingface.co/$repoId/tree/$revision"
    fun apiTreeUrl(): String = "https://huggingface.co/api/models/$repoId/tree/$revision"
}

@Serializable
data class HfTreeItem(
    val type: String = "file",
    val path: String = "",
    val size: Long = 0L
)

@Serializable
data class ModelCapabilities(
    val vision: Boolean = false,
    val audio: Boolean = false
)

@Serializable
data class ModelManifest(
    val modelId: String,
    val displayName: String,
    val version: String,
    val assetConfig: ModelAssetConfig,
    val fileSizeBytes: Long,
    val checksumSha256: String = "",
    val minRamGb: Int = 6,
    val quantization: String = "int4",
    val capabilities: ModelCapabilities = ModelCapabilities(vision = false)
) {
    val downloadUrl: String get() = assetConfig.buildDownloadUrl()
    val mirrors: List<RepositoryTarget> get() = assetConfig.mirrors
    val supportsVision: Boolean get() = capabilities.vision
}

object DefaultModelManifest {
    val MODELS = listOf(
        ModelManifest(
            modelId = "gemma-4-E2B-it",
            displayName = "Gemma 4 E2B (Multimodal, Official Google AI Edge)",
            version = "1.0.0",
            assetConfig = ModelAssetConfig(
                modelId = "gemma-4-E2B-it",
                repoId = "litert-community/gemma-4-E2B-it-litert-lm",
                revision = "main",
                assetPath = "gemma-4-E2B-it.litertlm",
                candidateFilenames = listOf(
                    "gemma-4-E2B-it.litertlm",
                    "gemma-4-E2B-it-int4.litertlm",
                    "gemma-4-E2B-it.task"
                ),
                mirrors = listOf(
                    RepositoryTarget(
                        repoId = "google/gemma-3n-E2B-it-litert-lm",
                        revision = "main",
                        defaultAssetPath = "gemma-3n-E2B-it-int4.litertlm",
                        candidateFilenames = listOf("gemma-3n-E2B-it-int4.litertlm"),
                        isMirror = true,
                        description = "Google Gemma 3n E2B Mirror"
                    )
                )
            ),
            fileSizeBytes = 2_770_000_000L,
            checksumSha256 = "",
            minRamGb = 8,
            quantization = "int4",
            capabilities = ModelCapabilities(vision = true)
        ),
        ModelManifest(
            modelId = "gemma-3n-E2B-it",
            displayName = "Gemma 3n E2B (Multimodal Vision + Audio)",
            version = "1.0.0",
            assetConfig = ModelAssetConfig(
                modelId = "gemma-3n-E2B-it",
                repoId = "google/gemma-3n-E2B-it-litert-lm",
                revision = "main",
                assetPath = "gemma-3n-E2B-it-int4.litertlm",
                candidateFilenames = listOf(
                    "gemma-3n-E2B-it-int4.litertlm",
                    "gemma-3n-E2B-it.litertlm"
                ),
                mirrors = listOf(
                    RepositoryTarget(
                        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
                        revision = "main",
                        defaultAssetPath = "gemma-4-E2B-it.litertlm",
                        candidateFilenames = listOf("gemma-4-E2B-it.litertlm"),
                        isMirror = true,
                        description = "LiteRT Community Gemma 4 Mirror"
                    )
                )
            ),
            fileSizeBytes = 3_919_000_000L,
            checksumSha256 = "",
            minRamGb = 8,
            quantization = "int4",
            capabilities = ModelCapabilities(vision = true)
        ),
        ModelManifest(
            modelId = "gemma3-270m-it",
            displayName = "Gemma 3 270M (Text-only, Fast)",
            version = "1.0.0",
            assetConfig = ModelAssetConfig(
                modelId = "gemma3-270m-it",
                repoId = "litert-community/gemma-3-270m-it",
                revision = "main",
                assetPath = "gemma3-270m-it-q8.task",
                candidateFilenames = listOf(
                    "gemma3-270m-it-q8.task",
                    "gemma3-270m-it-q8.litertlm"
                ),
                mirrors = emptyList()
            ),
            fileSizeBytes = 303_950_933L,
            checksumSha256 = "",
            minRamGb = 3,
            quantization = "q8",
            capabilities = ModelCapabilities(vision = false)
        ),
        ModelManifest(
            modelId = "gemma2-2b-it-int4",
            displayName = "Gemma 2 2B (Text-only)",
            version = "1.0.0",
            assetConfig = ModelAssetConfig(
                modelId = "gemma2-2b-it-int4",
                repoId = "litert-community/Gemma2-2B-IT",
                revision = "main",
                assetPath = "gemma2_q8_multi-prefill-seq_ekv1280.task",
                candidateFilenames = listOf(
                    "gemma2_q8_multi-prefill-seq_ekv1280.task",
                    "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
                    "gemma2-2b-it-int4.task"
                ),
                mirrors = emptyList()
            ),
            fileSizeBytes = 2_702_868_915L,
            checksumSha256 = "",
            minRamGb = 6,
            quantization = "q8",
            capabilities = ModelCapabilities(vision = false)
        ),
        ModelManifest(
            modelId = "gemma3-4b-it-int4",
            displayName = "Gemma 3 4B (Multimodal, Fallback Mirror)",
            version = "1.0.0",
            assetConfig = ModelAssetConfig(
                modelId = "gemma3-4b-it-int4",
                repoId = "litert-community/Gemma3-4B-IT",
                revision = "main",
                assetPath = "gemma3-4b-it-int4.task",
                candidateFilenames = listOf(
                    "gemma3-4b-it-int4.task",
                    "gemma-3-4b-it-int4.task",
                    "gemma3-4b-it-q4.task"
                ),
                mirrors = listOf(
                    RepositoryTarget(
                        repoId = "google/gemma-3n-E4B-it-litert-lm",
                        revision = "main",
                        defaultAssetPath = "gemma-3n-E4B-it-int4.litertlm",
                        candidateFilenames = listOf("gemma-3n-E4B-it-int4.litertlm"),
                        isMirror = true,
                        description = "Google Gemma 3n E4B Android Native Mirror"
                    )
                )
            ),
            fileSizeBytes = 5_270_000_000L,
            checksumSha256 = "",
            minRamGb = 8,
            quantization = "int4",
            capabilities = ModelCapabilities(vision = true)
        )
    )

    fun getModelById(id: String): ModelManifest? = MODELS.find { it.modelId == id }
    val DEFAULT = MODELS[0] // Gemma 4 E2B as verified default multimodal
}
