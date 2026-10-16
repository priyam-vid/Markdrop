package com.markdrop.app.ui.screens.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MarkdownWorkspaceMode {
    SYNTAX, PREVIEW, SPLIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerScreen(
    conversion: ConversionEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var workspaceMode by remember { mutableStateOf(MarkdownWorkspaceMode.PREVIEW) }
    var showExportBottomSheet by remember { mutableStateOf(false) }

    var markdownText by remember(conversion.markdownContent) {
        mutableStateOf(conversion.markdownContent ?: "")
    }

    if (showExportBottomSheet) {
        ExportBottomSheet(
            fileName = conversion.fileName,
            markdownContent = markdownText,
            onDismiss = { showExportBottomSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversion.fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Copy All
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", markdownText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy All")
                    }

                    // Share
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, markdownText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Markdown"))
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }

                    // Export Bottom Sheet
                    IconButton(onClick = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        showExportBottomSheet = true
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Export")
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
        ) {
            // Workspace Mode Segmented Control Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {
                    SegmentedButton(
                        selected = workspaceMode == MarkdownWorkspaceMode.SYNTAX,
                        onClick = {
                            HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                            workspaceMode = MarkdownWorkspaceMode.SYNTAX
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {
                            Icon(
                                Icons.Rounded.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Raw")
                    }

                    SegmentedButton(
                        selected = workspaceMode == MarkdownWorkspaceMode.PREVIEW,
                        onClick = {
                            HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                            workspaceMode = MarkdownWorkspaceMode.PREVIEW
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Preview")
                    }

                    SegmentedButton(
                        selected = workspaceMode == MarkdownWorkspaceMode.SPLIT,
                        onClick = {
                            HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                            workspaceMode = MarkdownWorkspaceMode.SPLIT
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {
                            Icon(
                                Icons.Rounded.VerticalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Split")
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "${conversion.pageCount} pgs • ${conversion.mode.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Workspace Content Area
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                when (workspaceMode) {
                    MarkdownWorkspaceMode.SPLIT -> {
                        SplitPaneWorkspace(
                            pdfUri = conversion.pdfUri,
                            pageCount = conversion.pageCount,
                            markdown = markdownText,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    MarkdownWorkspaceMode.PREVIEW -> {
                        RenderedMarkdownPreview(
                            markdown = markdownText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    MarkdownWorkspaceMode.SYNTAX -> {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest

                        val visualTransformation = remember(primaryColor, secondaryColor, tertiaryColor, codeBg) {
                            MarkdownSyntaxVisualTransformation(
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                tertiaryColor = tertiaryColor,
                                codeBackground = codeBg
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            BasicTextField(
                                value = markdownText,
                                onValueChange = { markdownText = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = visualTransformation,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class MarkdownSyntaxVisualTransformation(
    private val primaryColor: Color,
    private val secondaryColor: Color,
    private val tertiaryColor: Color,
    private val codeBackground: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        // Headings: #, ##, ### ...
        val headingRegex = Regex("""(?m)^(#{1,6}\s+.*)$""")
        for (match in headingRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                match.range.first, match.range.last + 1
            )
        }

        // Bold: **text**
        val boldRegex = Regex("""\*\*([^*]+)\*\*""")
        for (match in boldRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = tertiaryColor, fontWeight = FontWeight.Bold),
                match.range.first, match.range.last + 1
            )
        }

        // Italic: *text* or _text_
        val italicRegex = Regex("""(?<!\*)\*([^*]+)\*(?!\*)|_([^_]+)_""")
        for (match in italicRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = tertiaryColor, fontStyle = FontStyle.Italic),
                match.range.first, match.range.last + 1
            )
        }

        // Inline code: `code`
        val codeRegex = Regex("""`([^`]+)`""")
        for (match in codeRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(
                    color = primaryColor,
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground
                ),
                match.range.first, match.range.last + 1
            )
        }

        // Code fences: ``` ... ```
        val fenceRegex = Regex("""(?s)```.*?```""")
        for (match in fenceRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(
                    color = primaryColor,
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground
                ),
                match.range.first, match.range.last + 1
            )
        }

        // Links: [text](url)
        val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        for (match in linkRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = secondaryColor, textDecoration = TextDecoration.Underline),
                match.range.first, match.range.last + 1
            )
        }

        // Blockquotes: > quote
        val quoteRegex = Regex("""(?m)^(>.*)$""")
        for (match in quoteRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = secondaryColor.copy(alpha = 0.85f), fontStyle = FontStyle.Italic),
                match.range.first, match.range.last + 1
            )
        }

        // Table pipes: | ... |
        val tableRegex = Regex("""(?m)^(\|.*\|)$""")
        for (match in tableRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = secondaryColor, fontFamily = FontFamily.Monospace),
                match.range.first, match.range.last + 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun RenderedMarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    onHeadingClick: ((pageIndex: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lines = markdown.lines()
    var i = 0
    var currentLinePageIndex = 0

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Fenced Code Block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.appendLine(lines[i])
                    i++
                }
                if (i < lines.size && lines[i].trim().startsWith("```")) {
                    i++
                }
                val codeContent = codeBuilder.toString().trimEnd()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.ifEmpty { "code" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", codeContent))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy code",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = codeContent,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                continue
            }

            // 2. Tables
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                RenderTable(tableLines)
                continue
            }

            // 3. Headings & formatting
            when {
                trimmed.startsWith("# ") -> {
                    val headingPage = currentLinePageIndex
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .then(
                                if (onHeadingClick != null) Modifier.clickable {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    onHeadingClick(headingPage)
                                } else Modifier
                            )
                    )
                }
                trimmed.startsWith("## ") -> {
                    val headingPage = currentLinePageIndex
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 2.dp)
                            .then(
                                if (onHeadingClick != null) Modifier.clickable {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    onHeadingClick(headingPage)
                                } else Modifier
                            )
                    )
                }
                trimmed.startsWith("### ") -> {
                    val headingPage = currentLinePageIndex
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (onHeadingClick != null) Modifier.clickable {
                                    HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                                    onHeadingClick(headingPage)
                                } else Modifier
                            )
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(trimmed.drop(2), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                trimmed.startsWith("> ") -> {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = trimmed.removePrefix("> ").trim(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                trimmed == "---" -> {
                    currentLinePageIndex++
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Page ${currentLinePageIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = trimmed,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            i++
        }
    }
}

@Composable
private fun RenderTable(tableLines: List<String>) {
    if (tableLines.isEmpty()) return

    val rows = tableLines.map { line ->
        line.split("|")
            .map { it.trim() }
            .filterIndexed { index, _ -> index != 0 && index != line.split("|").lastIndex }
    }.filterNot { row ->
        row.all { cell -> cell.all { it == '-' || it == ':' } }
    }

    if (rows.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(8.dp)) {
                rows.forEachIndexed { rowIndex, cells ->
                    val isHeader = rowIndex == 0
                    Row(
                        modifier = Modifier
                            .background(
                                if (isHeader) MaterialTheme.colorScheme.surfaceContainerHighest
                                else if (rowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        cells.forEach { cell ->
                            Text(
                                text = cell,
                                style = if (isHeader) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                else MaterialTheme.typography.bodySmall,
                                color = if (isHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .widthIn(min = 80.dp, max = 240.dp)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                    if (isHeader) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

data class DocumentSection(
    val title: String,
    val pageIndex: Int,
    val level: Int = 1
)

@Composable
private fun SplitPaneWorkspace(
    pdfUri: String,
    pageCount: Int,
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var splitRatio by remember { mutableStateOf(0.48f) }

    val sections = remember(markdown) {
        val list = mutableListOf<DocumentSection>()
        val lines = markdown.lines()
        var currentPageIdx = 0
        var hasHeadingOnCurrentPage = false
        val pageCommentRegex = Regex("""<!--\s*page:\s*(\d+)\s*-->""")

        for (line in lines) {
            val trimmed = line.trim()
            val pageMatch = pageCommentRegex.find(trimmed)
            if (pageMatch != null) {
                val parsedPageNum = pageMatch.groupValues[1].toIntOrNull()
                if (parsedPageNum != null) {
                    currentPageIdx = (parsedPageNum - 1).coerceAtLeast(0)
                    hasHeadingOnCurrentPage = false
                    list.add(DocumentSection("Page $parsedPageNum", currentPageIdx, 1))
                    continue
                }
            } else if (trimmed == "---") {
                if (!hasHeadingOnCurrentPage) {
                    list.add(DocumentSection("Page ${currentPageIdx + 1}", currentPageIdx, 1))
                }
                currentPageIdx++
                hasHeadingOnCurrentPage = false
                continue
            }

            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..3) {
                    val title = trimmed.drop(level).trim()
                    if (title.isNotEmpty()) {
                        list.add(DocumentSection(title, currentPageIdx, level))
                        hasHeadingOnCurrentPage = true
                    }
                }
            }
        }
        if (list.isEmpty()) {
            list.add(DocumentSection("Page 1", 0, 1))
        }
        list
    }

    LaunchedEffect(pdfUri, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(pdfUri)
                val type = context.contentResolver.getType(uri) ?: ""
                val isImage = type.startsWith("image/") || pdfUri.endsWith(".png", true) || pdfUri.endsWith(".jpg", true) || pdfUri.endsWith(".jpeg", true)
                if (isImage) {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            pageBitmap?.recycle()
                            pageBitmap = bmp
                        }
                    }
                } else {
                    val renderer = com.markdrop.app.conversion.pdf.PdfPageRenderer(context, uri)
                    if (currentPage in 0 until renderer.pageCount) {
                        val bmp = renderer.renderPageToBitmap(currentPage, scaleFactor = 1.8f)
                        withContext(Dispatchers.Main) {
                            pageBitmap?.recycle()
                            pageBitmap = bmp
                        }
                    }
                    renderer.close()
                }
            } catch (_: Exception) {
                try {
                    val uri = android.net.Uri.parse(pdfUri)
                    val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            pageBitmap?.recycle()
                            pageBitmap = bmp
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pageBitmap?.recycle()
            pageBitmap = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Synchronized Section Header Navigation Row
        if (sections.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Jump:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
                items(sections) { section ->
                    val isSelected = section.pageIndex == currentPage
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            currentPage = section.pageIndex.coerceIn(0, maxOf(pageCount - 1, 0))
                        },
                        label = {
                            Text(
                                text = "${section.title} (P.${section.pageIndex + 1})",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (section.level == 1) Icons.Rounded.Bookmark else Icons.Rounded.SubdirectoryArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // PDF Pane
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(splitRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }

            // Navigation overlay bottom of PDF Pane
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Page", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "Page ${currentPage + 1} of ${maxOf(pageCount, 1)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage++ },
                        enabled = currentPage < pageCount - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next Page", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Draggable divider handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.y / size.height.toFloat() * 0.1f
                        splitRatio = (splitRatio + delta).coerceIn(0.2f, 0.8f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(width = 48.dp, height = 4.dp)
            ) {}
        }

        // Markdown Pane
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f - splitRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            RenderedMarkdownPreview(
                markdown = markdown,
                onHeadingClick = { pageIdx ->
                    currentPage = pageIdx.coerceIn(0, maxOf(pageCount - 1, 0))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
