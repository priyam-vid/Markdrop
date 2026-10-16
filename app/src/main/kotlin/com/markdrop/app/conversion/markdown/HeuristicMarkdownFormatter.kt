package com.markdrop.app.conversion.markdown

import android.graphics.Rect
import com.markdrop.app.conversion.ocr.OcrBlock
import com.markdrop.app.conversion.ocr.OcrElement
import com.markdrop.app.conversion.ocr.OcrLine
import com.markdrop.app.conversion.ocr.OcrPageResult
import java.util.regex.Pattern
import kotlin.math.abs

object HeuristicMarkdownFormatter {

    private val BULLET_PATTERN = Pattern.compile("^([•⁃◦▪▫–—*\\-+])\\s+(.*)", Pattern.DOTALL)
    private val NUMBERED_LIST_PATTERN = Pattern.compile("^(\\d+)[.)]\\s+(.*)", Pattern.DOTALL)

    fun formatDocument(pages: List<OcrPageResult>): String {
        if (pages.isEmpty()) return ""

        val sb = StringBuilder()
        pages.forEachIndexed { index, page ->
            if (index > 0) {
                sb.append("\n\n---\n\n")
            }
            sb.append(formatPage(page))
        }
        return sb.toString().trim()
    }

    fun formatPage(page: OcrPageResult): String {
        if (page.blocks.isEmpty()) return ""

        // 1. Calculate median line height for the page to establish baseline body text scale
        val allLineHeights = page.blocks.flatMap { it.lines }.map { it.lineHeight }.filter { it > 0 }.sorted()
        val medianHeight = if (allLineHeights.isNotEmpty()) {
            allLineHeights[allLineHeights.size / 2].toDouble()
        } else {
            20.0
        }

        // 2. Sort blocks into natural reading order (handling multi-column layouts if present)
        val sortedBlocks = sortBlocksInReadingOrder(page.blocks, page.width, medianHeight)

        val pageBuilder = StringBuilder()

        for (block in sortedBlocks) {
            val formattedBlock = formatBlock(block, medianHeight)
            if (formattedBlock.isNotBlank()) {
                if (pageBuilder.isNotEmpty()) {
                    pageBuilder.append("\n\n")
                }
                pageBuilder.append(formattedBlock)
            }
        }

        return pageBuilder.toString()
    }

    /**
     * Orders blocks logically:
     * - Detects 2-column layouts if blocks cleanly separate into left and right halves.
     * - Otherwise sorts primarily top-to-bottom with a line-height tolerance band to group horizontal elements.
     */
    private fun sortBlocksInReadingOrder(
        blocks: List<OcrBlock>,
        pageWidth: Int,
        medianHeight: Double
    ): List<OcrBlock> {
        if (blocks.size <= 3 || pageWidth <= 0) {
            return blocks.sortedWith(
                compareBy<OcrBlock> { it.boundingBox?.top ?: 0 }
                    .thenBy { it.boundingBox?.left ?: 0 }
            )
        }

        val midpoint = pageWidth / 2
        val leftColumnBlocks = mutableListOf<OcrBlock>()
        val rightColumnBlocks = mutableListOf<OcrBlock>()
        val spanningBlocks = mutableListOf<OcrBlock>()

        for (b in blocks) {
            val box = b.boundingBox
            if (box == null) {
                spanningBlocks.add(b)
                continue
            }
            val spansCenter = box.left < (midpoint - pageWidth * 0.08) && box.right > (midpoint + pageWidth * 0.08)
            when {
                spansCenter -> spanningBlocks.add(b)
                box.right <= midpoint + pageWidth * 0.05 -> leftColumnBlocks.add(b)
                box.left >= midpoint - pageWidth * 0.05 -> rightColumnBlocks.add(b)
                else -> spanningBlocks.add(b)
            }
        }

        // Only use 2-column ordering if both columns have at least 2 distinct blocks
        // and together make up most of the page content
        val isTwoColumn = leftColumnBlocks.size >= 2 && rightColumnBlocks.size >= 2 &&
                (leftColumnBlocks.size + rightColumnBlocks.size) >= (blocks.size * 0.6)

        val bandTolerance = (medianHeight * 0.75).toInt().coerceAtLeast(8)
        val comparator = ReadingOrderComparator(bandTolerance)

        if (!isTwoColumn) {
            return blocks.sortedWith(comparator)
        }

        // Two-column layout: Spanning headers first, then left col top-to-bottom, right col top-to-bottom, spanning footers
        val topSpanning = spanningBlocks.filter {
            val top = it.boundingBox?.top ?: 0
            val minColTop = minOf(
                leftColumnBlocks.minOfOrNull { b -> b.boundingBox?.top ?: 0 } ?: Int.MAX_VALUE,
                rightColumnBlocks.minOfOrNull { b -> b.boundingBox?.top ?: 0 } ?: Int.MAX_VALUE
            )
            top < minColTop
        }.sortedWith(comparator)

        val bottomSpanning = spanningBlocks.filter { it !in topSpanning }.sortedWith(comparator)
        val sortedLeft = leftColumnBlocks.sortedWith(comparator)
        val sortedRight = rightColumnBlocks.sortedWith(comparator)

        return topSpanning + sortedLeft + sortedRight + bottomSpanning
    }

