package com.markdrop.app.conversion.layout

/**
 * Recursive XY-Cut algorithm for spatial document layout decomposition.
 * Discovers columns, banners, sidebars, and reading order naturally via white-space projection.
 * Free of Android framework dependencies.
 */
object XYCut {

    fun segment(lines: List<LayoutLine>, pageWidth: Float, pageHeight: Float): List<LayoutBlock> {
        if (lines.isEmpty()) return emptyList()
        val sortedLines = lines.sortedBy { it.y }
        return recursiveCut(sortedLines, 0)
    }

    private fun recursiveCut(lines: List<LayoutLine>, depth: Int): List<LayoutBlock> {
        if (lines.size <= 1 || depth > 8) {
            return if (lines.isEmpty()) emptyList() else listOf(LayoutBlock(lines))
        }

        val bodyMode = computeBodyMode(lines)
        val verticalGutters = findVerticalGutters(lines, bodyMode)

        // 1. If there is a complete vertical gutter with 0 spanning lines (pure columns), cut vertically immediately
        if (verticalGutters.isNotEmpty()) {
            val gutter = verticalGutters.first()
            val (leftCol, rightCol, spanningLines) = partitionLines(lines, gutter)

            if (leftCol.isNotEmpty() && rightCol.isNotEmpty() && spanningLines.isEmpty()) {
                return recursiveCut(leftCol, depth + 1) + recursiveCut(rightCol, depth + 1)
            }
        }

        // 2. Check for prominent horizontal white gaps (e.g. separating Title, Body, Footnotes)
        val horizontalCutIdx = findHorizontalCut(lines, bodyMode)
        if (horizontalCutIdx != -1) {
            val topLines = lines.subList(0, horizontalCutIdx)
            val bottomLines = lines.subList(horizontalCutIdx, lines.size)
            return recursiveCut(topLines, depth + 1) + recursiveCut(bottomLines, depth + 1)
        }

        // 3. Check for vertical white-space gutters across lines with spanning lines
        if (verticalGutters.isNotEmpty()) {
            val gutter = verticalGutters.first()
            val (leftCol, rightCol, spanningLines) = partitionLines(lines, gutter)

            // If we found a clean column separation
            if (leftCol.isNotEmpty() && rightCol.isNotEmpty() && (leftCol.size + rightCol.size) >= (lines.size * 0.6)) {
                val topSpanning = spanningLines.filter { it.y < minOf(leftCol.first().y, rightCol.first().y) }
                val bottomSpanning = spanningLines.filter { it !in topSpanning }

                val result = mutableListOf<LayoutBlock>()
                if (topSpanning.isNotEmpty()) result.addAll(recursiveCut(topSpanning, depth + 1))
                result.addAll(recursiveCut(leftCol, depth + 1))
                result.addAll(recursiveCut(rightCol, depth + 1))
                if (bottomSpanning.isNotEmpty()) result.addAll(recursiveCut(bottomSpanning, depth + 1))
                return result
            }
        }

        return listOf(LayoutBlock(lines))
    }

    private fun partitionLines(
        lines: List<LayoutLine>,
        gutter: Gutter
    ): Triple<List<LayoutLine>, List<LayoutLine>, List<LayoutLine>> {
        val leftCol = mutableListOf<LayoutLine>()
        val rightCol = mutableListOf<LayoutLine>()
        val spanning = mutableListOf<LayoutLine>()

        for (line in lines) {
            val leftWords = line.words.filter { it.right <= gutter.start }
            val rightWords = line.words.filter { it.left >= gutter.end }
            val spanningWords = line.words.filter { it !in leftWords && it !in rightWords }

            when {
                leftWords.isNotEmpty() && rightWords.isNotEmpty() && spanningWords.isEmpty() -> {
                    leftCol.add(LayoutLine(words = leftWords, y = line.y, height = line.height, fontSize = line.fontSize))
                    rightCol.add(LayoutLine(words = rightWords, y = line.y, height = line.height, fontSize = line.fontSize))
                }
                line.right <= gutter.start -> leftCol.add(line)
                line.left >= gutter.end -> rightCol.add(line)
                else -> spanning.add(line)
            }
        }
        return Triple(leftCol, rightCol, spanning)
    }

    private fun findHorizontalCut(lines: List<LayoutLine>, bodyMode: Float): Int {
        val minGap = (bodyMode * 1.8f).coerceAtLeast(22f)
        var maxGap = 0f
        var bestIdx = -1

        for (i in 1 until lines.size) {
            val prev = lines[i - 1]
            val curr = lines[i]
            val gap = curr.y - (prev.y + prev.height)
            if (gap >= minGap && gap > maxGap) {
                maxGap = gap
                bestIdx = i
            }
        }
        return bestIdx
    }

    data class Gutter(val start: Float, val end: Float) {
        val width: Float get() = end - start
    }

    private fun findVerticalGutters(lines: List<LayoutLine>, bodyMode: Float): List<Gutter> {
        val minGutterWidth = (bodyMode * 1.5f).coerceAtLeast(20f)
        val leftBound = lines.minOf { it.left }
        val rightBound = lines.maxOf { it.right }
        val width = rightBound - leftBound
        if (width < 100f) return emptyList()

        // Histogram of horizontal coverage in 2-point bins using actual word boundaries
        val binSize = 2.0f
        val numBins = ((rightBound - leftBound) / binSize).toInt() + 1
        val coverage = IntArray(numBins)

        for (line in lines) {
            for (word in line.words) {
                val startBin = ((word.left - leftBound) / binSize).toInt().coerceIn(0, numBins - 1)
                val endBin = ((word.right - leftBound) / binSize).toInt().coerceIn(0, numBins - 1)
                for (b in startBin..endBin) {
                    coverage[b]++
                }
            }
        }

        // Find intervals where coverage is zero across nearly all lines
        val maxAllowedCoverage = (lines.size * 0.08f).toInt()
        val gutters = mutableListOf<Gutter>()
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
                    if (gutterEnd - gutterStart >= minGutterWidth) {
                        // Ensure gutter is not on outer margins
                        if (gutterStart > leftBound + 25f && gutterEnd < rightBound - 25f) {
                            gutters.add(Gutter(gutterStart, gutterEnd))
                        }
                    }
                }
            }
        }

        return gutters.sortedByDescending { it.width }
    }

    private fun computeBodyMode(lines: List<LayoutLine>): Float {
        val sizes = lines.map { it.fontSize }.filter { it > 0f }
        if (sizes.isEmpty()) return 12f
        val buckets = sizes.groupingBy { kotlin.math.round(it) }.eachCount()
        return buckets.maxByOrNull { it.value }?.key ?: 12f
    }
}
