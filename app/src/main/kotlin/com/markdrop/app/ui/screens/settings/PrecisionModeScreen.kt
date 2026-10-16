package com.markdrop.app.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.settings.ConversionSettings
import com.markdrop.app.model.DefaultModelManifest
import com.markdrop.app.model.ModelDownloadState
import com.markdrop.app.model.ModelManager
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecisionModeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as MarkdropApplication

    val viewModel: PrecisionModeViewModel = viewModel(
        factory = PrecisionModeViewModel.Factory(
            application = app,
            settingsRepository = app.settingsRepository,
            modelManager = app.modelManager
        )
    )
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val conversionSettings by viewModel.hfTokenFlow.collectAsStateWithLifecycle(
        initialValue = ConversionSettings()
    )
    val repoAssets by viewModel.repoAssets.collectAsStateWithLifecycle()
    val isLoadingAssets by viewModel.isLoadingAssets.collectAsStateWithLifecycle()
    val assetInspectionError by viewModel.assetInspectionError.collectAsStateWithLifecycle()
    val activeTarget by viewModel.activeRepositoryTarget.collectAsStateWithLifecycle()
    var showAssetPicker by remember { mutableStateOf(false) }

    var hfTokenInput by remember(conversionSettings.huggingFaceToken) {
        mutableStateOf(conversionSettings.huggingFaceToken ?: "")
    }
    var hfTokenVisible by remember { mutableStateOf(false) }
    var customUrlInput by remember(conversionSettings.customModelDownloadUrl) {
        mutableStateOf(conversionSettings.customModelDownloadUrl ?: "")
    }
    var showDeveloperOptions by remember { mutableStateOf(!conversionSettings.customModelDownloadUrl.isNullOrBlank()) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Precision Mode (Local AI)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Suitability Banner with Dynamic RAM Warning
            val isRamSufficient = viewModel.isRamSufficient()
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isRamSufficient) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, if (isRamSufficient) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRamSufficient) Icons.Rounded.Memory else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isRamSufficient) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = String.format("%.1f GB RAM Detected", viewModel.deviceRamGb),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isRamSufficient) {
                                "Device meets recommended specs (${selectedModel.minRamGb}GB+ RAM)"
                            } else {
                                "High Memory Pressure: Low tokens/sec expected. Device has ${String.format("%.1f", viewModel.deviceRamGb)} GB RAM, but ${selectedModel.displayName} recommends ${selectedModel.minRamGb}GB+."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Model Selection
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedModel.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    viewModel.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                viewModel.selectModel(model.modelId)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // Model Details Card
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModel.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    viewModel.inspectRepositoryTree(hfToken = conversionSettings.huggingFaceToken)
                                    showAssetPicker = true
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = "Inspect repository assets and mirrors",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedModel.assetConfig.repoTreeUrl()))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "View repository on Hugging Face",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Repository: ${selectedModel.assetConfig.repoId} (branch: ${selectedModel.assetConfig.revision})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedModel.mirrors.isNotEmpty()) {
                        Text(
                            text = "Mirrors: ${selectedModel.mirrors.size} alternative mirror(s) available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Primary Asset: ${selectedModel.assetConfig.assetPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version: ${selectedModel.version} • Quantization: ${selectedModel.quantization.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Download size: ~${selectedModel.fileSizeBytes / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hugging Face Access Token Card (for Gated Models like Gemma)
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
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
                            Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Hugging Face Access Token",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "Gemma and other gated models require accepting the model license on Hugging Face. Paste a 'Read' token below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = hfTokenInput,
                        onValueChange = { hfTokenInput = it },
                        label = { Text("HF Access Token (hf_...)") },
                        singleLine = true,
                        visualTransformation = if (hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
                                Icon(
                                    imageVector = if (hfTokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (hfTokenVisible) "Hide token" else "Show token"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Get a Token →", style = MaterialTheme.typography.labelMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (conversionSettings.huggingFaceToken != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.saveHfToken(null)
                                        hfTokenInput = ""
                                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    }
                                ) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.saveHfToken(hfTokenInput.trim().ifEmpty { null })
                                    HapticHelper.performHaptic(view, HapticType.SUCCESS, true)
                                }
                            ) {
                                Text("Save Token")
                            }
                        }
                    }
                }
            }

            // Developer & Custom URL Override Card
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Developer & Custom URL Override",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        IconButton(onClick = { showDeveloperOptions = !showDeveloperOptions }) {
                            Icon(
                                if (showDeveloperOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (showDeveloperOptions) "Collapse" else "Expand"
                            )
                        }
                    }

                    if (showDeveloperOptions) {
                        Text(
                            text = "Override default Hugging Face routing with a direct download link or mirror URL (e.g. self-hosted .task or .litertlm file).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("Direct Download URL Override") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!conversionSettings.customModelDownloadUrl.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        viewModel.saveCustomDownloadUrl(null)
                                        customUrlInput = ""
                                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    }
                                ) {
                                    Text("Reset Default", color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = {
                                    viewModel.saveCustomDownloadUrl(customUrlInput.trim().ifEmpty { null })
                                    HapticHelper.performHaptic(view, HapticType.SUCCESS, true)
                                }
                            ) {
                                Text("Save URL")
                            }
                        }
                    } else if (!conversionSettings.customModelDownloadUrl.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Custom URL Active: ${conversionSettings.customModelDownloadUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Download State / Action Section
            when (val state = downloadState) {
                is ModelDownloadState.Idle -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Model Not Downloaded",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Precision Mode runs completely on your device without internet. It requires downloading the model weights (~${selectedModel.fileSizeBytes / (1024 * 1024)} MB).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                    viewModel.startDownload(
                                        conversionSettings.huggingFaceToken,
                                        conversionSettings.customModelDownloadUrl
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Model (~${selectedModel.fileSizeBytes / (1024 * 1024)} MB)")
                            }
                        }
                    }
                }
                is ModelDownloadState.Downloading -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
                        label = "DownloadSmoothing"
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.statusMessage ?: "Downloading Model...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(onClick = { viewModel.cancelDownload() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
                is ModelDownloadState.Verifying -> {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Verifying checksum...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ModelDownloadState.Downloaded -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Model Ready", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "Precision Mode is enabled and available on the Convert tab.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                        viewModel.deleteModel()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete Model")
                                }
                            }
                        }
                    }
                }
                is ModelDownloadState.Error -> {
                    val isGated = state.errorType == ModelDownloadState.Error.ErrorType.GATED_ACCESS || state.httpCode == 403
                    val isAuth = state.errorType == ModelDownloadState.Error.ErrorType.UNAUTHORIZED || state.httpCode == 401
                    val isNotFound = state.errorType == ModelDownloadState.Error.ErrorType.NOT_FOUND || state.httpCode == 404
                    val isWebOnly = state.errorType == ModelDownloadState.Error.ErrorType.WEB_ONLY_REPO || state.isWebOnlyRepo
                    val isPreVal = state.errorType == ModelDownloadState.Error.ErrorType.PRE_VALIDATION

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWebOnly) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                            }
                        ),
                        border = BorderStroke(1.dp, if (isWebOnly) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    imageVector = when {
                                        isWebOnly -> Icons.Rounded.Block
                                        isAuth -> Icons.Rounded.KeyOff
                                        isGated -> Icons.Rounded.Lock
                                        isNotFound -> Icons.Rounded.SearchOff
                                        isPreVal -> Icons.Rounded.Shield
                                        else -> Icons.Rounded.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = if (isWebOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = when {
                                        isWebOnly -> "Incompatible Web Weights Detected"
                                        isAuth -> "Invalid / Missing HF Token (HTTP 401)"
                                        isGated -> "Gated Model Access Required (HTTP 403)"
                                        isNotFound -> "Model Asset Not Found (HTTP 404)"
                                        isPreVal -> "Pre-Validation Check Failed"
                                        else -> "Download Failed"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isWebOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWebOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )

                            if (!state.failingUrl.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Target Endpoint:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = state.failingUrl,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (state.suggestedMirrors.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Alternative Mirrors Available:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (isWebOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        state.suggestedMirrors.forEach { mirror ->
                                            AssistChip(
                                                onClick = {
                                                    viewModel.selectRepositoryTarget(mirror, conversionSettings.huggingFaceToken)
                                                    showAssetPicker = true
                                                },
                                                label = { Text("Inspect ${mirror.repoId.substringAfterLast('/')}") },
                                                leadingIcon = { Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (isNotFound && !state.suggestedOverrideUrl.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Auto-Discovered Compatible Weights:",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = state.discoveredCandidates.firstOrNull()?.fileName ?: state.suggestedOverrideUrl!!,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    customUrlInput = state.suggestedOverrideUrl!!
                                                    viewModel.saveCustomDownloadUrl(state.suggestedOverrideUrl)
                                                    showDeveloperOptions = true
                                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Pre-populate URL", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = {
                                                    val best = state.discoveredCandidates.firstOrNull()
                                                    val target = activeTarget
                                                    if (best != null) {
                                                        viewModel.startDownloadWithAsset(best, target, conversionSettings.huggingFaceToken)
                                                    } else {
                                                        viewModel.startDownload(conversionSettings.huggingFaceToken, state.suggestedOverrideUrl)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Download Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }

                            // Contextual Quick Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.inspectRepositoryTree(hfToken = conversionSettings.huggingFaceToken)
                                        showAssetPicker = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Inspect Assets", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (isNotFound || isPreVal || isWebOnly) {
                                    OutlinedButton(
                                        onClick = { showDeveloperOptions = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Custom URL", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else if (isGated || isAuth) {
                                    val targetRepoUrl = state.repoUrl ?: selectedModel.assetConfig.repoTreeUrl()
                                    OutlinedButton(
                                        onClick = {
                                            val url = if (isAuth) "https://huggingface.co/settings/tokens" else targetRepoUrl
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (isGated) "Accept Terms →" else "HF Settings →",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.startDownload(
                                                conversionSettings.huggingFaceToken,
                                                conversionSettings.customModelDownloadUrl
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        if (showAssetPicker) {
            ModelAssetPickerBottomSheet(
                manifest = selectedModel,
                activeTarget = activeTarget,
                assets = repoAssets,
                isLoading = isLoadingAssets,
                errorMessage = assetInspectionError,
                onTargetSelected = { target ->
                    viewModel.selectRepositoryTarget(target, conversionSettings.huggingFaceToken)
                },
                onAssetSelected = { asset, target ->
                    showAssetPicker = false
                    viewModel.startDownloadWithAsset(asset, target, conversionSettings.huggingFaceToken)
                },
                onRefresh = {
                    viewModel.inspectRepositoryTree(activeTarget, conversionSettings.huggingFaceToken)
                },
                onDismiss = {
                    showAssetPicker = false
                }
            )
        }
    }
}
