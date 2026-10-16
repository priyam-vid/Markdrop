package com.markdrop.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.settings.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsRepository = (context.applicationContext as MarkdropApplication).settingsRepository
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(settingsRepository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedModeScreen by remember { mutableStateOf(false) }
    var showPrecisionModeScreen by remember { mutableStateOf(false) }

    if (showAdvancedModeScreen) {
        AdvancedModeScreen(onBack = { showAdvancedModeScreen = false })
        return
    }

    if (showPrecisionModeScreen) {
        PrecisionModeScreen(onBack = { showPrecisionModeScreen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Theme Section
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
            val themeModeLabels = listOf("System", "Light", "Dark")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                themeModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.themeSettings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size)
                    ) {
                        Text(themeModeLabels[index])
                    }
                }
            }

            Text(
                text = "Seed Color",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ColorPaletteGrid(
                selectedHex = uiState.themeSettings.seedColorHex,
                onColorSelected = { hex ->
                    viewModel.setSeedColor(hex)
                    viewModel.setUseDynamicColor(false)
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ListItem(
                headlineContent = { Text("Dynamic Color") },
                supportingContent = { Text(if (isAndroid12Plus) "Use wallpaper colors" else "Requires Android 12+") },
                trailingContent = {
                    Switch(
                        checked = uiState.themeSettings.useDynamicColor,
                        onCheckedChange = { viewModel.setUseDynamicColor(it) },
                        enabled = isAndroid12Plus
                    )
                }
            )

            val isDarkMode = uiState.themeSettings.themeMode == ThemeMode.DARK || 
                             (uiState.themeSettings.themeMode == ThemeMode.SYSTEM)
            ListItem(
                headlineContent = { Text("AMOLED Black") },
                supportingContent = { Text("True black background in dark mode") },
                trailingContent = {
                    Switch(
                        checked = uiState.themeSettings.useAmoled,
                        onCheckedChange = { viewModel.setUseAmoled(it) },
                        enabled = isDarkMode
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Conversion Section
            Text(
                text = "Conversion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Precision Mode (Local AI)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Download an on-device multimodal AI model (~2.0 GB) for higher quality offline transcription and complex tables.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { showPrecisionModeScreen = true }) {
                        Text("Configure Model")
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Advanced Mode (BYOK)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Bring your own API key for Gemini, OpenAI, Claude, or OpenRouter for frontier-quality multimodal transcription.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { showAdvancedModeScreen = true }) {
                        Text("Configure API Keys")
                    }
                }
            }

            ListItem(
                headlineContent = { Text("Strip Headers & Footers") },
                supportingContent = { Text("Automatically remove recurring page headers, footers, and page numbers from generated Markdown") },
                trailingContent = {
                    Switch(
                        checked = uiState.conversionSettings.stripHeaderFooterEnabled,
                        onCheckedChange = { viewModel.setStripHeaderFooterEnabled(it) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Default Mode") },
                supportingContent = { Text("Fast") }
            )

            ListItem(
                headlineContent = { Text("Local Concurrency") },
                supportingContent = { 
                    Slider(
                        value = uiState.conversionSettings.localConcurrency.toFloat(),
                        onValueChange = { viewModel.setLocalConcurrency(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                },
                trailingContent = { Text(uiState.conversionSettings.localConcurrency.toString()) }
            )

            ListItem(
                headlineContent = { Text("Cloud Concurrency") },
                supportingContent = { 
                    Slider(
                        value = uiState.conversionSettings.cloudConcurrency.toFloat(),
                        onValueChange = { viewModel.setCloudConcurrency(it.roundToInt()) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                },
                trailingContent = { Text(uiState.conversionSettings.cloudConcurrency.toString()) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Interaction Section
            Text(
                text = "Interaction",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Haptic Feedback") },
                trailingContent = {
                    Switch(
                        checked = uiState.interactionSettings.hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListItem(
                headlineContent = { Text("App Version") },
                trailingContent = { Text("1.0.0") }
            )

            ListItem(
                headlineContent = { Text("Open Source Licenses") },
                modifier = Modifier.padding(bottom = 80.dp)
            )
        }
    }
}
