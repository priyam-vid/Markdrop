package com.markdrop.app.conversion.layout

import com.markdrop.app.conversion.ocr.OcrPageResult
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic Layout-to-Markdown converter.
 * Pure Kotlin, zero Android framework dependencies.
 */
object LayoutToMarkdown {

    private val EXTENDED_BULLET_REGEX = Regex("""^([•⁃◦▪▫–—*+\-o●·°]|©)\s+(.*)""", RegexOption.DOT_MATCHES_ALL)
    private val NUMBERED_LIST_REGEX = Regex("""^(\d{1,4}[.)]|\(\d{1,4}\)|\[\d{1,4}\])\s+(.*)""", RegexOption.DOT_MATCHES_ALL)

    fun formatDocument(pages: List<LayoutPage>): String {
        if (pages.isEmpty()) return ""
        return pages.joinToString("\n\n---\n\n") { formatPage(it) }.trim()
    }

    fun formatPage(page: LayoutPage): String {
        val lines = page.lines.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return ""

        val bodyModeSize = computeBodyModeSize(lines)
        val sortedLines = sortLinesInReadingOrder(lines, page.width, bodyModeSize)
        val blocks = groupLinesIntoBlocks(sortedLines, bodyModeSize)

        val sb = StringBuilder()

        if (page.averageConfidence < 0.65f) {
            sb.append("> [!WARNING] Low OCR confidence detected on this page (average ${((page.averageConfidence * 100).roundToInt())}%). Scanned handwriting or low-contrast text may have approximate transcription.\n\n")
        }

        blocks.forEachIndexed { index, block ->
            val formatted = formatBlock(block, bodyModeSize)
            if (formatted.isNotBlank()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                    sb.append("\n\n")
                }
                sb.append(formatted)
            }
        }

