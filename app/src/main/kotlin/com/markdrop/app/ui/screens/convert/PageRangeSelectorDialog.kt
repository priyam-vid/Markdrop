package com.markdrop.app.ui.screens.convert

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.markdrop.app.conversion.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageRangeSelectorDialog(
    pdfUri: Uri,
    totalPages: Int,
    initialSelectedPages: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit
) {
    val context = LocalContext.current
    var selectedPages by remember { mutableStateOf(initialSelectedPages.ifEmpty { (0 until totalPages).toSet() }) }
    var rangeText by remember { mutableStateOf(pagesToRangeString(selectedPages)) }
    var parseError by remember { mutableStateOf(false) }

    // Cache rendered thumbnails
    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }

    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                val renderer = PdfPageRenderer(context, pdfUri)
                for (i in 0 until totalPages) {
                    val thumb = renderer.renderPageToBitmap(i, scaleFactor = 0.5f)
                    withContext(Dispatchers.Main) {
                        thumbnails[i] = thumb
                    }
                }
                renderer.close()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title & Page Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Select Pages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${selectedPages.size} of $totalPages pages selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                selectedPages = (0 until totalPages).toSet()
                                rangeText = pagesToRangeString(selectedPages)
                            }
                        ) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val all = (0 until totalPages).toSet()
                                selectedPages = all - selectedPages
                                rangeText = pagesToRangeString(selectedPages)
                            }
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "Invert Selection")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Range input text field
                OutlinedTextField(
                    value = rangeText,
                    onValueChange = { input ->
                        rangeText = input
                        val parsed = parseRangeString(input, totalPages)
                        if (parsed != null) {
                            parseError = false
                            selectedPages = parsed
                        } else {
                            parseError = true
                        }
                    },
                    label = { Text("Page Range (e.g. 1-3, 5, 8-12)") },
                    isError = parseError,
                    supportingText = if (parseError) {
                        { Text("Invalid range format", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Page Thumbnails Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items((0 until totalPages).toList()) { pageIndex ->
                        val isSelected = selectedPages.contains(pageIndex)
                        val thumb = thumbnails[pageIndex]

                        PageThumbnailItem(
                            pageIndex = pageIndex,
                            isSelected = isSelected,
                            bitmap = thumb,
                            onClick = {
                                selectedPages = if (isSelected) {
                                    selectedPages - pageIndex
                                } else {
                                    selectedPages + pageIndex
                                }
                                rangeText = pagesToRangeString(selectedPages)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedPages) },
                        enabled = selectedPages.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirm (${selectedPages.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailItem(
    pageIndex: Int,
    isSelected: Boolean,
    bitmap: Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Selection overlay scrim if not selected
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Checkbox pill top right
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                )
                .border(1.5.dp, Color.White, CircleShape)
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Page number badge bottom left
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topEnd = 8.dp),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Converts a set of 0-based page indices to a human-readable range string (e.g. "1-3, 5, 8-10").
 */
fun pagesToRangeString(pages: Set<Int>): String {
    if (pages.isEmpty()) return ""
    val sorted = pages.map { it + 1 }.sorted()
    val ranges = mutableListOf<String>()

    var start = sorted[0]
    var prev = sorted[0]

    for (i in 1 until sorted.size) {
        val curr = sorted[i]
        if (curr == prev + 1) {
            prev = curr
        } else {
            if (start == prev) ranges.add("$start")
            else ranges.add("$start-$prev")
            start = curr
            prev = curr
        }
    }
    if (start == prev) ranges.add("$start")
    else ranges.add("$start-$prev")

    return ranges.joinToString(", ")
}

/**
 * Parses a range string (e.g. "1-3, 5, 8-12") into a 0-based set of page indices.
 */
fun parseRangeString(input: String, totalPages: Int): Set<Int>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return emptySet()

    val result = mutableSetOf<Int>()
    val parts = trimmed.split(",").map { it.trim() }

    for (part in parts) {
        if (part.isEmpty()) continue
        if (part.contains("-")) {
            val rangeTokens = part.split("-").map { it.trim() }
            if (rangeTokens.size != 2) return null
            val start = rangeTokens[0].toIntOrNull() ?: return null
            val end = rangeTokens[1].toIntOrNull() ?: return null
            if (start < 1 || end < start) return null
            for (p in start..minOf(end, totalPages)) {
                result.add(p - 1)
            }
        } else {
            val single = part.toIntOrNull() ?: return null
            if (single in 1..totalPages) {
                result.add(single - 1)
            }
        }
    }
    return result
}
