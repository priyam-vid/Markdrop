package com.markdrop.app.conversion.layout

import com.markdrop.app.conversion.ocr.OcrPageResult
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic Layout-to-Markdown converter.
 * Pure Kotlin, zero Android framework dependencies.
 * Uses LayoutWord as the atomic unit for styles, tables, and spatial reading order.
 */
object LayoutToMarkdown {

    private val EXTENDED_BULLET_REGEX = Regex("""^([•⁃◦▪▫–—*+\-o●·°]|©)\s+(.*)""", RegexOption.DOT_MATCHES_ALL)
    private val NUMBERED_LIST_REGEX = Regex("""^(\d{1,4}[.)]|\(\d{1,4}\)|\[\d{1,4}\])\s+(.*)""", RegexOption.DOT_MATCHES_ALL)

    fun formatDocument(pages: List<LayoutPage>): String {
        if (pages.isEmpty()) return ""
        return pages.joinToString("\n\n---\n\n") { formatPage(it) }.trim()
    }

    fun formatPage(page: LayoutPage): String {
        val lines = if (page.words.isNotEmpty()) {
            LineClusterer.cluster(page.words)
        } else {
            page.lines
        }.filter { it.text.isNotBlank() }

        if (lines.isEmpty()) {
            return if (page.figures.isNotEmpty()) {
                "> **Figure:** (image on this page — not transcribed in Offline mode)"
            } else ""
        }

        val bodyModeSize = computeBodyModeSize(lines)
        val blocks = extractBlocksWithTables(lines, page.width.coerceAtLeast(300f), page.height.coerceAtLeast(400f))

        val sb = StringBuilder()

        if (page.averageConfidence < 0.65f) {
            sb.append("> [!WARNING] Low OCR confidence detected on this page (average ${((page.averageConfidence * 100).roundToInt())}%). Scanned handwriting or low-contrast text may have approximate transcription.\n\n")
        }

        val placedFigures = mutableSetOf<FigureBox>()

        // 1. Emit figures at top if above first block
        for (fig in page.figures) {
            if (blocks.isEmpty() || fig.bottom <= blocks.first().top) {
                if (placedFigures.add(fig)) {
                    sb.append("> **Figure:** (image on this page — not transcribed in Offline mode)\n\n")
                }
            }
        }

        blocks.forEachIndexed { index, block ->
            for (fig in page.figures) {
                if (!placedFigures.contains(fig) && fig.bottom <= block.top + 10f) {
                    if (placedFigures.add(fig)) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
                        sb.append("> **Figure:** (image on this page — not transcribed in Offline mode)\n\n")
                    }
                }
            }

            val formatted = formatBlock(block.lines, bodyModeSize)
            if (formatted.isNotBlank()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                    sb.append("\n\n")
                }
                sb.append(formatted)

                // Check for figures embedded between this block and next block
                val nextBlockTop = blocks.getOrNull(index + 1)?.top ?: Float.MAX_VALUE
                for (fig in page.figures) {
                    if (!placedFigures.contains(fig) && fig.top >= block.bottom - 20f && fig.bottom <= nextBlockTop + 20f) {
                        if (placedFigures.add(fig)) {
                            sb.append("\n\n> **Figure:** (image on this page — not transcribed in Offline mode)\n\n")
                        }
                    }
                }
            }
        }

        // Remaining unplaced figures at bottom
        for (fig in page.figures) {
            if (placedFigures.add(fig)) {
                if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
                sb.append("> **Figure:** (image on this page — not transcribed in Offline mode)\n\n")
            }
        }

        return sb.toString().trim()
    }

    private fun extractBlocksWithTables(lines: List<LayoutLine>, pageWidth: Float, pageHeight: Float): List<LayoutBlock> {
        val table = TableDetector.findTable(lines) ?: return XYCut.segment(lines, pageWidth, pageHeight)

        val result = mutableListOf<LayoutBlock>()
        if (table.startIndex > 0) {
            val beforeLines = lines.subList(0, table.startIndex)
            result.addAll(extractBlocksWithTables(beforeLines, pageWidth, pageHeight))
        }
        val tableLines = lines.subList(table.startIndex, table.endIndex)
        result.add(LayoutBlock(tableLines))
        if (table.endIndex < lines.size) {
            val afterLines = lines.subList(table.endIndex, lines.size)
            result.addAll(extractBlocksWithTables(afterLines, pageWidth, pageHeight))
        }
        return result
    }

    /**
     * Maps an ML Kit OcrPageResult into pure Kotlin LayoutPage.
     */
    fun fromOcrPageResult(ocr: OcrPageResult): LayoutPage {
        val layoutWords = mutableListOf<LayoutWord>()
        var totalConfidence = 0.0
        var confidenceCount = 0

        for (block in ocr.blocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                val top = box?.top?.toFloat() ?: 0f
                val bottom = box?.bottom?.toFloat() ?: (top + 16f)
                val height = (bottom - top).coerceAtLeast(14f)

                for (el in line.elements) {
                    val elBox = el.boundingBox
                    val elLeft = elBox?.left?.toFloat() ?: 0f
                    val elRight = elBox?.right?.toFloat() ?: (elLeft + 20f)
                    val elTop = elBox?.top?.toFloat() ?: top
                    val elBottom = elBox?.bottom?.toFloat() ?: bottom
                    val conf = el.confidence
                    if (conf != null && conf > 0f) {
                        totalConfidence += conf
                        confidenceCount++
                    }
                    layoutWords.add(
                        LayoutWord(
                            text = el.text,
                            left = elLeft,
                            top = elTop,
                            right = elRight,
                            bottom = elBottom,
                            fontSize = height,
                            confidence = conf
                        )
                    )
                }
            }
        }

        val avgConfidence = if (confidenceCount > 0) (totalConfidence / confidenceCount).toFloat() else 1.0f
        val lines = LineClusterer.cluster(layoutWords)

        return LayoutPage(
            pageIndex = ocr.pageIndex,
            width = ocr.width.toFloat(),
            height = ocr.height.toFloat(),
            words = layoutWords,
            lines = lines,
            averageConfidence = avgConfidence
        )
    }

    fun formatOcr(ocr: OcrPageResult): String {
        return formatPage(fromOcrPageResult(ocr))
    }

    /**
     * Computes the statistical mode (most frequent size) of body text,
     * weighted by character count.
     */
    fun computeBodyModeSize(lines: List<LayoutLine>): Float {
        val sizes = lines.flatMap { line ->
            line.words.map { Pair(it.fontSize, it.text.length) }
        }.filter { it.first > 0f }

        if (sizes.isEmpty()) return 12f

        val buckets = mutableMapOf<Float, Int>()
        for ((size, count) in sizes) {
            val rounded = (size * 2).roundToInt() / 2f
            buckets[rounded] = (buckets[rounded] ?: 0) + count
        }

        return buckets.maxByOrNull { it.value }?.key ?: 12f
    }

    private fun isHeadingLine(line: LayoutLine, bodySize: Float): Boolean {
        val ratio = line.fontSize / bodySize
        val raw = line.text.trim()
        val isShort = raw.length < 120
        val doesNotEndWithPeriod = !raw.endsWith(".") || raw.matches(Regex("""^\d+(\.\d+)*\.?$"""))
        val isNumberedHeading = raw.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)\s+[A-Z].*"""))

        return isShort && doesNotEndWithPeriod && (ratio >= 1.15f || isNumberedHeading || (line.isBold && raw.length < 70))
    }

    private fun hasLargeInternalGap(line: LayoutLine): Boolean {
        if (line.words.size < 2) return false
        val sortedWords = line.words.sortedBy { it.left }
        for (i in 1 until sortedWords.size) {
            val gap = sortedWords[i].left - sortedWords[i - 1].right
            if (gap >= (sortedWords[i].fontSize * 2.5f).coerceAtLeast(25f)) {
                return true
            }
        }
        return false
    }

    private fun formatBlock(lines: List<LayoutLine>, bodySize: Float): String {
        if (lines.isEmpty()) return ""

        val first = lines.first()
        if (isHeadingLine(first, bodySize)) {
            val ratio = first.fontSize / bodySize
            if (lines.size == 1 || (lines.size == 2 && isHeadingLine(lines[1], bodySize))) {
                val raw = lines.joinToString(" ") { it.text.trim() }
                return when {
                    ratio >= 1.5f -> "# $raw"
                    ratio >= 1.3f -> "## $raw"
                    ratio >= 1.15f -> "## $raw"
                    first.isBold -> "### $raw"
                    else -> "## $raw"
                }
            } else {
                val headingRaw = first.text.trim()
                val headingMd = when {
                    ratio >= 1.5f -> "# $headingRaw"
                    ratio >= 1.3f -> "## $headingRaw"
                    ratio >= 1.15f -> "## $headingRaw"
                    first.isBold -> "### $headingRaw"
                    else -> "## $headingRaw"
                }
                val rest = formatBlock(lines.drop(1), bodySize)
                return if (rest.isNotBlank()) "$headingMd\n\n$rest" else headingMd
            }
        }

        // Check for Tables
        val table = TableDetector.findTable(lines)
        if (table != null) {
            val sb = StringBuilder()
            if (table.startIndex > 0) {
                val before = formatBlock(lines.subList(0, table.startIndex), bodySize)
                if (before.isNotBlank()) sb.append(before).append("\n\n")
            }
            sb.append(table.markdown)
            if (table.endIndex < lines.size) {
                val after = formatBlock(lines.subList(table.endIndex, lines.size), bodySize)
                if (after.isNotBlank()) sb.append("\n\n").append(after)
            }
            return sb.toString().trim()
        }

        // Check for Lists
        if (ListDetector.isListCandidate(lines)) {
            return ListDetector.formatList(lines) { formatWordsInline(it) }
        }

        // Reconstruct coherent paragraph with word-level styles and dehyphenation
        val paragraph = StringBuilder()
        for (i in lines.indices) {
            val line = lines[i]
            val lineFormatted = formatWordsInline(line.words)
            if (lineFormatted.isEmpty()) continue

            if (paragraph.isEmpty()) {
                paragraph.append(lineFormatted)
            } else {
                val prevLine = lines[i - 1]
                val prevRaw = prevLine.text.trim()
                val isHardBreak = prevRaw.contains("...") ||
                        prevRaw.contains("···") ||
                        prevRaw.contains("___") ||
                        prevRaw.endsWith(":") ||
                        (hasLargeInternalGap(prevLine) && hasLargeInternalGap(line))

                if (isHardBreak) {
                    paragraph.append("\n").append(lineFormatted)
                } else if (paragraph.endsWith("-") && lineFormatted.firstOrNull()?.isLowerCase() == true) {
                    paragraph.setLength(paragraph.length - 1)
                    paragraph.append(lineFormatted)
                } else {
                    paragraph.append(" ").append(lineFormatted)
                }
            }
        }

        return paragraph.toString()
    }

    /**
     * Formats a list of LayoutWords with bold and italic runs.
     * Merges consecutive styled words (**a** **b** -> **a b**).
     * If > 80% of words are bold, leaves them unstyled to prevent ugly line-wide bolding.
     */
    fun formatWordsInline(words: List<LayoutWord>): String {
        if (words.isEmpty()) return ""

        val boldCount = words.count { it.bold }
        val mostlyBold = boldCount >= (words.size * 0.8) && words.size >= 5

        val sb = StringBuilder()
        var i = 0

        while (i < words.size) {
            val w = words[i]
            val applyBold = w.bold && !mostlyBold
            val applyItalic = w.italic

            if (applyBold || applyItalic) {
                val run = mutableListOf<LayoutWord>()
                while (i < words.size && (words[i].bold && !mostlyBold) == applyBold && words[i].italic == applyItalic) {
                    run.add(words[i])
                    i++
                }

                val runText = run.joinToString(" ") { escapeMarkdown(it.text) }
                val styled = when {
                    applyBold && applyItalic -> "***$runText***"
                    applyBold -> "**$runText**"
                    applyItalic -> "*$runText*"
                    else -> runText
                }

                if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                sb.append(styled)
            } else {
                if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                sb.append(escapeMarkdown(w.text))
                i++
            }
        }

        return sb.toString()
    }

    private fun escapeMarkdown(text: String): String {
        return text
    }
}
