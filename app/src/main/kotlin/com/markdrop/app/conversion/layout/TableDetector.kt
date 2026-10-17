package com.markdrop.app.conversion.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fragment of words within a line that belong to the same table cell.
 */
data class CellFragment(
    val words: List<LayoutWord>,
    val left: Float = words.minOfOrNull { it.left } ?: 0f,
    val right: Float = words.maxOfOrNull { it.right } ?: 0f,
    val text: String = words.joinToString(" ") { it.text }
)

/**
 * Representation of a detected table region within a list of lines.
 */
data class DetectedTable(
    val startIndex: Int,
    val endIndex: Int, // exclusive
    val columnCount: Int,
    val markdown: String
)

/**
 * Robust spatial alignment-based table detector.
 * Discovers columns via inter-word spacing gutters and dual-edge (left or right) alignment.
 * Supports multi-word cells, right-aligned numeric columns, missing cells,
 * and strictly rejects prose paragraphs, TOC dot leaders, address blocks, and signature lines.
 */
object TableDetector {

    /**
     * Splits a LayoutLine's words into cell fragments based on inter-word gap spacing.
     */
    fun clusterLineIntoCells(line: LayoutLine): List<CellFragment> {
        val words = line.words.sortedBy { it.left }
        if (words.isEmpty()) {
            if (line.text.isNotBlank()) {
                return listOf(CellFragment(emptyList(), line.left, line.right, line.text))
            }
            return emptyList()
        }

        val cells = mutableListOf<CellFragment>()
        var currentCellWords = mutableListOf<LayoutWord>()

        for (word in words) {
            if (currentCellWords.isEmpty()) {
                currentCellWords.add(word)
            } else {
                val prevWord = currentCellWords.last()
                val gap = word.left - prevWord.right
                // Table column gutters are significantly larger than intra-cell word spacing.
                // Column gutters are typically >= 14pt or >= 1.35 * fontSize.
                val minColumnGap = (word.fontSize * 1.35f).coerceAtLeast(13f)
                if (gap >= minColumnGap) {
                    cells.add(CellFragment(currentCellWords))
                    currentCellWords = mutableListOf(word)
                } else {
                    currentCellWords.add(word)
                }
            }
        }
        if (currentCellWords.isNotEmpty()) {
            cells.add(CellFragment(currentCellWords))
        }
        return cells
    }

    /**
     * Attempts to find a table within a slice of lines.
     * Returns DetectedTable if found, or null if the lines do not form a table.
     */
    fun findTable(lines: List<LayoutLine>): DetectedTable? {
        if (lines.size < 2) return null

        val lineFragments = lines.map { clusterLineIntoCells(it) }

        var bestTable: DetectedTable? = null
        var start = 0

        while (start < lines.size) {
            // Find start of multi-cell lines
            if (lineFragments[start].size < 2) {
                start++
                continue
            }

            var end = start
            while (end < lines.size && (lineFragments[end].size >= 2 || (end > start && lineFragments[end].isNotEmpty() && lineFragments[end].first().left > lines.first().left + 30f))) {
                end++
            }

            val candidateCount = end - start
            if (candidateCount >= 2) {
                val candidateLines = lines.subList(start, end)
                val candidateFrags = lineFragments.subList(start, end)

                val table = evaluateCandidateTable(start, end, candidateLines, candidateFrags)
                if (table != null) {
                    bestTable = table
                    break
                }
            }
            start = end + 1
        }

        return bestTable
    }

