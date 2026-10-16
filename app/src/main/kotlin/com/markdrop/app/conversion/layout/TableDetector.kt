package com.markdrop.app.conversion.layout

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
 * Discovers columns via inter-word spacing gutters and vertical X-alignment across rows.
 * Supports multi-word cells, missing cells, and strictly rejects prose paragraphs.
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
                // Normal word spacing: ~3-5pt. Column gutters: typically >= 14pt or >= 1.4 * fontSize.
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

        // Find candidate continuous row ranges where lines have multiple cell fragments
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
        // Collect all left coordinates to discover column anchors
        val allLefts = frags.flatMap { row -> row.map { it.left } }.sorted()
        if (allLefts.isEmpty()) return null

        // Cluster left coordinates within tolerance (18pt)
        val columnAnchors = mutableListOf<Float>()
        var cluster = mutableListOf<Float>()

        for (left in allLefts) {
            if (cluster.isEmpty()) {
                cluster.add(left)
            } else {
                if (left - cluster.last() <= 18f) {
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

        // Map each line's fragments to the closest column anchor
        val mappedRows = mutableListOf<List<String>>()
        var validRowMatchCount = 0

        for (row in frags) {
            val rowCells = Array(numColumns) { "" }
            var rowFilled = 0

            for (fragment in row) {
                // Find closest anchor
                var bestAnchorIdx = -1
                var bestDist = Float.MAX_VALUE

                for (c in columnAnchors.indices) {
                    val dist = kotlin.math.abs(fragment.left - columnAnchors[c])
                    if (dist < bestDist && dist <= 30f) {
                        bestDist = dist
                        bestAnchorIdx = c
                    }
                }

                if (bestAnchorIdx != -1) {
                    val existing = rowCells[bestAnchorIdx]
                    rowCells[bestAnchorIdx] = if (existing.isEmpty()) fragment.text else "$existing ${fragment.text}"
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

        // Check for prose characteristics: average cell length > 80 chars
        val totalCells = mappedRows.sumOf { r -> r.count { it.isNotBlank() } }
        val totalChars = mappedRows.sumOf { r -> r.sumOf { it.length } }
        if (totalCells > 0 && (totalChars / totalCells) > 80) {
            return null
        }

        // Strict rejection for 2-column prose: if 2 columns and average words per cell > 4, reject
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
