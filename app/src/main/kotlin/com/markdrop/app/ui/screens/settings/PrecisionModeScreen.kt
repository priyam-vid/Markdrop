package com.markdrop.app.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.settings.ConversionSettings
import com.markdrop.app.model.ModelDownloadState
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
    val isModelVerified by viewModel.isModelVerified.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
    val verificationMessage by viewModel.verificationMessage.collectAsStateWithLifecycle()
    val conversionSettings by viewModel.hfTokenFlow.collectAsStateWithLifecycle(
        initialValue = ConversionSettings()
    )

    var hfTokenInput by remember(conversionSettings.huggingFaceToken) {
        mutableStateOf(conversionSettings.huggingFaceToken ?: "")
    }
    var hfTokenVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On-Device AI (Experimental)") },
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
            // Hardware Suitability Banner with RAM Info
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
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
                            }
                        )
                    }
                }
            }

            // Hugging Face Token Section
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
                            Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Hugging Face Access Token", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Text(
                        text = "Official Gemma weights require accepting license terms on Hugging Face. A read-only token is required to download gated models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = hfTokenInput,
                        onValueChange = { hfTokenInput = it },
                        label = { Text("HF Token (hf_...)") },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                        singleLine = true,
                        visualTransformation = if (hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
                                Icon(
                                    if (hfTokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (hfTokenVisible) "Hide token" else "Show token"
                                )
                            }
                        },
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
                                text = "On-Device AI runs completely on your phone without internet. Requires downloading model weights (~${selectedModel.fileSizeBytes / (1024 * 1024)} MB).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                    viewModel.startDownload(conversionSettings.huggingFaceToken)
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
                        Text("Verifying model archive...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ModelDownloadState.Downloaded -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
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
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text("Model Ready for On-Device Inference", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "Size: ${state.fileSizeBytes / (1024 * 1024)} MB. Ready for local multimodal transcription.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Verification status
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isModelVerified) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = if (isModelVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isModelVerified) "Model Verified via Vision Smoke Test" else "Model Unverified (Smoke Test Pending/Failed)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isModelVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            if (verificationMessage != null) {
                                Text(
                                    text = verificationMessage ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                        viewModel.runSmokeTest()
                                    },
                                    enabled = !isVerifying,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verifying...")
                                    } else {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verify Model")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                        viewModel.deleteModel()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
                is ModelDownloadState.Error -> {
                    val isAuth = state.httpCode == 401 || state.errorType == ModelDownloadState.Error.ErrorType.UNAUTHORIZED
                    val isGated = state.httpCode == 403 || state.errorType == ModelDownloadState.Error.ErrorType.GATED_ACCESS

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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
                                        isAuth -> Icons.Rounded.KeyOff
                                        isGated -> Icons.Rounded.Lock
                                        else -> Icons.Rounded.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = when {
                                        isAuth -> "Invalid / Missing HF Token (HTTP 401)"
                                        isGated -> "Gated Model Access Required (HTTP 403)"
                                        else -> "Download Failed"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isGated || isAuth) {
                                    val targetRepoUrl = state.repoUrl ?: selectedModel.assetConfig.repoTreeUrl()
                                    OutlinedButton(
                                        onClick = {
                                            val url = if (isAuth) "https://huggingface.co/settings/tokens" else targetRepoUrl
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isGated) "Accept Terms →" else "HF Settings →")
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.startDownload(conversionSettings.huggingFaceToken)
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

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
