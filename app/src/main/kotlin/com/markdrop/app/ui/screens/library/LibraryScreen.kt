package com.markdrop.app.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as MarkdropApplication

    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(app.conversionRepository)
    )
    val completedList by viewModel.completedConversions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var activeViewerEntity by remember { mutableStateOf<ConversionEntity?>(null) }

    AnimatedContent(
        targetState = activeViewerEntity,
        transitionSpec = {
            if (targetState != null) {
                (scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))) +
                 fadeIn(animationSpec = tween(350)))
                    .togetherWith(
                        scaleOut(targetScale = 0.96f, animationSpec = tween(350)) +
                        fadeOut(animationSpec = tween(250))
                    )
            } else {
                (scaleIn(initialScale = 0.96f, animationSpec = tween(350)) +
                 fadeIn(animationSpec = tween(350)))
                    .togetherWith(
                        scaleOut(targetScale = 0.92f, animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))) +
                        fadeOut(animationSpec = tween(250))
                    )
            }
        },
        label = "LibraryContainerTransform",
        modifier = modifier.fillMaxSize()
    ) { entity ->
        if (entity != null) {
            MarkdownViewerScreen(
                conversion = entity,
                onBack = { activeViewerEntity = null }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Library", style = MaterialTheme.typography.titleLarge) }
                    )
                }
            ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                placeholder = { Text("Search conversions...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (completedList.isEmpty()) {
                EmptyLibraryView(
                    isSearching = searchQuery.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                ) {
                    items(
                        items = completedList,
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
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
                            LibraryItemCard(
                                item = item,
                                onClick = {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    activeViewerEntity = item
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun LibraryItemCard(
    item: ConversionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status badge pill
                    val statusColor = when (item.status) {
                        com.markdrop.app.data.db.ConversionStatus.DONE -> MaterialTheme.colorScheme.tertiary
                        com.markdrop.app.data.db.ConversionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    val relativeTime = formatRelativeTime(item.completedAt ?: item.createdAt)
                    Text(
                        text = "$relativeTime • ${item.pageCount} pgs • ${item.mode.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = if (isSearching) "No matching documents" else "No conversions yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isSearching) "Try a different search term" else "Converted PDFs will appear here with full Markdown viewing and export options.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
