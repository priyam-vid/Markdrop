package com.markdrop.app.ui.screens.convert

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.conversion.ConversionMode
import com.markdrop.app.conversion.ConversionResult
import com.markdrop.app.conversion.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConvertStatus {
    object Idle : ConvertStatus()
    data class Converting(
        val currentPage: Int,
        val totalPages: Int,
        val progress: Float,
        val statusMessage: String? = null
    ) : ConvertStatus()
    data class Success(val result: ConversionResult) : ConvertStatus()
    data class Error(val message: String) : ConvertStatus()
}

data class SelectedPdfInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int
)

data class ConvertScreenUiState(
    val selectedPdf: SelectedPdfInfo? = null,
    val selectedPageIndices: Set<Int> = emptySet(),
    val selectedMode: ConversionMode = ConversionMode.OFFLINE,
    val status: ConvertStatus = ConvertStatus.Idle,
    val isPrecisionAvailable: Boolean = false,
    val isAdvancedAvailable: Boolean = false,
    val configuredProviderName: String = "",
    val configuredModelName: String = "",
    val promptPricePerToken: Double? = null,
    val deviceRamGb: Double = 0.0,
    val isRamSufficient: Boolean = true,
    val effectiveEngineSummary: String = ""
)

class ConvertViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val secureKeyStore = com.markdrop.app.data.security.SecureKeyStore(application)
    private val modelManager = (application as com.markdrop.app.MarkdropApplication).modelManager

    private val _uiState = MutableStateFlow(ConvertScreenUiState())
    val uiState: StateFlow<ConvertScreenUiState> = _uiState.asStateFlow()

    init {
        refreshAvailability()
    }

    private fun computeSummary(
        mode: ConversionMode,
        provider: String,
        model: String,
        pdfInfo: SelectedPdfInfo?,
        selectedPages: Set<Int>
    ): String {
        val totalPdfPages = pdfInfo?.pageCount ?: 1
        val selectedCount = if (selectedPages.isEmpty()) totalPdfPages else selectedPages.size
        val pageStr = if (selectedCount == totalPdfPages) "$totalPdfPages pages" else "$selectedCount of $totalPdfPages pages"

        return if (mode.isAi) {
            "AI · $provider ($model) · $pageStr"
        } else {
            "Offline · text-layer / ML Kit OCR · $pageStr"
        }
    }

    fun refreshAvailability() {
        viewModelScope.launch {
            val hasKey = secureKeyStore.hasAnyConfiguredProvider()
            val activeProvider = secureKeyStore.getSelectedProvider()
            val activeModel = secureKeyStore.getSelectedModel(activeProvider)

            val app = getApplication<com.markdrop.app.MarkdropApplication>()
            val settings = app.settingsRepository.conversionSettingsFlow.first()
            val modelId = settings.precisionModelId ?: com.markdrop.app.model.DefaultModelManifest.DEFAULT.modelId
            val manifest = com.markdrop.app.model.DefaultModelManifest.getModelById(modelId) ?: com.markdrop.app.model.DefaultModelManifest.DEFAULT
            
            val isModelReady = modelManager.isModelReady(manifest)
            val ramGb = modelManager.getDeviceRamGb()
            val isRamSufficient = ramGb >= manifest.minRamGb
            val pricing = app.settingsRepository.getModelPricing(activeModel).first()

            val current = _uiState.value
            _uiState.value = current.copy(
                isPrecisionAvailable = isModelReady,
                isAdvancedAvailable = hasKey,
                configuredProviderName = activeProvider.displayName,
                configuredModelName = activeModel,
                promptPricePerToken = pricing,
                deviceRamGb = ramGb,
                isRamSufficient = isRamSufficient,
                effectiveEngineSummary = computeSummary(
                    mode = current.selectedMode,
                    provider = activeProvider.displayName,
                    model = activeModel,
                    pdfInfo = current.selectedPdf,
                    selectedPages = current.selectedPageIndices
                )
            )
        }
    }

    fun selectPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    queryPdfInfo(uri)
                }
                _uiState.value = _uiState.value.copy(
                    selectedPdf = info,
                    selectedPageIndices = (0 until info.pageCount).toSet(),
                    status = ConvertStatus.Idle
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = ConvertStatus.Error("Failed to load PDF: ${e.localizedMessage ?: "Unknown error"}")
                )
            }
        }
    }

    fun setSelectedPages(pages: Set<Int>) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedPageIndices = pages,
            effectiveEngineSummary = computeSummary(
                mode = current.selectedMode,
                provider = current.configuredProviderName,
                model = current.configuredModelName,
                pdfInfo = current.selectedPdf,
                selectedPages = pages
            )
        )
    }

    fun selectMode(mode: ConversionMode) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedMode = mode,
            effectiveEngineSummary = computeSummary(
                mode = mode,
                provider = current.configuredProviderName,
                model = current.configuredModelName,
                pdfInfo = current.selectedPdf,
                selectedPages = current.selectedPageIndices
            )
        )
    }

    fun startConversion() {
        val pdf = _uiState.value.selectedPdf ?: return
        val mode = _uiState.value.selectedMode
        val pagesToProcess = _uiState.value.selectedPageIndices.toList().sorted()
        val totalToProcess = pagesToProcess.size

        viewModelScope.launch {
            val app = getApplication<com.markdrop.app.MarkdropApplication>()

            _uiState.value = _uiState.value.copy(
                status = ConvertStatus.Converting(currentPage = 0, totalPages = totalToProcess, progress = 0f)
            )

            try {
                // Insert job record into Room DB
                val entity = com.markdrop.app.data.db.ConversionEntity(
                    fileName = pdf.name,
                    pdfUri = pdf.uri.toString(),
                    mode = mode,
                    status = com.markdrop.app.data.db.ConversionStatus.PENDING,
                    pageCount = totalToProcess,
                    pagesCompleted = 0,
                    progress = 0f,
                    fileSizeBytes = pdf.sizeBytes
                )
                val jobId = app.conversionRepository.insert(entity)

                // Enqueue background WorkManager job with foreground notification
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.markdrop.app.conversion.worker.ConversionWorker>()
                    .setInputData(androidx.work.workDataOf(com.markdrop.app.conversion.worker.ConversionWorker.KEY_JOB_ID to jobId))
                    .addTag("conversion_job_$jobId")
                    .build()

                androidx.work.WorkManager.getInstance(app).enqueue(workRequest)

                // Observe conversion progress and completion from Room DB
                app.conversionRepository.getByIdFlow(jobId).collect { currentJob ->
                    if (currentJob == null) return@collect
                    when (currentJob.status) {
                        com.markdrop.app.data.db.ConversionStatus.DONE,
                        com.markdrop.app.data.db.ConversionStatus.PARTIAL -> {
                            val result = ConversionResult(
                                markdown = currentJob.markdownContent ?: "",
                                pageCount = currentJob.pageCount,
                                durationMs = currentJob.durationMs,
                                mode = currentJob.mode
                            )
                            _uiState.value = _uiState.value.copy(status = ConvertStatus.Success(result))
                        }
                        com.markdrop.app.data.db.ConversionStatus.FAILED -> {
                            _uiState.value = _uiState.value.copy(
                                status = ConvertStatus.Error(currentJob.errorMessage ?: "Conversion failed")
                            )
                        }
                        com.markdrop.app.data.db.ConversionStatus.PROCESSING -> {
                            _uiState.value = _uiState.value.copy(
                                status = ConvertStatus.Converting(
                                    currentPage = currentJob.pagesCompleted,
                                    totalPages = currentJob.pageCount,
                                    progress = currentJob.progress,
                                    statusMessage = "Processing page ${currentJob.pagesCompleted} of ${currentJob.pageCount}..."
                                )
                            )
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = ConvertStatus.Error(e.localizedMessage ?: "Conversion failed")
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedPdf = null,
            status = ConvertStatus.Idle
        )
    }

    private fun queryPdfInfo(uri: Uri): SelectedPdfInfo {
        val context = getApplication<Application>()
        var name = "document.pdf"
        var size: Long = 0

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        val type = context.contentResolver.getType(uri) ?: ""
        val isImage = type.startsWith("image/") ||
                name.endsWith(".png", true) ||
                name.endsWith(".jpg", true) ||
                name.endsWith(".jpeg", true) ||
                name.endsWith(".webp", true)

        val pages = if (isImage) {
            1
        } else {
            try {
                val renderer = PdfPageRenderer(context, uri)
                val count = renderer.pageCount
                renderer.close()
                count
            } catch (_: Exception) {
                1
            }
        }

        return SelectedPdfInfo(
            uri = uri,
            name = name,
            sizeBytes = size,
            pageCount = pages
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConvertViewModel(application) as T
        }
    }
}
