package com.markdrop.app.ui.screens.convert

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.conversion.ConversionMode
import com.markdrop.app.conversion.ConversionResult
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: ConvertViewModel = viewModel(
        factory = ConvertViewModel.Factory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPageRangeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshAvailability()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            HapticHelper.performHaptic(view, HapticType.LIGHT, true)
            viewModel.selectPdf(uri)
        }
    }

    if (showPageRangeDialog && uiState.selectedPdf != null) {
        PageRangeSelectorDialog(
            pdfUri = uiState.selectedPdf!!.uri,
            totalPages = uiState.selectedPdf!!.pageCount,
            initialSelectedPages = uiState.selectedPageIndices,
            onDismiss = { showPageRangeDialog = false },
            onConfirm = {
                viewModel.setSelectedPages(it)
                showPageRangeDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Markdrop",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (uiState.selectedPdf != null) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear selected file")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header tagline
            Text(
                text = "Drop a PDF in, get clean Markdown out.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            // PDF Picker / Document Card
            if (uiState.selectedPdf == null) {
                PdfDropArea(
                    onClick = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        filePickerLauncher.launch(arrayOf("application/pdf"))
                    }
                )
            } else {
                PdfInfoCard(
                    pdfInfo = uiState.selectedPdf!!,
                    selectedPagesCount = uiState.selectedPageIndices.size,
                    onEditPages = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        showPageRangeDialog = true
                    },
                    onChangeFile = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        filePickerLauncher.launch(arrayOf("application/pdf"))
                    }
                )
            }

            // Mode Selection Segmented Buttons
            ModeSelectorSection(
                selectedMode = uiState.selectedMode,
                isPrecisionAvailable = uiState.isPrecisionAvailable,
                isAdvancedAvailable = uiState.isAdvancedAvailable,
                onModeSelected = { mode ->
                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    viewModel.selectMode(mode)
                }
            )

            // Mode Explanation & Known Limitations Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (uiState.selectedMode) {
                                ConversionMode.FAST -> Icons.Rounded.Bolt
                                ConversionMode.SMART -> Icons.Rounded.AutoAwesome
                                ConversionMode.PRECISION -> Icons.Rounded.Psychology
                                ConversionMode.ADVANCED -> Icons.Rounded.Cloud
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${uiState.selectedMode.title} Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.selectedMode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Conversion Status / Progress
            when (val status = uiState.status) {
                is ConvertStatus.Converting -> {
                    ConversionProgressCard(status = status)
                }
                is ConvertStatus.Error -> {
                    ErrorCard(
                        message = status.message,
                        onRetry = {
                            HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                            viewModel.startConversion()
                        },
                        onRunFastMode = {
                            HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                            viewModel.selectMode(ConversionMode.FAST)
                            viewModel.startConversion()
                        },
                        onRunAdvancedMode = {
                            HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                            viewModel.selectMode(ConversionMode.ADVANCED)
                            if (uiState.isAdvancedAvailable) {
                                viewModel.startConversion()
                            } else if (onNavigateToSettings != null) {
                                Toast.makeText(context, "Configure your API key in Settings > Advanced Mode", Toast.LENGTH_LONG).show()
                                onNavigateToSettings()
                            } else {
                                Toast.makeText(context, "Configure your API key in Settings > Advanced Mode", Toast.LENGTH_LONG).show()
                            }
                        },
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                is ConvertStatus.Success -> {
                    SuccessResultCard(
                        result = status.result,
                        onCopy = {
                            copyToClipboard(context, status.result.markdown)
                            Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                            HapticHelper.performHaptic(view, HapticType.SUCCESS, true)
                        },
                        onShare = {
                            shareMarkdown(context, status.result.markdown)
                        }
                    )
                }
                ConvertStatus.Idle -> {}
            }

            // Pre-Flight Estimator Status Card
            if (uiState.selectedPdf != null) {
                PreFlightEstimatorCard(
                    mode = uiState.selectedMode,
                    selectedPagesCount = uiState.selectedPageIndices.size,
                    totalPages = uiState.selectedPdf!!.pageCount,
                    configuredProvider = uiState.configuredProviderName,
                    configuredModel = uiState.configuredModelName,
                    promptPricePerToken = uiState.promptPricePerToken,
                    deviceRamGb = uiState.deviceRamGb,
                    isRamSufficient = uiState.isRamSufficient
                )
            }

            // Prompt user if Precision Mode is selected but no compatible local model is ready
            if (uiState.selectedPdf != null && uiState.selectedMode == ConversionMode.PRECISION && !uiState.isPrecisionAvailable) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Precision Model Not Ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "A verified SentencePiece-compatible model (such as PaliGemma 2) is required for on-device Precision Mode. You can convert right now using Fast Mode or Advanced Mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    viewModel.selectMode(ConversionMode.FAST)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fast Mode (OCR)", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    viewModel.selectMode(ConversionMode.ADVANCED)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Advanced Mode", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Convert Action Button
            val isConverting = uiState.status is ConvertStatus.Converting
            val hasSelectedPages = uiState.selectedPageIndices.isNotEmpty()
            val isModeReady = when (uiState.selectedMode) {
                ConversionMode.FAST, ConversionMode.SMART -> true
                ConversionMode.PRECISION -> uiState.isPrecisionAvailable
                ConversionMode.ADVANCED -> uiState.isAdvancedAvailable
            }
            val canConvert = uiState.selectedPdf != null && hasSelectedPages && isModeReady && !isConverting

            Button(
                onClick = {
                    HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                    viewModel.startConversion()
                },
                enabled = canConvert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isConverting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Converting...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Rounded.Transform, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.selectedMode == ConversionMode.PRECISION && !uiState.isPrecisionAvailable) "Precision Model Not Ready"
                        else if (uiState.selectedMode == ConversionMode.ADVANCED && !uiState.isAdvancedAvailable) "Configure Key in Settings"
                        else if (uiState.selectedPageIndices.isNotEmpty()) "Convert ${uiState.selectedPageIndices.size} Pages"
                        else "Convert to Markdown",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Bottom clearance for floating glass bottom nav
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun PdfDropArea(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Select a PDF file",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to browse your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PdfInfoCard(
    pdfInfo: SelectedPdfInfo,
    selectedPagesCount: Int,
    onEditPages: () -> Unit,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pdfInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pdfInfo.pageCount} pages total • ${formatFileSize(pdfInfo.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onChangeFile) {
                    Text("Change")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Page range selection chip row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected Pages:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AssistChip(
                    onClick = onEditPages,
                    label = { Text("$selectedPagesCount of ${pdfInfo.pageCount} Pages") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectorSection(
    selectedMode: ConversionMode,
    isPrecisionAvailable: Boolean,
    isAdvancedAvailable: Boolean,
    onModeSelected: (ConversionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(ConversionMode.FAST, ConversionMode.SMART, ConversionMode.PRECISION, ConversionMode.ADVANCED)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Conversion Mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selectedMode == mode)
                    }
                ) {
                    Text(mode.title)
                }
            }
        }
    }
}

@Composable
private fun PreFlightEstimatorCard(
    mode: ConversionMode,
    selectedPagesCount: Int,
    totalPages: Int,
    configuredProvider: String,
    configuredModel: String,
    promptPricePerToken: Double? = null,
    deviceRamGb: Double,
    isRamSufficient: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (mode) {
                        ConversionMode.FAST -> Icons.Rounded.Speed
                        ConversionMode.SMART -> Icons.Rounded.AutoAwesome
                        ConversionMode.PRECISION -> Icons.Rounded.Memory
                        ConversionMode.ADVANCED -> Icons.Rounded.Paid
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Pre-Flight Estimator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (mode) {
                ConversionMode.FAST -> {
                    Text(
                        text = "• Pages: $selectedPagesCount of $totalPages\n• Cost: $0.00 (100% Free & Offline)\n• Tokens: 0 · Instant on-device OCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConversionMode.SMART -> {
                    Text(
                        text = "• Pages: $selectedPagesCount of $totalPages\n• Routing: Hybrid intelligent (OCR for text, AI for tables/math)\n• Token Savings: ~50–70% compared to full AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConversionMode.PRECISION -> {
                    val ramText = String.format(java.util.Locale.US, "%.1f GB", deviceRamGb)
                    val ramWarning = if (deviceRamGb < 8.0) "\n• High Memory Pressure: Slower inference expected (<8GB RAM)" else ""
                    Text(
                        text = "• Pages: $selectedPagesCount of $totalPages\n• Engine: Local on-device LiteRT runtime\n• Memory: $ramText RAM detected (${if (isRamSufficient) "Sufficient" else "Warning: 6GB+ recommended"})$ramWarning",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRamSufficient && deviceRamGb >= 8.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                ConversionMode.ADVANCED -> {
                    val estInputTokens = selectedPagesCount * 2100
                    val estOutputTokens = selectedPagesCount * 500
                    val estTotalTokens = estInputTokens + estOutputTokens
                    val costEstimate = if (promptPricePerToken != null) {
                        String.format(java.util.Locale.US, "$%.4f", estInputTokens * promptPricePerToken)
                    } else when {
                        configuredModel.contains("flash", ignoreCase = true) -> String.format(java.util.Locale.US, "$%.4f", estTotalTokens * 0.0000003)
                        configuredModel.contains("mini", ignoreCase = true) -> String.format(java.util.Locale.US, "$%.4f", estTotalTokens * 0.0000006)
                        configuredModel.contains("sonnet", ignoreCase = true) -> String.format(java.util.Locale.US, "$%.3f", estTotalTokens * 0.000005)
                        else -> String.format(java.util.Locale.US, "$%.4f", estTotalTokens * 0.0000015)
                    }

                    Text(
                        text = "• Pages: $selectedPagesCount of $totalPages\n• Target: $configuredProvider ($configuredModel)\n• Est. Tokens: ~$estTotalTokens (~$estInputTokens in / ~$estOutputTokens out)\n• Est. API Cost: ~$costEstimate USD (billed directly by provider)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionProgressCard(
    status: ConvertStatus.Converting,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.statusMessage ?: "Converting page ${status.currentPage} of ${status.totalPages}...",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(status.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun SuccessResultCard(
    result: ConversionResult,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Conversion Complete",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text(
                    text = "${result.durationMs / 1000f}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Markdown preview snippet
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                Text(
                    text = result.markdown.take(400) + if (result.markdown.length > 400) "..." else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }

                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onRunFastMode: () -> Unit,
    onRunAdvancedMode: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPrecisionOrTokenizerError = message.contains("Incompatible Tokenizer", ignoreCase = true) ||
            message.contains("SentencePiece", ignoreCase = true) ||
            message.contains("Precision Mode", ignoreCase = true) ||
            message.contains("local AI", ignoreCase = true) ||
            message.contains("model is not downloaded", ignoreCase = true) ||
            message.contains("LiteRT", ignoreCase = true)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (isPrecisionOrTokenizerError) "Local AI Incompatibility" else "Conversion Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            if (isPrecisionOrTokenizerError) {
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                Text(
                    text = "Recommended Alternatives:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRunFastMode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fast Mode (OCR)", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = onRunAdvancedMode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Advanced Mode", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPrecisionOrTokenizerError && onNavigateToSettings != null) {
                    TextButton(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settings")
                    }
                }
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Markdown", text)
    clipboard.setPrimaryClip(clip)
}

private fun shareMarkdown(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Markdown")
    context.startActivity(shareIntent)
}