    private fun evaluateCandidateTable(
        startIdx: Int,
        endIdx: Int,
        lines: List<LayoutLine>,
        frags: List<List<CellFragment>>
    ): DetectedTable? {
        val candidateCount = lines.size

        // Negative Control: Reject Table of Contents dot leaders
        if (lines.any { it.text.contains("...") || it.text.contains(". . .") || it.text.contains("···") || it.text.matches(Regex(""".*\.{3,}.*""")) }) {
            return null
        }

        // Negative Control: Reject signature lines and form fields
        if (lines.any { it.text.contains("Signature:", ignoreCase = true) || it.text.contains("Date:", ignoreCase = true) || it.text.contains("_____") }) {
            return null
        }

        // Negative Control: Reject lines where cells contain markdown heading syntax (### Column Headings)
        if (lines.any { it.text.trim().startsWith("#") } || frags.any { row -> row.any { it.text.trim().startsWith("#") } }) {
            return null
        }

        // Collect all left and right coordinates to discover column anchors
        val allFrags = frags.flatten()
        if (allFrags.isEmpty()) return null

        val allLefts = allFrags.map { it.left }.sorted()
        val columnAnchors = mutableListOf<Float>()
        var cluster = mutableListOf<Float>()

        for (left in allLefts) {
            if (cluster.isEmpty()) {
                cluster.add(left)
            } else {
                if (left - cluster.last() <= 20f) {
                    cluster.add(left)
                } else {
                    columnAnchors.add(cluster.average().toFloat())
                    cluster = mutableListOf(left)
                }
            }
        }
        if (cluster.isNotEmpty()) {
            columnAnchors.add(cluster.average().toFloat())
        }

        val numColumns = columnAnchors.size
        if (numColumns < 2) return null

        // Rule 1: Minimum rows requirement
        // Require at least 3 rows to form a table, or 2 rows ONLY if first row is distinctly bold/header-styled AND has >= 3 columns.
        val isFirstRowHeader = lines.first().isBold || frags.first().all { frag ->
            frag.words.isNotEmpty() && (frag.words.all { it.bold } || frag.words.count { it.bold } >= (frag.words.size * 0.6))
        }
        if (candidateCount < 3 && !(candidateCount == 2 && isFirstRowHeader && numColumns >= 3)) {
            return null
        }

        // Compute column bounds for overlap mapping
        data class ColRange(val left: Float, val right: Float)
        val colRanges = columnAnchors.mapIndexed { idx, anchorLeft ->
            val nextAnchor = columnAnchors.getOrNull(idx + 1) ?: (anchorLeft + 150f)
            val fragsInCol = allFrags.filter { it.left >= anchorLeft - 25f && it.left < nextAnchor - 10f }
            val colRight = if (fragsInCol.isNotEmpty()) fragsInCol.maxOf { it.right } else nextAnchor
            ColRange(anchorLeft, colRight)
        }

        // Map each line's fragments to the best matching column
        val mappedRows = mutableListOf<List<String>>()
        val colLeftCoords = List(numColumns) { mutableListOf<Float>() }
        val colRightCoords = List(numColumns) { mutableListOf<Float>() }
        var validRowMatchCount = 0

        for (row in frags) {
            val rowCells = Array(numColumns) { "" }
            var rowFilled = 0

            for (fragment in row) {
                // Find column with highest horizontal overlap, or closest anchor
                var bestCol = -1
                var bestOverlap = -1.0f

                for (c in 0 until numColumns) {
                    val cr = colRanges[c]
                    val overlap = max(0f, min(fragment.right, cr.right) - max(fragment.left, cr.left))
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap
                        bestCol = c
                    }
                }

                if (bestCol == -1 || bestOverlap <= 0f) {
                    // Fallback to closest anchor distance
                    bestCol = columnAnchors.indices.minByOrNull { abs(fragment.left - columnAnchors[it]) } ?: -1
                }

                if (bestCol != -1) {
                    val existing = rowCells[bestCol]
                    rowCells[bestCol] = if (existing.isEmpty()) fragment.text else "$existing ${fragment.text}"
                    colLeftCoords[bestCol].add(fragment.left)
                    colRightCoords[bestCol].add(fragment.right)
                    rowFilled++
                }
            }

            if (rowFilled >= 2 || (rowFilled >= 1 && numColumns <= 3)) {
                validRowMatchCount++
            }
            mappedRows.add(rowCells.toList())
        }

        // At least 70% of rows must match column layout
        if (validRowMatchCount < (lines.size * 0.7).toInt().coerceAtLeast(2)) {
            return null
        }

        // Rule 2: Dual-edge alignment verification
        // Each column must have consistent left alignment OR consistent right alignment across rows
        for (c in 0 until numColumns) {
            val lefts = colLeftCoords[c]
            val rights = colRightCoords[c]
            if (lefts.size >= 2) {
                val leftSpread = lefts.maxOrNull()!! - lefts.minOrNull()!!
                val rightSpread = rights.maxOrNull()!! - rights.minOrNull()!!
                val minSpread = min(leftSpread, rightSpread)
                // If neither left nor right alignment is consistent (spread > 22pt), reject as unaligned prose
                if (minSpread > 22.0f) {
                    return null
                }
            }
        }

        // Rule 3: Prose paragraph rejection
        val totalCells = mappedRows.sumOf { r -> r.count { it.isNotBlank() } }
        val totalChars = mappedRows.sumOf { r -> r.sumOf { it.length } }
        if (totalCells > 0 && (totalChars / totalCells) > 80) {
            return null
        }

        if (numColumns == 2) {
            val filledCells = mappedRows.flatMap { r -> r.filter { it.isNotBlank() } }
            if (filledCells.isNotEmpty()) {
                val avgWords = filledCells.map { cell -> cell.split(Regex("""\s+""")).count { it.isNotBlank() } }.average()
                if (avgWords > 4.0) {
                    return null
                }
            }
        }

        // Format as GFM Table
        fun formatRow(row: List<String>): String {
            val raw = "| " + row.joinToString(" | ") { escapeCell(it) } + " |"
            return raw.replace(Regex("""\|\s{2,}\|"""), "| |")
        }

        val sb = StringBuilder()
        val header = mappedRows.first()
        sb.append(formatRow(header)).append("\n")

        // Separator
        sb.append("| ").append(List(numColumns) { "---" }.joinToString(" | ")).append(" |\n")

        // Data rows
        for (r in 1 until mappedRows.size) {
            sb.append(formatRow(mappedRows[r])).append("\n")
        }

        return DetectedTable(
            startIndex = startIdx,
            endIndex = endIdx,
            columnCount = numColumns,
            markdown = sb.toString().trimEnd()
        )
    }

    private fun escapeCell(text: String): String {
        return text.replace("|", "\\|").trim()
    }
}
