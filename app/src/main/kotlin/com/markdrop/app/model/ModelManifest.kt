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
}

@Serializable
data class ModelAssetConfig(
    val modelId: String,
    val repoId: String,
    val revision: String = "main",
    val assetPath: String,
    val candidateFilenames: List<String> = emptyList(),
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
        val target = targetRepo ?: primaryTarget()
        val filename = resolvedFilename ?: target.defaultAssetPath ?: assetPath
        return "https://huggingface.co/${target.repoId}/resolve/${target.revision}/$filename?download=true"
    }

    fun repoTreeUrl(): String = "https://huggingface.co/$repoId/tree/$revision"
}

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
                mirrors = emptyList()
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
                mirrors = emptyList()
            ),
            fileSizeBytes = 3_919_000_000L,
            checksumSha256 = "",
            minRamGb = 8,
            quantization = "int4",
            capabilities = ModelCapabilities(vision = true)
        ),
        ModelManifest(
            modelId = "gemma3-4b-it-int4",
            displayName = "Gemma 3 4B (Multimodal)",
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
                mirrors = emptyList()
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
