package com.markdrop.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markdrop.app.model.ModelManifest
import com.markdrop.app.model.RepoAssetItem
import com.markdrop.app.model.RepositoryTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelAssetPickerBottomSheet(
    manifest: ModelManifest,
    activeTarget: RepositoryTarget,
    assets: List<RepoAssetItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onTargetSelected: (RepositoryTarget) -> Unit,
    onAssetSelected: (RepoAssetItem, RepositoryTarget) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterCompatibleOnly by remember { mutableStateOf(false) }
    var warningAssetItem by remember { mutableStateOf<RepoAssetItem?>(null) }

    val allTargets = manifest.assetConfig.allTargets()
    val displayedAssets = if (filterCompatibleOnly) {
        assets.filter { it.isAndroidCompatible }
    } else {
        assets
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Repository Asset Inspector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = manifest.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh repository tree")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Repository Target Selector (Primary vs Mirror)
            if (allTargets.size > 1) {
                Text(
                    text = "Select Repository Mirror:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allTargets.forEach { target ->
                        val isSelected = target.repoId == activeTarget.repoId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTargetSelected(target) },
                            label = {
                                Text(
                                    if (target.isMirror) "Mirror (${target.repoId.substringAfterLast('/')})"
                                    else "Primary (${target.repoId.substringAfterLast('/')})"
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Current target badge and filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${activeTarget.repoId} @ ${activeTarget.revision}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = filterCompatibleOnly,
                    onClick = { filterCompatibleOnly = !filterCompatibleOnly },
                    label = { Text("Compatible Only") },
                    leadingIcon = if (filterCompatibleOnly) {
                        { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Content Area
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = "Querying Hugging Face Model Tree API...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("Failed to inspect repository", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (displayedAssets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filterCompatibleOnly) "No compatible Android LiteRT weights found.\nToggle 'Compatible Only' to see all files."
                        else "No files found in repository tree.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedAssets, key = { it.path }) { item ->
                        AssetItemCard(
                            item = item,
                            onClick = {
                                if (item.isAndroidCompatible) {
                                    onAssetSelected(item, activeTarget)
                                } else {
                                    warningAssetItem = item
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Incompatible Web Asset Warning Dialog
    warningAssetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { warningAssetItem = null },
            icon = {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Incompatible Runtime Bundle")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The selected file '${item.fileName}' is packaged for WebAssembly / Browser runtimes.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Android native LiteRT engines will fail with 'Unable to open zip archive (zip_utils.cc:133)' because Wasm bundles do not contain the native C++ task archive structure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Downloads for WebAssembly / JS bundles are strictly disabled to protect app stability. Please select an Android native (.task or .litertlm) asset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = { warningAssetItem = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun AssetItemCard(
    item: RepoAssetItem,
    onClick: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (item.isAndroidCompatible) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    item.isAndroidCompatible -> Icons.Rounded.CheckCircle
                    item.isWebOnly -> Icons.Rounded.Block
                    else -> Icons.Rounded.Description
                },
                contentDescription = null,
                tint = when {
                    item.isAndroidCompatible -> MaterialTheme.colorScheme.primary
                    item.isWebOnly -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.directory.isNotEmpty()) {
                    Text(
                        text = item.directory,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedSize(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.isAndroidCompatible) {
                        SuggestionChip(
                            onClick = onClick,
                            label = { Text("Compatible (Android)", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null,
                            modifier = Modifier.height(24.dp)
                        )
                    } else if (item.isWebOnly) {
                        SuggestionChip(
                            onClick = onClick,
                            label = { Text("Incompatible (Web Only)", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            border = null,
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Select file",
                tint = if (item.isAndroidCompatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