    private fun formatBlock(block: OcrBlock, medianHeight: Double): String {
        val lines = block.lines
        if (lines.isEmpty()) return block.text.trim()

        // Detect if this block looks like a Markdown table
        val tableMarkdown = tryFormatAsTable(lines)
        if (tableMarkdown != null) {
            return tableMarkdown
        }

        val avgLineHeight = lines.map { it.lineHeight }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: medianHeight
        val heightRatio = avgLineHeight / medianHeight

        // Heading detection: font scale ratio + short length + does not end with sentence period
        val isSingleOrTwoLines = lines.size <= 2
        val rawTrimmed = block.text.replace("\n", " ").trim()
        val doesNotEndWithPeriod = !rawTrimmed.endsWith(".") || rawTrimmed.matches(Regex("""^\d+(\.\d+)*\.?$"""))

        if (isSingleOrTwoLines && doesNotEndWithPeriod && rawTrimmed.length < 120) {
            if (heightRatio >= 1.6) {
                return "# $rawTrimmed"
            } else if (heightRatio >= 1.3) {
                return "## $rawTrimmed"
            } else if (heightRatio >= 1.15 && rawTrimmed.length < 70) {
                return "### $rawTrimmed"
            }
        }

        // List detection and paragraph assembly
        val blockLines = mutableListOf<String>()
        var inList = false

        for (line in lines) {
            val rawLine = line.text.trim()
            if (rawLine.isEmpty()) continue

            val bulletMatcher = BULLET_PATTERN.matcher(rawLine)
            val numberedMatcher = NUMBERED_LIST_PATTERN.matcher(rawLine)

            when {
                bulletMatcher.matches() -> {
                    val content = bulletMatcher.group(2)
                    blockLines.add("- $content")
                    inList = true
                }
                numberedMatcher.matches() -> {
                    val num = numberedMatcher.group(1)
                    val content = numberedMatcher.group(2)
                    blockLines.add("$num. $content")
                    inList = true
                }
                inList -> {
                    // Continuation of previous list item
                    val lastIndex = blockLines.lastIndex
                    if (lastIndex >= 0) {
                        blockLines[lastIndex] = blockLines[lastIndex] + " " + rawLine
                    } else {
                        blockLines.add(rawLine)
                    }
                }
                else -> {
                    blockLines.add(rawLine)
                }
            }
        }

        if (inList) {
            return blockLines.joinToString("\n")
        }

        // Reconstruct coherent paragraph with dehyphenation
        val paragraph = StringBuilder()
        for (line in lines) {
            val rawLine = line.text.trim()
            if (rawLine.isEmpty()) continue

            if (paragraph.isEmpty()) {
                paragraph.append(rawLine)
            } else {
                // If previous line ended with a trailing hyphen attached to a word, merge (e.g. "differ-" + "ent" -> "different")
                if (paragraph.endsWith("-") && rawLine.firstOrNull()?.isLetter() == true) {
                    paragraph.setLength(paragraph.length - 1)
                    paragraph.append(rawLine)
                } else {
                    paragraph.append(" ").append(rawLine)
                }
            }
        }

        return paragraph.toString()
    }

