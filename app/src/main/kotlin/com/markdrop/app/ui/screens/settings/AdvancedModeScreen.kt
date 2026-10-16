package com.markdrop.app.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.conversion.cloud.CloudProvider
import com.markdrop.app.data.security.SecureKeyStore
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedModeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as MarkdropApplication

    val secureKeyStore = remember { SecureKeyStore(context) }
    val viewModel: AdvancedModeViewModel = viewModel(
        factory = AdvancedModeViewModel.Factory(
            application = app,
            settingsRepository = app.settingsRepository,
            secureKeyStore = secureKeyStore
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Mode (BYOK)") },
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
            // Notice banner explaining BYOK billing
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Advanced Mode sends your PDF pages to a third-party AI vision endpoint using your own API key. You are billed directly by that provider, not by this app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = "Configured Providers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Provider Cards
            CloudProvider.entries.forEach { provider ->
                val state = uiState.providerStates[provider] ?: ProviderState(
                    provider = provider,
                    apiKey = "",
                    selectedModel = provider.defaultModel,
                    hasKey = false
                )
                val isActive = uiState.activeProvider == provider

                ProviderCard(
                    provider = provider,
                    state = state,
                    isActive = isActive,
                    onSelectActive = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        viewModel.setActiveProvider(provider)
                    },
                    onKeyChange = { viewModel.updateApiKeyInput(provider, it) },
                    onModelSelect = { viewModel.selectModel(provider, it) },
                    onTestConnection = {
                        HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                        viewModel.testConnection(provider)
                    },
                    onRemoveKey = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        viewModel.removeKey(provider)
                    },
                    onSyncModels = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        viewModel.syncModels(provider)
                    },
                    onOpenKeyPage = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyPageUrl))
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    provider: CloudProvider,
    state: ProviderState,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onKeyChange: (String) -> Unit,
    onModelSelect: (String) -> Unit,
    onTestConnection: () -> Unit,
    onRemoveKey: () -> Unit,
    onSyncModels: () -> Unit,
    onOpenKeyPage: () -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row with Radio and Provider Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = isActive,
                        onClick = onSelectActive,
                        enabled = state.hasKey
                    )
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(onClick = onOpenKeyPage) {
                    Text("Get a key →", style = MaterialTheme.typography.labelMedium)
                }
            }

            // API Key Input
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onKeyChange,
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Model Selector Dropdown & Live Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vision Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        state.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    onModelSelect(model)
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSyncModels,
                    enabled = !state.isSyncingModels && state.apiKey.isNotBlank()
                ) {
                    if (state.isSyncingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Sync models from provider",
                            tint = if (state.apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (state.syncTimestamp != null) {
                val formattedTime = remember(state.syncTimestamp) {
                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(state.syncTimestamp))
                }
                Text(
                    text = "Models synced: $formattedTime (${state.availableModels.size} available)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.syncError != null) {
                Text(
                    text = "Sync notice: ${state.syncError} (using fallback models)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Test Connection Status Message
            when (val status = state.testStatus) {
                is TestConnectionStatus.Testing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Validating key with ${provider.displayName}...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is TestConnectionStatus.Success -> {
                    Text(
                        text = "✓ ${status.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is TestConnectionStatus.Error -> {
                    Text(
                        text = "✗ ${status.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TestConnectionStatus.Idle -> {}
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.hasKey) {
                    TextButton(
                        onClick = onRemoveKey,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove Key")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                FilledTonalButton(
                    onClick = onTestConnection,
                    enabled = state.apiKey.isNotBlank() && state.testStatus !is TestConnectionStatus.Testing
                ) {
                    Text("Test Connection")
                }
            }
        }
    }
}