        return sb.toString().trim()
    }

    /**
     * Maps an ML Kit OcrPageResult into pure Kotlin LayoutPage.
     */
    fun fromOcrPageResult(ocr: OcrPageResult): LayoutPage {
        val layoutLines = mutableListOf<LayoutLine>()
        var totalConfidence = 0.0
        var confidenceCount = 0

        for (block in ocr.blocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                val left = box?.left?.toFloat() ?: 0f
                val right = box?.right?.toFloat() ?: 0f
                val top = box?.top?.toFloat() ?: 0f
                val bottom = box?.bottom?.toFloat() ?: 0f
                val height = if (line.lineHeight > 0) line.lineHeight.toFloat() else (bottom - top).coerceAtLeast(16f)

                val elements = line.elements.map { el ->
                    val elBox = el.boundingBox
                    if (el.confidence != null && el.confidence > 0f) {
                        totalConfidence += el.confidence
                        confidenceCount++
                    }
                    LayoutElement(
                        text = el.text,
                        left = elBox?.left?.toFloat() ?: 0f,
                        right = elBox?.right?.toFloat() ?: 0f,
                        confidence = el.confidence
                    )
                }

                layoutLines.add(
                    LayoutLine(
                        text = line.text,
                        y = top,
                        height = height,
                        fontSize = height,
                        left = left,
                        right = right,
                        elements = elements
                    )
                )
            }
        }

        val avgConfidence = if (confidenceCount > 0) (totalConfidence / confidenceCount).toFloat() else 1.0f

        return LayoutPage(
            pageIndex = ocr.pageIndex,
            width = ocr.width.toFloat(),
            height = ocr.height.toFloat(),
            lines = layoutLines,
            averageConfidence = avgConfidence
        )
    }

    fun formatOcr(ocr: OcrPageResult): String {
        return formatPage(fromOcrPageResult(ocr))
    }

    /**
     * Computes the statistical mode (most frequent size) of body text.
     * Rounds sizes to 1.0 pt buckets to cluster font rendering variations.
     */
    fun computeBodyModeSize(lines: List<LayoutLine>): Float {
        val sizes = lines.map { it.fontSize }.filter { it > 0f }
        if (sizes.isEmpty()) return 16f

        val buckets = sizes.groupingBy { (it * 2).roundToInt() / 2f }.eachCount()
        val mode = buckets.maxByOrNull { it.value }?.key
        return mode ?: 16f
    }

    /**
     * Sorts lines into natural reading order using stable 2D row-clustering.
     */
    private fun sortLinesInReadingOrder(
        lines: List<LayoutLine>,
        pageWidth: Float,
        bodySize: Float
    ): List<LayoutLine> {
        if (lines.size <= 2) return lines.sortedBy { it.y }

        val bandTolerance = (bodySize * 0.6f).coerceAtLeast(6f)

        // Check for 2-column layout if page width is positive
        if (pageWidth > 200f) {
            val midpoint = pageWidth / 2f
            val leftCol = mutableListOf<LayoutLine>()
            val rightCol = mutableListOf<LayoutLine>()
            val spanning = mutableListOf<LayoutLine>()

            for (l in lines) {
                when {
                    l.left < (midpoint - pageWidth * 0.08f) && l.right > (midpoint + pageWidth * 0.08f) -> spanning.add(l)
                    l.right <= midpoint + pageWidth * 0.05f -> leftCol.add(l)
                    l.left >= midpoint - pageWidth * 0.05f -> rightCol.add(l)
                    else -> spanning.add(l)
                }
            }

            val isTwoColumn = leftCol.size >= 3 && rightCol.size >= 3 &&
                    (leftCol.size + rightCol.size) >= (lines.size * 0.65f)

            if (isTwoColumn) {
                val minColTop = minOf(
                    leftCol.minOfOrNull { it.y } ?: Float.MAX_VALUE,
                    rightCol.minOfOrNull { it.y } ?: Float.MAX_VALUE
                )
                val topSpanning = spanning.filter { it.y < minColTop }
                val bottomSpanning = spanning.filter { it !in topSpanning }

                return clusterSort(topSpanning, bandTolerance) +
                        clusterSort(leftCol, bandTolerance) +
                        clusterSort(rightCol, bandTolerance) +
                        clusterSort(bottomSpanning, bandTolerance)
            }
        }

        return clusterSort(lines, bandTolerance)
    }

    private fun clusterSort(lines: List<LayoutLine>, bandTolerance: Float): List<LayoutLine> {
        if (lines.size <= 1) return lines
        val sortedByY = lines.sortedWith(compareBy<LayoutLine> { it.y }.thenBy { it.left })

        class RowBand(var yMin: Float, var yMax: Float) {
            val items = mutableListOf<LayoutLine>()
        }

        val rows = mutableListOf<RowBand>()
        for (l in sortedByY) {
            val row = rows.find { abs(l.y - it.yMin) <= bandTolerance || (l.y >= it.yMin && l.y <= it.yMax) }
            if (row != null) {
                row.items.add(l)
                row.yMin = minOf(row.yMin, l.y)
                row.yMax = maxOf(row.yMax, l.y + l.height)
            } else {
                val newRow = RowBand(l.y, l.y + l.height)
                newRow.items.add(l)
                rows.add(newRow)
            }
        }

        rows.sortBy { it.yMin }
        return rows.flatMap { it.items.sortedBy { line -> line.left } }
    }

    /**
     * Groups consecutive lines into paragraphs, blocks, or tables.
     */
    private fun groupLinesIntoBlocks(lines: List<LayoutLine>, bodySize: Float): List<List<LayoutLine>> {
        val blocks = mutableListOf<MutableList<LayoutLine>>()
        var currentBlock = mutableListOf<LayoutLine>()

        for (line in lines) {
            if (currentBlock.isEmpty()) {
                currentBlock.add(line)
            } else {
                val prev = currentBlock.last()
                val gap = line.y - (prev.y + prev.height)
                val isNewParagraphGap = gap > (bodySize * 0.7f)
                val isHeading = isHeadingLine(line, bodySize)
                val prevIsHeading = isHeadingLine(prev, bodySize)

                if (isNewParagraphGap || isHeading || prevIsHeading) {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf(line)
                } else {
                    currentBlock.add(line)
                }
            }
        }

        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock)
        }

        return blocks
    }

    private fun isHeadingLine(line: LayoutLine, bodySize: Float): Boolean {
        val ratio = line.fontSize / bodySize
        val raw = line.text.trim()
        val isShort = raw.length < 120
        val doesNotEndWithPeriod = !raw.endsWith(".") || raw.matches(Regex("""^\d+(\.\d+)*\.?$"""))
        return isShort && doesNotEndWithPeriod && (ratio >= 1.15f || (line.isBold && raw.length < 70))
    }

    private fun formatBlock(lines: List<LayoutLine>, bodySize: Float): String {
        if (lines.isEmpty()) return ""

        // Check if block forms a table
        val tableMd = tryFormatTable(lines)
        if (tableMd != null) {
            return tableMd
        }

        // Check for Heading
        if (lines.size <= 2) {
            val first = lines.first()
            val ratio = first.fontSize / bodySize
            val raw = lines.joinToString(" ") { it.text.trim() }

            if (isHeadingLine(first, bodySize)) {
                return when {
                    ratio >= 1.5f -> "# $raw"
                    ratio >= 1.3f -> "## $raw"
                    ratio >= 1.15f -> "### $raw"
                    first.isBold -> "### $raw"
                    else -> raw
                }
            }
        }

        // Check for Lists
        val listItems = mutableListOf<String>()
        var inList = false

        for (line in lines) {
            val raw = line.text.trim()
            val bulletMatch = EXTENDED_BULLET_REGEX.matchEntire(raw)
            val numberedMatch = NUMBERED_LIST_REGEX.matchEntire(raw)

            when {
                bulletMatch != null -> {
                    val content = formatInline(bulletMatch.groupValues[2], line.isBold, line.isItalic)
                    listItems.add("- $content")
                    inList = true
                }
                numberedMatch != null -> {
                    val num = numberedMatch.groupValues[1]
                    val content = formatInline(numberedMatch.groupValues[2], line.isBold, line.isItalic)
                    val prefix = if (num.endsWith(".") || num.endsWith(")")) num else "$num."
                    listItems.add("$prefix $content")
                    inList = true
                }
                inList -> {
                    // Continuation of list item only if indented or short
                    val lastIdx = listItems.lastIndex
                    if (lastIdx >= 0 && (line.left > lines.first().left + 10f || raw.length < 80)) {
                        listItems[lastIdx] = listItems[lastIdx] + " " + formatInline(raw, line.isBold, line.isItalic)
                    } else {
                        // End list, start regular line
                        inList = false
                        listItems.add(formatInline(raw, line.isBold, line.isItalic))
                    }
                }
                else -> {
                    listItems.add(formatInline(raw, line.isBold, line.isItalic))
                }
            }
        }

        if (inList || listItems.any { it.startsWith("- ") || it.matches(Regex("""^\d+.*\s.*""")) }) {
            return listItems.joinToString("\n")
        }

        // Reconstruct coherent paragraph with dehyphenation
        val paragraph = StringBuilder()
        for (line in lines) {
            val raw = line.text.trim()
            if (raw.isEmpty()) continue

            val formattedLine = formatInline(raw, line.isBold, line.isItalic)

            if (paragraph.isEmpty()) {
                paragraph.append(formattedLine)
            } else {
                // If previous line ended with trailing '-' and next starts with lowercase letter, merge!
                if (paragraph.endsWith("-") && raw.firstOrNull()?.isLowerCase() == true) {
                    paragraph.setLength(paragraph.length - 1)
                    paragraph.append(formattedLine)
                } else {
                    paragraph.append(" ").append(formattedLine)
                }
            }
        }

        return paragraph.toString()
    }

    private fun formatInline(text: String, isBold: Boolean, isItalic: Boolean): String {
        var result = text.trim()
        if (isBold && !result.startsWith("**") && !result.endsWith("**")) {
            result = "**$result**"
        }
        if (isItalic && !result.startsWith("*") && !result.endsWith("*")) {
            result = "*$result*"
        }
        return result
    }

    private fun tryFormatTable(lines: List<LayoutLine>): String? {
        if (lines.size < 2) return null

        // 1. Explicit pipe or tab table
        val delimitedLines = lines.filter { it.text.contains("|") || it.text.contains("\t") }
        if (delimitedLines.size >= (lines.size * 0.7f).coerceAtLeast(2f)) {
            val rows = lines.map { l ->
                if (l.text.contains("|")) {
                    l.text.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    l.text.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
                }
            }.filter { it.size >= 2 }

            if (rows.size >= 2) {
                val maxCols = rows.maxOf { it.size }
                val sb = StringBuilder()
                val header = padCells(rows.first(), maxCols)
                sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
                sb.append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")
                for (i in 1 until rows.size) {
                    val row = padCells(rows[i], maxCols)
                    sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                }
                return sb.toString().trimEnd()
            }
        }

        // 2. Spatial alignment table (multi-column elements within lines)
        val linesWithMultipleElements = lines.filter { it.elements.size >= 2 }
        if (linesWithMultipleElements.size < 3 || linesWithMultipleElements.size < (lines.size * 0.75f)) {
            return null
        }

        val colCount = linesWithMultipleElements.map { it.elements.size }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        if (colCount < 2 || colCount > 8) return null

        val matchingRows = linesWithMultipleElements.filter { it.elements.size == colCount }
        if (matchingRows.size < 3) return null

        val sb = StringBuilder()
        val header = matchingRows.first().elements.map { it.text.trim() }
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(colCount) { "---" }.joinToString(" | ")).append(" |\n")

        for (i in 1 until matchingRows.size) {
            val row = matchingRows[i].elements.map { it.text.trim() }
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }

        return sb.toString().trimEnd()
    }

    private fun padCells(cells: List<String>, count: Int): List<String> {
        val list = cells.toMutableList()
        while (list.size < count) list.add("")
        return if (list.size > count) list.subList(0, count) else list
    }
}
