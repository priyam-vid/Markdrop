package com.markdrop.app.conversion

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import com.markdrop.app.conversion.image.ImageLoader
import com.markdrop.app.conversion.pdf.PageSelectionParser
import com.markdrop.app.conversion.assemble.Assembler
import com.markdrop.app.conversion.cloud.AiErrorPolicy
import com.markdrop.app.conversion.cloud.CloudConfig
import com.markdrop.app.conversion.cloud.CloudVisionClient
import com.markdrop.app.conversion.cloud.PageErrorAction
import com.markdrop.app.conversion.layout.LayoutPage
import com.markdrop.app.conversion.layout.LayoutToMarkdown
import com.markdrop.app.conversion.local.LocalLlmEngine
import com.markdrop.app.conversion.markdown.HeaderFooterCleaner
import com.markdrop.app.conversion.ocr.MlKitOcrEngine
import com.markdrop.app.conversion.layout.PageGeometry
import com.markdrop.app.conversion.pdf.PdfOpenError
import com.markdrop.app.conversion.pdf.PdfOpenException
import com.markdrop.app.conversion.pdf.PdfPageRenderer
import com.markdrop.app.conversion.pdf.TextLayerExtractor
import com.markdrop.app.conversion.pdf.TextLayerSession
import com.markdrop.app.data.db.ConversionStatus
import com.markdrop.app.data.db.MarkdropDatabase
import com.markdrop.app.data.db.PageResultEntity
import com.markdrop.app.data.security.SecureKeyStore
import com.markdrop.app.model.DefaultModelManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Unified conversion pipeline for Markdrop.
 * Principle: one pipeline, two user-visible modes (OFFLINE, AI), per-page persistence.
 */
