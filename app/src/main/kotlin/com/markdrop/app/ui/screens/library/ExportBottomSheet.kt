package com.markdrop.app.ui.screens.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    fileName: String,
    markdownContent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val baseName = fileName.substringBeforeLast(".")

    // SAF Markdown file export
    val mdExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(markdownContent.toByteArray())
                }
                Toast.makeText(context, "Saved $baseName.md", Toast.LENGTH_SHORT).show()
                HapticHelper.performHaptic(view, HapticType.SUCCESS, true)
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                HapticHelper.performHaptic(view, HapticType.ERROR, true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Export & Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ExportOptionItem(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy Raw Markdown",
                subtitle = "Copy markdown text directly to system clipboard",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", markdownContent))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    onDismiss()
                }
            )

            ExportOptionItem(
                icon = Icons.Rounded.Description,
                title = "Save as Markdown (.md)",
                subtitle = "Standard Github-flavored markdown file",
                onClick = {
                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    mdExportLauncher.launch("$baseName.md")
                }
            )

            ExportOptionItem(
                icon = Icons.Rounded.Share,
                title = "Android Share Sheet",
                subtitle = "Share text to Obsidian, Notion, Drive, WhatsApp, etc.",
                onClick = {
                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        putExtra(Intent.EXTRA_TEXT, markdownContent)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Markdown"))
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ExportOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
