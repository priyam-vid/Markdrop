package com.markdrop.app.ui.screens.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.data.db.ConversionStatus
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onNavigateToConvert: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as MarkdropApplication

    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.Factory(app.conversionRepository, app.queueManager)
    )
    val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()

    var isPausedAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversion Queue", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (queueItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                if (isPausedAll) {
                                    viewModel.resumeAll()
                                    isPausedAll = false
                                } else {
                                    viewModel.pauseAll()
                                    isPausedAll = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPausedAll) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (isPausedAll) "Resume All" else "Pause All"
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (queueItems.isEmpty()) {
            EmptyQueueView(
                onNavigateToConvert = onNavigateToConvert,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                items(
                    items = queueItems,
                    key = { it.id }
                ) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                viewModel.deleteItem(item.id)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = MaterialTheme.colorScheme.errorContainer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete item",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        QueueItemCard(
                            item = item,
                            onPauseResume = {
                                HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                if (item.status == ConversionStatus.PAUSED) {
                                    viewModel.resumeItem(item.id)
                                } else {
                                    viewModel.pauseItem(item.id)
                                }
                            },
                            onRetry = {
                                HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                                viewModel.retryItem(item.id)
                            },
                            onCancel = {
                                HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                viewModel.deleteItem(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: ConversionEntity,
    onPauseResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.mode.name} • ${item.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(status = item.status)
            }

            // Progress bar for active processing
            if (item.status == ConversionStatus.PROCESSING || item.status == ConversionStatus.WAITING) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (item.status == ConversionStatus.WAITING) "Waiting for slot…" else "Page ${item.pagesCompleted}/${item.pageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == ConversionStatus.FAILED) {
                    FilledTonalButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (item.status != ConversionStatus.DONE && item.status != ConversionStatus.FAILED) {
                    IconButton(onClick = onPauseResume) {
                        Icon(
                            imageVector = if (item.status == ConversionStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (item.status == ConversionStatus.PAUSED) "Resume" else "Pause",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ConversionStatus) {
    val (backgroundColor, textColor, label) = when (status) {
        ConversionStatus.PENDING -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Pending")
        ConversionStatus.WAITING -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Waiting…")
        ConversionStatus.PROCESSING -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Processing")
        ConversionStatus.DONE -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Done")
        ConversionStatus.FAILED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Failed")
        ConversionStatus.PAUSED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline, "Paused")
        ConversionStatus.CANCELLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline, "Cancelled")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyQueueView(
    onNavigateToConvert: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Queue,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Conversion Queue Empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Active background and batch PDF conversions will appear here as they process. Start by dropping or selecting a PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (onNavigateToConvert != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            HapticHelper.performHaptic(view, HapticType.MEDIUM, true)
                            onNavigateToConvert()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import PDF to Convert")
                    }
                }
            }
        }
    }
}