class ConversionPipeline(
    private val context: Context
) {
    private val db = MarkdropDatabase.getInstance(context)
    private val secureKeyStore = SecureKeyStore(context)
    private val app = context.applicationContext as com.markdrop.app.MarkdropApplication

    suspend fun executeJob(
        jobId: Long,
        onProgress: (pageIndex: Int, totalPages: Int, message: String) -> Unit = { _, _, _ -> }
    ): ConversionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val job = db.conversionDao().getById(jobId)
            ?: throw IllegalArgumentException("Job with ID $jobId not found")

        val pdfUri = Uri.parse(job.pdfUri)
        val mimeType = context.contentResolver.getType(pdfUri) ?: ""
        val isImage = mimeType.startsWith("image/") ||
                job.fileName.endsWith(".png", true) ||
                job.fileName.endsWith(".jpg", true) ||
                job.fileName.endsWith(".jpeg", true) ||
                job.fileName.endsWith(".webp", true)

        val pdfRenderer = if (!isImage) {
            try {
                PdfPageRenderer(context, pdfUri)
            } catch (_: Exception) {
                null
            }
        } else null
        val ocrEngine = MlKitOcrEngine()

        val isAiMode = job.mode.isAi
        val hasCloudKey = secureKeyStore.hasAnyConfiguredProvider()

        val settings = app.settingsRepository.conversionSettingsFlow.first()
        val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
        val manifest = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT
        val isLocalModelVerified = app.settingsRepository.isLocalModelVerified(manifest.modelId).first()
        val hasLocalAi = app.modelManager.isModelReady(manifest) && isLocalModelVerified

        val aiSource = settings.aiSource // AUTO, CLOUD, LOCAL
        val useCloud = isAiMode && (aiSource == "CLOUD" || (aiSource == "AUTO" && hasCloudKey))
        val useLocal = isAiMode && !useCloud && (aiSource == "LOCAL" || (aiSource == "AUTO" && hasLocalAi))

        val resolvedCloudConfig = if (useCloud) {
            CloudConfig.resolve(secureKeyStore).getOrElse { err ->
                db.conversionDao().markFailed(jobId, err.message ?: "Cloud configuration error")
                throw err
            }
        } else null

        if (isAiMode && !useCloud && !useLocal) {
            val provider = secureKeyStore.getSelectedProvider()
            if (aiSource == "CLOUD" || (!hasLocalAi && !hasCloudKey)) {
                db.conversionDao().markFailed(jobId, "API key for ${provider.displayName} is missing. Please add your key in Settings.")
                throw ApiKeyMissingException(provider.displayName)
            } else if (aiSource == "LOCAL") {
                val reason = if (!app.modelManager.isModelReady(manifest)) "is not ready. Please download it in Settings." else "is unverified. Please run smoke test verification."
                db.conversionDao().markFailed(jobId, "Local AI model '${manifest.displayName}' $reason")
                throw ModelNotReadyException(manifest.displayName)
            }
        }

        val cloudClient = if (resolvedCloudConfig != null) CloudVisionClient() else null
        val localLlm = if (useLocal) {
            LocalLlmEngine(context, app.modelManager.getModelPath(manifest), manifest)
        } else null

        val aiErrorPolicy = AiErrorPolicy(fallbackToOcrOnError = settings.fallbackToOcrOnError)
        var hasAnyFallback = false

        val textSession: TextLayerSession? = if (pdfRenderer != null) {
            try {
                TextLayerSession.open(context, pdfUri)
            } catch (e: PdfOpenException) {
                if (e.error == PdfOpenError.PASSWORD_REQUIRED) {
                    db.conversionDao().markFailed(jobId, "PDF is password protected.")
                    throw PdfEncryptedException()
                } else if (e.error == PdfOpenError.CORRUPT) {
                    db.conversionDao().markFailed(jobId, "PDF file is corrupted.")
                    throw PdfCorruptException(cause = e)
                }
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        } else null

        try {
            db.conversionDao().updateStatus(jobId, ConversionStatus.PROCESSING)

            val totalPdfPages = pdfRenderer?.pageCount ?: 1
            val pagesToProcess = PageSelectionParser.parse(job.selectedPages, totalPdfPages)
            val totalPages = pagesToProcess.size

            // Resume capability: Fetch existing completed page results for this job
            val existingPages = db.pageResultDao().getByJobId(jobId).associateBy { it.pageIndex }
            var lastCompletedMarkdown: String? = null

            for ((progressIndex, pageIndex) in pagesToProcess.withIndex()) {
                coroutineContext.ensureActive()

                // Check if already completed in a prior attempt
                val existing = existingPages[pageIndex]
                if (existing != null && existing.status == "DONE" && !existing.markdown.isNullOrBlank()) {
                    lastCompletedMarkdown = existing.markdown
                    val progress = (progressIndex + 1).toFloat() / totalPages
                    db.conversionDao().updateProgress(jobId, progressIndex + 1, progress)
                    onProgress(progressIndex + 1, totalPages, "Page ${progressIndex + 1} of $totalPages (resumed from cache)")
                    continue
                }

                val pageStart = System.currentTimeMillis()
                var pageMarkdown = ""
                var engineUsed = "UNKNOWN"
                var pageError: String? = null
                var pageStatus = "DONE"

                try {
                    // Step 1: Check for usable embedded text layer (born-digital PDFs)
                    val geom = textSession?.pageGeometry(pageIndex) ?: PageGeometry(595f, 842f)
                    val words = textSession?.extractWords(pageIndex)
                    val contentInfo = if (words != null) textSession.analyzePage(pageIndex, words) else null

                    val bypassTextLayer = isAiMode && !settings.aiUseEmbeddedTextLayer

                    if (!bypassTextLayer && contentInfo?.isUsableTextLayer == true) {
                        onProgress(progressIndex + 1, totalPages, "Extracting page ${progressIndex + 1} of $totalPages (text layer)...")
                        pageMarkdown = LayoutToMarkdown.formatPage(
                            LayoutPage(
                                pageIndex = pageIndex,
                                width = geom.width,
                                height = geom.height,
                                words = words ?: emptyList(),
                                figures = contentInfo.figureBoxes
                            )
                        )
                        engineUsed = "TEXT_LAYER"
                    } else {
                        suspend fun loadBitmap(isOcr: Boolean): Bitmap {
                            return if (pdfRenderer != null) {
                                try {
                                    pdfRenderer.renderPageToBitmap(pageIndex, targetDpi = 200, isOcrMode = isOcr)
                                } catch (e: Exception) {
                                    throw PageRenderException(pageIndex, cause = e)
                                }
                            } else {
                                ImageLoader.loadImage(context, pdfUri, maxDimension = 2048)
                            }
                        }

                        if (isAiMode && (cloudClient != null || localLlm != null)) {
                            // Step 2: AI mode rasterization + Vision LLM
                            onProgress(progressIndex + 1, totalPages, "Transcribing page ${progressIndex + 1} of $totalPages via AI...")
                            val bitmap = loadBitmap(isOcr = false)
                            try {
                                if (cloudClient != null && resolvedCloudConfig != null) {
                                    pageMarkdown = cloudClient.transcribePage(
                                        bitmap = bitmap,
                                        provider = resolvedCloudConfig.provider,
                                        apiKey = resolvedCloudConfig.apiKey,
                                        model = resolvedCloudConfig.model,
                                        pageNumber = progressIndex + 1,
                                        totalPages = totalPages,
                                        previousTail = lastCompletedMarkdown?.takeLast(400)
                                    )
                                    engineUsed = "VISION_AI_CLOUD (${resolvedCloudConfig.model})"
                                    aiErrorPolicy.recordSuccess()
                                } else if (localLlm != null) {
                                    pageMarkdown = localLlm.transcribePage(
                                        bitmap = bitmap,
                                        pageNumber = progressIndex + 1,
                                        totalPages = totalPages,
                                        previousTail = lastCompletedMarkdown?.takeLast(400)
                                    )
                                    engineUsed = "VISION_AI_LOCAL (${manifest.displayName})"
                                    aiErrorPolicy.recordSuccess()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e

                                // 1. Immediate fail-fast abort on Auth, BadRequest, ModelNotFound (404)
                                if (aiErrorPolicy.isFailFast(e)) {
                                    val prov = resolvedCloudConfig?.provider?.displayName ?: "Local AI"
                                    val mod = resolvedCloudConfig?.model ?: manifest.displayName
                                    val fatalMsg = "AI Error [$prov ($mod)]: ${e.message}"
                                    db.conversionDao().markFailed(jobId, fatalMsg)
                                    throw e
                                }

                                // 2. Circuit breaker trips after 3 consecutive identical page failures
                                if (aiErrorPolicy.recordErrorAndCheckTripped(e)) {
                                    val breakerMsg = "Circuit breaker tripped after 3 consecutive failures (${e.javaClass.simpleName}): ${e.message}"
                                    db.conversionDao().markFailed(jobId, breakerMsg)
                                    throw IllegalStateException(breakerMsg, e)
                                }

                                // 3. Handle page error according to fallback switch
                                val decision = aiErrorPolicy.handlePageError(pageIndex, e)
                                if (decision.action == PageErrorAction.FALLBACK_TO_OCR) {
                                    onProgress(progressIndex + 1, totalPages, "AI error on page ${progressIndex + 1}, falling back to OCR...")
                                    val ocrResult = ocrEngine.processImage(bitmap, pageIndex)
                                    val ocrMd = LayoutToMarkdown.formatOcr(ocrResult)
                                    pageMarkdown = "${decision.markerComment}\n\n$ocrMd"
                                    engineUsed = "FALLBACK_OCR"
                                    pageStatus = "FALLBACK"
                                    hasAnyFallback = true
                                } else {
                                    pageError = e.message ?: "AI transcription error"
                                    pageMarkdown = decision.markerComment ?: "<!-- Error transcribing page ${pageIndex + 1} -->"
                                    engineUsed = "AI_ERROR"
                                    pageStatus = "FAILED"
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        } else {
                            // Step 3: Offline mode rasterization + ML Kit OCR -> LayoutToMarkdown
                            onProgress(progressIndex + 1, totalPages, "Extracting page ${progressIndex + 1} of $totalPages (OCR)...")
                            val bitmap = loadBitmap(isOcr = true)
                            try {
                                val ocrResult = ocrEngine.processImage(bitmap, pageIndex)
                                pageMarkdown = LayoutToMarkdown.formatOcr(ocrResult)
                                engineUsed = "OFFLINE_OCR"
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    pageError = e.message ?: "Page processing error"
                    pageMarkdown = "<!-- Error transcribing page ${pageIndex + 1}: $pageError -->"
                    engineUsed = "ERROR"
                    pageStatus = "FAILED"
                }

                val pageDuration = System.currentTimeMillis() - pageStart

                // Persist page result row immediately
                val pageEntity = PageResultEntity(
                    jobId = jobId,
                    pageIndex = pageIndex,
                    status = pageStatus,
                    markdown = pageMarkdown,
                    error = pageError,
                    durationMs = pageDuration,
                    engineUsed = engineUsed
                )
                db.pageResultDao().insert(pageEntity)
                if (pageError == null) {
                    lastCompletedMarkdown = pageMarkdown
                }

                val progress = (progressIndex + 1).toFloat() / totalPages
                db.conversionDao().updateProgress(jobId, progressIndex + 1, progress)
            }

            // All pages processed. Gather results in order
            val allResults = db.pageResultDao().getByJobId(jobId).sortedBy { it.pageIndex }
            val rawPages = allResults.map { it.markdown ?: "" }

            val stripEnabled = app.settingsRepository.stripHeaderFooterEnabledFlow.first()
            val cleanedPages = if (stripEnabled && !isAiMode) HeaderFooterCleaner.cleanPages(rawPages) else rawPages
            val finalMarkdown = Assembler.assemble(cleanedPages)

            val duration = System.currentTimeMillis() - startTime
            val hasErrors = allResults.any { it.status == "FAILED" || it.status == "FALLBACK" }
            val finalStatus = if (hasErrors) ConversionStatus.PARTIAL else ConversionStatus.DONE

            // Save to filesDir
            val docDir = File(context.filesDir, "conversions").apply { mkdirs() }
            val outputFile = File(docDir, "${job.fileName.substringBeforeLast(".")}_$jobId.md")
            outputFile.writeText(finalMarkdown)

            db.conversionDao().markCompleted(
                id = jobId,
                status = finalStatus,
                completedAt = System.currentTimeMillis(),
                markdown = finalMarkdown,
                duration = duration
            )

            ConversionResult(
                markdown = finalMarkdown,
                pageCount = totalPages,
                durationMs = duration,
                mode = job.mode
            )
        } finally {
            textSession?.close()
            pdfRenderer?.close()
            ocrEngine.close()
            cloudClient?.close()
            localLlm?.close()
        }
    }
}