    /**
     * Strict table detection:
     * Only formats as a table if:
     * 1. Lines have explicit delimiters (pipes or tabs), OR
     * 2. Multiple rows (>= 3) have distinct column clusters that align vertically within a tight X-coordinate threshold.
     * Regular paragraphs must NEVER be detected as tables.
     */
    private fun tryFormatAsTable(lines: List<OcrLine>): String? {
        if (lines.size < 2) return null

        // Case A: Explicit pipe or tab delimited lines
        val hasExplicitDelimiters = lines.count { it.text.contains("|") || it.text.contains("\t") } >= (lines.size * 0.7)
        if (hasExplicitDelimiters) {
            return formatDelimitedTable(lines)
        }

        // Case B: Spatial multi-column table detection
        // A genuine spatial table requires at least 3 rows (header + 2 data rows)
        if (lines.size < 3) return null

        // In ML Kit, elements are individual words. In a table, each row has multiple cells separated by large horizontal gaps.
        // Cluster word elements within each line into table cells based on inter-word gap spacing.
        val rowCells = lines.map { clusterLineIntoCells(it) }

        // All rows must have the same number of columns (between 2 and 7 columns)
        val colCounts = rowCells.map { it.size }
        val dominantColCount = colCounts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        if (dominantColCount < 2 || dominantColCount > 7) return null

        // At least 80% of rows must match dominantColCount
        val matchingRows = rowCells.filter { it.size == dominantColCount }
        if (matchingRows.size < lines.size * 0.8 || matchingRows.size < 3) return null

        // Check vertical column alignment across rows:
        // Column start X coordinates must be consistent across matching rows within tolerance
        val avgColStarts = DoubleArray(dominantColCount)
        for (col in 0 until dominantColCount) {
            avgColStarts[col] = matchingRows.map { it[col].first }.average()
        }

        val alignmentTolerance = 35.0 // pixels
        val isAligned = matchingRows.all { row ->
            row.indices.all { col ->
                abs(row[col].first - avgColStarts[col]) <= alignmentTolerance
            }
        }

        if (!isAligned) return null

        // Build markdown table
        val sb = StringBuilder()
        val header = matchingRows.first()
        sb.append("| ").append(header.joinToString(" | ") { it.second.trim() }).append(" |\n")
        sb.append("| ").append(List(dominantColCount) { "---" }.joinToString(" | ")).append(" |\n")

        for (i in 1 until matchingRows.size) {
            val row = matchingRows[i]
            sb.append("| ").append(row.joinToString(" | ") { it.second.trim() }).append(" |\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Groups word elements within an OCR line into table cells based on large horizontal spacing.
     * In normal text, gaps between words are small (~0.2-0.5x line height).
     * In tables, column gaps are much wider (~1.5x+ line height).
     */
    private fun clusterLineIntoCells(line: OcrLine): List<Pair<Int, String>> {
        if (line.elements.isEmpty()) return emptyList()

        val sortedElements = line.elements.filter { it.text.isNotBlank() }
            .sortedBy { it.boundingBox?.left ?: 0 }

        if (sortedElements.isEmpty()) return emptyList()

        val avgHeight = line.lineHeight.takeIf { it > 0 } ?: 20
        val columnGapThreshold = avgHeight * 1.2

        val cells = mutableListOf<Pair<Int, String>>()
        var currentCellStartX = sortedElements[0].boundingBox?.left ?: 0
        var currentCellText = StringBuilder(sortedElements[0].text.trim())
        var lastRight = sortedElements[0].boundingBox?.right ?: 0

        for (i in 1 until sortedElements.size) {
            val el = sortedElements[i]
            val left = el.boundingBox?.left ?: 0
            val right = el.boundingBox?.right ?: left
            val gap = left - lastRight

            if (gap > columnGapThreshold) {
                // New table column cell
                cells.add(Pair(currentCellStartX, currentCellText.toString()))
                currentCellStartX = left
                currentCellText = StringBuilder(el.text.trim())
            } else {
                // Continuation of current cell
                currentCellText.append(" ").append(el.text.trim())
            }
            lastRight = right
        }

        cells.add(Pair(currentCellStartX, currentCellText.toString()))
        return cells
    }

    private fun formatDelimitedTable(lines: List<OcrLine>): String? {
        val rows = lines.map { line ->
            if (line.text.contains("|")) {
                line.text.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                line.text.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }.filter { it.size >= 2 }

        if (rows.size < 2) return null
        val colCount = rows.map { it.size }.maxOrNull() ?: return null

        val sb = StringBuilder()
        val header = padCells(rows.first(), colCount)
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(colCount) { "---" }.joinToString(" | ")).append(" |\n")

        for (i in 1 until rows.size) {
            val row = padCells(rows[i], colCount)
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }

        return sb.toString().trimEnd()
    }

    private fun padCells(cells: List<String>, targetCount: Int): List<String> {
        val mutable = cells.toMutableList()
        while (mutable.size < targetCount) {
            mutable.add("")
        }
        return if (mutable.size > targetCount) mutable.subList(0, targetCount) else mutable
    }
}

