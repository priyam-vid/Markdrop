package com.markdrop.app.conversion.layout

/**
 * Recursive XY-Cut algorithm for spatial document layout decomposition.
 * Discovers columns, banners, sidebars, and reading order naturally via white-space projection.
 * Supports N-column layouts (N in [1, 2, 3, 4]) and banner-aware segmentation.
 * Free of Android framework dependencies.
 */
object XYCut {

    data class Gutter(val start: Float, val end: Float) {
        val width: Float get() = end - start
    }

    fun segment(lines: List<LayoutLine>, pageWidth: Float, pageHeight: Float): List<LayoutBlock> {
        if (lines.isEmpty()) return emptyList()
        val sortedLines = lines.sortedBy { it.y }
        return recursiveCut(sortedLines, 0)
    }

    private fun recursiveCut(lines: List<LayoutLine>, depth: Int): List<LayoutBlock> {
        if (lines.size <= 1 || depth > 10) {
            return if (lines.isEmpty()) emptyList() else listOf(LayoutBlock(lines))
        }

        // 1. If there is a clean vertical gutter with 0 crossing words, cut vertically immediately
        val cleanGutters = findVerticalGutters(lines, maxCrossingRatio = 0.0f)
        if (cleanGutters.isNotEmpty()) {
            val hasSignatureBlanks = lines.any { it.text.contains("___") }
            val hasHeadingInLines = lines.any { line ->
                line.words.any { word ->
                    word.text.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)$"""))
                } || line.text.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)\s+[A-Z].*"""))
            }
            if (!hasSignatureBlanks && (lines.size >= 3 || hasHeadingInLines)) {
                val columnSlices = sliceLinesByGutters(lines, cleanGutters)
                val columnHeight = lines.maxOf { it.y + it.height } - lines.minOf { it.y }
                if (columnSlices.size > 1 && columnSlices.all { it.isNotEmpty() } && columnHeight >= 25.0f) {
                    return columnSlices.flatMap { recursiveCut(it, depth + 1) }
                }
            }
        }

        // 2. Banner-aware segmentation:
        // Detect candidate vertical gutters across page height using relaxed threshold (provisional gutters)
        val provisionalGutters = findVerticalGutters(lines, maxCrossingRatio = 0.45f)
        if (provisionalGutters.isNotEmpty()) {
            val (topBanner, middleColumns, bottomBanner) = partitionBannerAndColumns(lines, provisionalGutters)
            if (topBanner.isNotEmpty() || bottomBanner.isNotEmpty()) {
                val result = mutableListOf<LayoutBlock>()
                if (topBanner.isNotEmpty()) {
                    result.addAll(recursiveCut(topBanner, depth + 1))
                }
                if (middleColumns.isNotEmpty()) {
                    result.addAll(recursiveCut(middleColumns, depth + 1))
                }
                if (bottomBanner.isNotEmpty()) {
                    result.addAll(recursiveCut(bottomBanner, depth + 1))
                }
                return result
            }
        }

        // 3. Dynamic horizontal cut: threshold relative to local line height (gap >= 1.2 * localLineHeight, min 4 pt)
        val horizontalCutIdx = findHorizontalCut(lines)
        if (horizontalCutIdx != -1) {
            val topLines = lines.subList(0, horizontalCutIdx)
            val bottomLines = lines.subList(horizontalCutIdx, lines.size)
            return recursiveCut(topLines, depth + 1) + recursiveCut(bottomLines, depth + 1)
        }

        return listOf(LayoutBlock(lines))
    }

    /**
     * Determines which column slot a word belongs to given a set of sorted non-overlapping gutters.
     * Returns:
     * - slot index in 0..k if word falls entirely inside that slot
     * - -1 if word crosses or falls inside any gutter
     */
    private fun getWordSlot(word: LayoutWord, sortedGutters: List<Gutter>): Int {
        val k = sortedGutters.size
        for (g in sortedGutters) {
            // If word overlaps with the gutter
            if (word.left < g.end - 1.0f && word.right > g.start + 1.0f) {
                return -1
            }
        }

        if (word.right <= sortedGutters[0].start + 2.0f) return 0
        if (word.left >= sortedGutters[k - 1].end - 2.0f) return k

        for (i in 1 until k) {
            if (word.left >= sortedGutters[i - 1].end - 2.0f && word.right <= sortedGutters[i].start + 2.0f) {
                return i
            }
        }
        return -1
    }

    /**
     * Splits a LayoutLine by vertical gutters into constituent lines per column slot.
     * Preserves all words.
     */
    private fun splitLineByGutters(line: LayoutLine, sortedGutters: List<Gutter>): List<Pair<Int, LayoutLine>> {
        val wordSlots = line.words.groupBy { getWordSlot(it, sortedGutters) }
        val result = mutableListOf<Pair<Int, LayoutLine>>()

        for ((slot, wordsInSlot) in wordSlots) {
            if (wordsInSlot.isEmpty()) continue
            val sortedWords = wordsInSlot.sortedBy { it.left }
            result.add(
                slot to LayoutLine(
                    words = sortedWords,
                    y = line.y,
                    height = line.height,
                    fontSize = line.fontSize,
                    left = sortedWords.first().left,
                    right = sortedWords.last().right
                )
            )
        }
        return result
    }

    private fun partitionBannerAndColumns(
        lines: List<LayoutLine>,
        gutters: List<Gutter>
    ): Triple<List<LayoutLine>, List<LayoutLine>, List<LayoutLine>> {
        val sortedGutters = gutters.sortedBy { it.start }

        // Deconstruct lines into column slot candidates and spanning banner candidates
        val slotLinesWithSlot = lines.flatMap { splitLineByGutters(it, sortedGutters) }
        val columnLines = slotLinesWithSlot.filter { it.first >= 0 }.map { it.second }
        val bannerLines = slotLinesWithSlot.filter { it.first < 0 }.map { it.second }
        if (columnLines.isEmpty()) {
            return Triple(emptyList(), lines, emptyList())
        }

        val hasSignatureBlanks = columnLines.any { it.text.contains("___") }
        if (hasSignatureBlanks) {
            return Triple(emptyList(), lines, emptyList())
        }

        val linesInColumns = lines.filter { line ->
            val parts = splitLineByGutters(line, sortedGutters)
            parts.any { it.first >= 0 }
        }
        val hasHeadingInColumns = linesInColumns.any { line ->
            line.words.any { word ->
                word.text.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)$"""))
            } || line.text.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)\s+[A-Z].*"""))
        }
        if (linesInColumns.size < 3 && !hasHeadingInColumns) {
            return Triple(emptyList(), lines, emptyList())
        }

        val firstColY = columnLines.minOf { it.y }
        val lastColBottom = columnLines.maxOf { it.y + it.height }
        val columnHeight = lastColBottom - firstColY

        if (columnHeight < 25.0f) {
            return Triple(emptyList(), lines, emptyList())
        }

        val topBanner = mutableListOf<LayoutLine>()
        val middle = mutableListOf<LayoutLine>()
        val bottomBanner = mutableListOf<LayoutLine>()

        for (line in lines) {
            val parts = splitLineByGutters(line, sortedGutters)
            val isSpanning = parts.any { it.first < 0 }

            if (isSpanning) {
                if (line.y + line.height <= firstColY + 2.0f) {
                    topBanner.add(line)
                } else if (line.y >= lastColBottom - 2.0f) {
                    bottomBanner.add(line)
                } else {
                    // Spanning in the middle (e.g. wide figure or full-width table)
                    middle.add(line)
                }
            } else {
                // Non-spanning lines
                if (line.y + line.height <= firstColY + 1.0f) {
                    topBanner.add(line)
                } else if (line.y >= lastColBottom - 1.0f && bannerLines.any { it.y >= lastColBottom - 2.0f }) {
                    bottomBanner.add(line)
                } else {
                    middle.add(line)
                }
            }
        }

        return Triple(topBanner, middle, bottomBanner)
    }

    private fun sliceLinesByGutters(lines: List<LayoutLine>, gutters: List<Gutter>): List<List<LayoutLine>> {
        val sortedGutters = gutters.sortedBy { it.start }
        val k = sortedGutters.size
        val slices = List(k + 1) { mutableListOf<LayoutLine>() }

        for (line in lines) {
            val parts = splitLineByGutters(line, sortedGutters)
            for ((slot, subLine) in parts) {
                if (slot in 0..k) {
                    slices[slot].add(subLine)
                } else {
                    // Fallback for spanning lines: place into the slot containing line center
                    val center = (subLine.left + subLine.right) / 2.0f
                    val targetSlot = when {
                        center <= sortedGutters[0].start -> 0
                        center >= sortedGutters[k - 1].end -> k
                        else -> {
                            var matched = 0
                            for (i in 1 until k) {
                                if (center >= sortedGutters[i - 1].end && center <= sortedGutters[i].start) {
                                    matched = i
                                    break
                                }
                            }
                            matched
                        }
                    }
                    slices[targetSlot].add(subLine)
                }
            }
        }

        return slices
    }

    private fun findHorizontalCut(lines: List<LayoutLine>): Int {
        var maxGap = 0f
        var bestIdx = -1

        for (i in 1 until lines.size) {
            val prev = lines[i - 1]
            val curr = lines[i]
            val gap = curr.y - (prev.y + prev.height)
            val localLineHeight = maxOf(prev.height, curr.height, prev.fontSize, curr.fontSize)
            val minGap = (localLineHeight * 1.2f).coerceAtLeast(4.0f)

            if (gap >= minGap && gap > maxGap) {
                maxGap = gap
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun findVerticalGutters(lines: List<LayoutLine>, maxCrossingRatio: Float): List<Gutter> {
        val allWords = lines.flatMap { it.words }
        if (allWords.isEmpty()) return emptyList()

        val leftBound = allWords.minOf { it.left }
        val rightBound = allWords.maxOf { it.right }
        val totalWidth = rightBound - leftBound
        if (totalWidth < 100f) return emptyList()

        val binSize = 2.0f
        val numBins = ((totalWidth) / binSize).toInt() + 1
        val coverage = IntArray(numBins)

        for (word in allWords) {
            val startBin = ((word.left - leftBound) / binSize).toInt().coerceIn(0, numBins - 1)
            val endBin = ((word.right - leftBound) / binSize).toInt().coerceIn(0, numBins - 1)
            for (b in startBin..endBin) {
                coverage[b]++
            }
        }

        val maxAllowedCoverage = (lines.size * maxCrossingRatio).toInt()
        val candidateGutters = mutableListOf<Gutter>()
        var inGutter = false
        var gutterStart = 0f

        for (b in 0 until numBins) {
            val x = leftBound + b * binSize
            if (coverage[b] <= maxAllowedCoverage) {
                if (!inGutter) {
                    inGutter = true
                    gutterStart = x
                }
            } else {
                if (inGutter) {
                    inGutter = false
                    val gutterEnd = x
                    val width = gutterEnd - gutterStart
                    if (width >= 8.0f && gutterStart > leftBound + 15f && gutterEnd < rightBound - 15f) {
                        // Check if text exists on both sides
                        val hasLeft = allWords.any { it.right <= gutterStart + 2.0f }
                        val hasRight = allWords.any { it.left >= gutterEnd - 2.0f }
                        if (hasLeft && hasRight) {
                            candidateGutters.add(Gutter(gutterStart, gutterEnd))
                        }
                    }
                }
            }
        }

        if (inGutter) {
            val gutterEnd = leftBound + numBins * binSize
            val width = gutterEnd - gutterStart
            if (width >= 8.0f && gutterStart > leftBound + 15f && gutterEnd < rightBound - 15f) {
                val hasLeft = allWords.any { it.right <= gutterStart + 2.0f }
                val hasRight = allWords.any { it.left >= gutterEnd - 2.0f }
                if (hasLeft && hasRight) {
                    candidateGutters.add(Gutter(gutterStart, gutterEnd))
                }
            }
        }

        // Sort by start x and merge closely adjacent/overlapping candidate gutters
        if (candidateGutters.isEmpty()) return emptyList()
        val sorted = candidateGutters.sortedBy { it.start }
        val merged = mutableListOf<Gutter>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end + 4.0f) {
                // Merge
                current = Gutter(current.start, maxOf(current.end, next.end))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        // Filter and keep up to 3 non-overlapping gutters (up to 4 columns)
        return merged.sortedByDescending { it.width }.take(3).sortedBy { it.start }
    }

    private fun isSectionHeading(line: LayoutLine): Boolean {
        val raw = line.text.trim()
        return raw.matches(Regex("""^(\d{1,2}\.|\d{1,2}(\.\d+)+)\s+[A-Z].*""")) ||
                raw.startsWith("#") ||
                (line.words.isNotEmpty() && line.words.all { it.bold } && raw.length < 60)
    }
}
