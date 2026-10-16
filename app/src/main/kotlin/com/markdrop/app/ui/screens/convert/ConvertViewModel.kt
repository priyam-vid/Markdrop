package com.markdrop.app.ui.screens.convert

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markdrop.app.conversion.ConversionEngine
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
    val selectedMode: ConversionMode = ConversionMode.FAST,
    val status: ConvertStatus = ConvertStatus.Idle,
    val isPrecisionAvailable: Boolean = false,
    val isAdvancedAvailable: Boolean = false,
    val configuredProviderName: String = "",
    val configuredModelName: String = "",
    val promptPricePerToken: Double? = null,
    val deviceRamGb: Double = 0.0,
    val isRamSufficient: Boolean = true
)

class ConvertViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val conversionEngine = ConversionEngine(application)
    private val secureKeyStore = com.markdrop.app.data.security.SecureKeyStore(application)
    private val modelManager = (application as com.markdrop.app.MarkdropApplication).modelManager

    private val _uiState = MutableStateFlow(ConvertScreenUiState())
    val uiState: StateFlow<ConvertScreenUiState> = _uiState.asStateFlow()

    init {
        refreshAvailability()
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

            _uiState.value = _uiState.value.copy(
                isPrecisionAvailable = isModelReady,
                isAdvancedAvailable = hasKey,
                configuredProviderName = activeProvider.displayName,
                configuredModelName = activeModel,
                promptPricePerToken = pricing,
                deviceRamGb = ramGb,
                isRamSufficient = isRamSufficient
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
        _uiState.value = _uiState.value.copy(selectedPageIndices = pages)
    }

    fun selectMode(mode: ConversionMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun startConversion() {
        val pdf = _uiState.value.selectedPdf ?: return
        val mode = _uiState.value.selectedMode
        val pagesToProcess = _uiState.value.selectedPageIndices.toList().sorted()
        val totalToProcess = pagesToProcess.size

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = ConvertStatus.Converting(currentPage = 0, totalPages = totalToProcess, progress = 0f)
            )

            try {
                val result = conversionEngine.convertPdf(
                    pdfUri = pdf.uri,
                    mode = mode,
                    selectedPages = pagesToProcess,
                    onProgress = { current, total, statusMsg ->
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        _uiState.value = _uiState.value.copy(
                            status = ConvertStatus.Converting(
                                currentPage = current,
                                totalPages = total,
                                progress = progress,
                                statusMessage = statusMsg
                            )
                        )
                    }
                )

                // Save to Room DB for Library history
                val app = getApplication<com.markdrop.app.MarkdropApplication>()
                app.conversionRepository.insert(
                    com.markdrop.app.data.db.ConversionEntity(
                        fileName = pdf.name,
                        pdfUri = pdf.uri.toString(),
                        markdownContent = result.markdown,
                        mode = mode,
                        status = com.markdrop.app.data.db.ConversionStatus.DONE,
                        pageCount = result.pageCount,
                        pagesCompleted = result.pageCount,
                        progress = 1.0f,
                        durationMs = result.durationMs,
                        completedAt = System.currentTimeMillis(),
                        fileSizeBytes = pdf.sizeBytes
                    )
                )

                _uiState.value = _uiState.value.copy(status = ConvertStatus.Success(result))
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

        val renderer = PdfPageRenderer(context, uri)
        val pages = renderer.pageCount
        renderer.close()

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
