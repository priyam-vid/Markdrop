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

    // SAF Standalone HTML export
    val htmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            try {
                val html = generateStandaloneHtml(baseName, markdownContent)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(html.toByteArray())
                }
                Toast.makeText(context, "Saved $baseName.html with KaTeX & CSS", Toast.LENGTH_SHORT).show()
                HapticHelper.performHaptic(view, HapticType.SUCCESS, true)
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "HTML export failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                icon = Icons.Rounded.Html,
                title = "Save as Standalone HTML",
                subtitle = "Self-contained HTML document with KaTeX LaTeX math & styling",
                onClick = {
                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    htmlExportLauncher.launch("$baseName.html")
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

/**
 * Generates an elegant standalone HTML document containing KaTeX for math rendering,
 * github-markdown-css styling, and markdown parser.
 */
private fun generateStandaloneHtml(title: String, markdown: String): String {
    val escapedMd = markdown
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - Markdrop</title>
    <!-- GitHub Markdown CSS -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.1/github-markdown.min.css">
    <!-- KaTeX Math -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
    <!-- Marked.js Markdown parser -->
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <style>
        body {
            box-sizing: border-box;
            min-width: 200px;
            max-width: 980px;
            margin: 0 auto;
            padding: 45px;
            background: #ffffff;
            color: #1f2328;
            font-family: -apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans",Helvetica,Arial,sans-serif;
        }
        @media (prefers-color-scheme: dark) {
            body {
                background: #0d1117;
                color: #e6edf3;
            }
            .markdown-body {
                color-scheme: dark;
            }
        }
        @media (max-width: 767px) {
            body {
                padding: 15px;
            }
        }
    </style>
</head>
<body>
    <article class="markdown-body" id="content"></article>
    <script>
        const rawMarkdown = `$escapedMd`;
        document.getElementById('content').innerHTML = marked.parse(rawMarkdown);
        document.addEventListener("DOMContentLoaded", function() {
            renderMathInElement(document.body, {
                delimiters: [
                    {left: '$$', right: '$$', display: true},
                    {left: '$', right: '$', display: false},
                    {left: '\\(', right: '\\)', display: false},
                    {left: '\\[', right: '\\]', display: true}
                ],
                throwOnError : false
            });
        });
    </script>
</body>
</html>
""".trimIndent()
}
