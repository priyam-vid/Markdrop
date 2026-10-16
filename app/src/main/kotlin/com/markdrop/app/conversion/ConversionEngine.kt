package com.markdrop.app.conversion

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.cloud.CloudVisionClient
import com.markdrop.app.conversion.local.LocalLlmEngine
import com.markdrop.app.conversion.markdown.HeaderFooterCleaner
import com.markdrop.app.conversion.markdown.HeuristicMarkdownFormatter
import com.markdrop.app.conversion.ocr.MlKitOcrEngine
import com.markdrop.app.conversion.ocr.OcrPageResult
import com.markdrop.app.conversion.pdf.PdfPageRenderer
import com.markdrop.app.data.security.SecureKeyStore
import com.markdrop.app.model.DefaultModelManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ConversionEngine(
    private val context: Context
) {

    suspend fun convertPdf(
        pdfUri: Uri,
        mode: ConversionMode = ConversionMode.FAST,
        selectedPages: List<Int>? = null,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit
    ): ConversionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        when (mode) {
            ConversionMode.OFFLINE, ConversionMode.FAST -> convertWithFastMode(pdfUri, selectedPages, onProgress, startTime)
            ConversionMode.AI, ConversionMode.ADVANCED -> convertWithAdvancedMode(pdfUri, selectedPages, onProgress, startTime)
            ConversionMode.SMART -> convertWithSmartMode(pdfUri, selectedPages, onProgress, startTime)
            ConversionMode.PRECISION -> convertWithPrecisionMode(pdfUri, selectedPages, onProgress, startTime)
        }
    }

    suspend fun convertPdf(
        pdfUri: Uri,
        mode: ConversionMode = ConversionMode.FAST,
        selectedPages: List<Int>? = null,
        onProgress: (pageIndex: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): ConversionResult = convertPdf(pdfUri, mode, selectedPages) { p, t, _ -> onProgress(p, t) }

    private suspend fun convertWithFastMode(
        pdfUri: Uri,
        selectedPages: List<Int>?,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit,
        startTime: Long
    ): ConversionResult {
        val pdfRenderer = PdfPageRenderer(context, pdfUri)
        val ocrEngine = MlKitOcrEngine()

        try {
            val totalPdfPages = pdfRenderer.pageCount
            val pagesToProcess = selectedPages?.filter { it in 0 until totalPdfPages }
                ?: (0 until totalPdfPages).toList()
            val totalPages = pagesToProcess.size
            val ocrPages = mutableListOf<OcrPageResult>()

            pagesToProcess.forEachIndexed { progressIndex, pageIndex ->
                coroutineContext.ensureActive()

                onProgress(progressIndex + 1, totalPages, "Extracting page ${progressIndex + 1} of $totalPages via OCR...")

                val bitmap = pdfRenderer.renderPageToBitmap(pageIndex, scaleFactor = 2.5f)
                try {
                    val ocrResult = ocrEngine.processImage(bitmap, pageIndex)
                    ocrPages.add(ocrResult)
                } finally {
                    bitmap.recycle()
                }
            }

            // Format structured OCR data into Markdown per page
            val rawPageMarkdowns = ocrPages.map { page ->
                HeuristicMarkdownFormatter.formatDocument(listOf(page))
            }

            // Strip recurring headers, footers, watermarks, and isolated page numbers if enabled
            val app = context.applicationContext as com.markdrop.app.MarkdropApplication
            val stripEnabled = app.settingsRepository.stripHeaderFooterEnabledFlow.first()
            val cleanedPageMarkdowns = if (stripEnabled) HeaderFooterCleaner.cleanPages(rawPageMarkdowns) else rawPageMarkdowns
            val finalMarkdown = cleanedPageMarkdowns.joinToString("\n\n---\n\n")
            val duration = System.currentTimeMillis() - startTime

            return ConversionResult(
                markdown = finalMarkdown,
                pageCount = totalPages,
                durationMs = duration,
                mode = ConversionMode.FAST
            )
        } finally {
            pdfRenderer.close()
            ocrEngine.close()
        }
    }

    private suspend fun convertWithSmartMode(
        pdfUri: Uri,
        selectedPages: List<Int>?,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit,
        startTime: Long
    ): ConversionResult {
        val pdfRenderer = PdfPageRenderer(context, pdfUri)
        val ocrEngine = MlKitOcrEngine()
        val secureKeyStore = SecureKeyStore(context)
        val app = context.applicationContext as com.markdrop.app.MarkdropApplication
        val hasCloudKey = secureKeyStore.hasAnyConfiguredProvider()

        val settings = app.settingsRepository.conversionSettingsFlow.first()
        val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
        val manifest = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT
        val hasLocalAi = app.modelManager.isModelReady(manifest)

        val client = if (hasCloudKey) CloudVisionClient() else null
        val localLlm = if (!hasCloudKey && hasLocalAi) {
            LocalLlmEngine(context, app.modelManager.getModelPath(manifest))
        } else null

        try {
            val totalPdfPages = pdfRenderer.pageCount
            val pagesToProcess = selectedPages?.filter { it in 0 until totalPdfPages }
                ?: (0 until totalPdfPages).toList()
            val totalPages = pagesToProcess.size
            val pageMarkdowns = mutableListOf<String>()

            pagesToProcess.forEachIndexed { progressIndex, pageIndex ->
                coroutineContext.ensureActive()
                onProgress(progressIndex + 1, totalPages, "Intelligent routing page ${progressIndex + 1} of $totalPages...")

                val bitmap = pdfRenderer.renderPageToBitmap(pageIndex, scaleFactor = 2.0f)
                try {
                    // First pass: fast OCR analysis
                    val ocrResult = ocrEngine.processImage(bitmap, pageIndex)
                    val isComplex = isPageComplex(ocrResult)

                    if (isComplex && client != null) {
                        // Cloud AI for complex pages
                        val provider = secureKeyStore.getSelectedProvider()
                        val apiKey = secureKeyStore.getApiKey(provider) ?: ""
                        val model = secureKeyStore.getSelectedModel(provider)
                        val pageMd = client.transcribePage(bitmap, provider, apiKey, model)
                        pageMarkdowns.add(pageMd)
                    } else if (isComplex && localLlm != null) {
                        // Local AI for complex pages
                        val pageMd = localLlm.transcribePage(bitmap)
                        pageMarkdowns.add(pageMd)
                    } else {
                        // Simple page: zero-cost fast OCR formatting
                        val pageMd = HeuristicMarkdownFormatter.formatDocument(listOf(ocrResult))
                        pageMarkdowns.add(pageMd)
                    }
                } finally {
                    bitmap.recycle()
                }
            }

            val stripEnabled = app.settingsRepository.stripHeaderFooterEnabledFlow.first()
            val cleanedPageMarkdowns = if (stripEnabled) HeaderFooterCleaner.cleanPages(pageMarkdowns) else pageMarkdowns
            val finalMarkdown = cleanedPageMarkdowns.joinToString("\n\n---\n\n")
            val duration = System.currentTimeMillis() - startTime

            return ConversionResult(
                markdown = finalMarkdown,
                pageCount = totalPages,
                durationMs = duration,
                mode = ConversionMode.SMART
            )
        } finally {
            pdfRenderer.close()
            ocrEngine.close()
            client?.close()
            localLlm?.close()
        }
    }

    private fun isPageComplex(ocrResult: OcrPageResult): Boolean {
        var tableSignals = 0
        var mathSignals = 0

        ocrResult.blocks.forEach { block ->
            val text = block.text
            // Table signals: pipe symbols, multiple consecutive numbers or columns, tab-like alignment
            if (text.contains("|") || text.count { it == '\t' } > 2) tableSignals++
            if (Regex("""\b\d{1,4}\s+\d{1,4}\s+\d{1,4}\b""").containsMatchIn(text)) tableSignals++

            // Math signals: LaTeX patterns, formulas, greek letters, exponents
            if (Regex("""[\\_^{}=+*/∫∑√πθλαβγΔ]""").containsMatchIn(text)) mathSignals++
            if (text.contains("\\frac") || text.contains("\\sqrt") || text.contains("\\sum")) mathSignals += 2
        }

        return tableSignals >= 2 || mathSignals >= 2
    }

    private suspend fun convertWithAdvancedMode(
        pdfUri: Uri,
        selectedPages: List<Int>?,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit,
        startTime: Long
    ): ConversionResult {
        val secureKeyStore = SecureKeyStore(context)
        val provider = secureKeyStore.getSelectedProvider()
        val apiKey = secureKeyStore.getApiKey(provider)
            ?: throw IllegalStateException("No API key configured for ${provider.displayName}. Please configure your key in Settings.")
        val model = secureKeyStore.getSelectedModel(provider)

        val pdfRenderer = PdfPageRenderer(context, pdfUri)
        val client = CloudVisionClient()

        try {
            val totalPdfPages = pdfRenderer.pageCount
            val pagesToProcess = selectedPages?.filter { it in 0 until totalPdfPages }
                ?: (0 until totalPdfPages).toList()
            val totalPages = pagesToProcess.size
            val pageMarkdowns = mutableListOf<String>()

            // Process in sequential batches of 2 pages with retry resilience
            val batchSize = 2
            val pageChunks = pagesToProcess.chunked(batchSize)
            var processedCount = 0

            for (chunk in pageChunks) {
                coroutineContext.ensureActive()
                val startPage = chunk.first() + 1
                val endPage = chunk.last() + 1
                val batchMsg = if (totalPages > 3 && chunk.size > 1) {
                    "Processing pages $startPage-$endPage of $totalPages..."
                } else {
                    "Processing page $startPage of $totalPages..."
                }

                for (pageIndex in chunk) {
                    coroutineContext.ensureActive()
                    processedCount++
                    onProgress(processedCount, totalPages, batchMsg)

                    val bitmap = pdfRenderer.renderPageToBitmap(pageIndex, scaleFactor = 2.0f)
                    var attempts = 0
                    var pageMarkdown: String? = null

                    try {
                        while (attempts < 2 && pageMarkdown == null) {
                            try {
                                attempts++
                                pageMarkdown = client.transcribePage(bitmap, provider, apiKey, model)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                if (attempts >= 2) throw e
                                delay(1000) // Brief backoff before retry
                            }
                        }
                        if (pageMarkdown != null) {
                            pageMarkdowns.add(pageMarkdown)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            val app = context.applicationContext as com.markdrop.app.MarkdropApplication
            val stripEnabled = app.settingsRepository.stripHeaderFooterEnabledFlow.first()
            val cleanedPageMarkdowns = if (stripEnabled) HeaderFooterCleaner.cleanPages(pageMarkdowns) else pageMarkdowns
            val finalMarkdown = cleanedPageMarkdowns.joinToString("\n\n---\n\n")
            val duration = System.currentTimeMillis() - startTime

            return ConversionResult(
                markdown = finalMarkdown,
                pageCount = totalPages,
                durationMs = duration,
                mode = ConversionMode.ADVANCED
            )
        } finally {
            pdfRenderer.close()
            client.close()
        }
    }

    private suspend fun convertWithPrecisionMode(
        pdfUri: Uri,
        selectedPages: List<Int>?,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit,
        startTime: Long
    ): ConversionResult {
        val app = context.applicationContext as com.markdrop.app.MarkdropApplication
        val settings = app.settingsRepository.conversionSettingsFlow.first()
        val modelId = settings.precisionModelId ?: DefaultModelManifest.DEFAULT.modelId
        val manifest = DefaultModelManifest.getModelById(modelId) ?: DefaultModelManifest.DEFAULT

        val modelManager = app.modelManager
        if (!modelManager.isModelReady(manifest)) {
            val file = modelManager.getModelFile(manifest)
            if (file.exists() && file.length() > 0 && !modelManager.hasSentencePieceTokenizer(file)) {
                throw com.markdrop.app.conversion.local.IncompatibleTokenizerException(
                    "Incompatible Tokenizer: This model uses a Hugging Face BPE tokenizer. LiteRT on Android currently requires SentencePiece-based models (such as PaliGemma or Gemma)."
                )
            }
            throw IllegalStateException("Precision Mode model is not downloaded. Please download a SentencePiece-compatible model in Settings.")
        }

        val pdfRenderer = PdfPageRenderer(context, pdfUri)
        val localLlmEngine = LocalLlmEngine(context, modelManager.getModelPath(manifest))

        try {
            val totalPdfPages = pdfRenderer.pageCount
            val pagesToProcess = selectedPages?.filter { it in 0 until totalPdfPages }
                ?: (0 until totalPdfPages).toList()
            val totalPages = pagesToProcess.size
            val pageMarkdowns = mutableListOf<String>()

            pagesToProcess.forEachIndexed { progressIndex, pageIndex ->
                coroutineContext.ensureActive()

                onProgress(progressIndex + 1, totalPages, "Inferring page ${progressIndex + 1} of $totalPages via local AI...")

                val bitmap = pdfRenderer.renderPageToBitmap(pageIndex, scaleFactor = 2.0f)
                try {
                    val pageMarkdown = localLlmEngine.transcribePage(bitmap)
                    pageMarkdowns.add(pageMarkdown)
                } finally {
                    bitmap.recycle()
                }
            }

            val stripEnabled = app.settingsRepository.stripHeaderFooterEnabledFlow.first()
            val cleanedPageMarkdowns = if (stripEnabled) HeaderFooterCleaner.cleanPages(pageMarkdowns) else pageMarkdowns
            val finalMarkdown = cleanedPageMarkdowns.joinToString("\n\n---\n\n")
            val duration = System.currentTimeMillis() - startTime

            return ConversionResult(
                markdown = finalMarkdown,
                pageCount = totalPages,
                durationMs = duration,
                mode = ConversionMode.PRECISION
            )
        } finally {
            pdfRenderer.close()
            localLlmEngine.close()
        }
    }
}
