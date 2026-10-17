package com.markdrop.app.conversion.assemble

/**
 * Pure Kotlin Assembler for cross-page markdown merging and heading normalization.
 * Zero Android framework dependencies to ensure full JVM unit testability.
 */
object Assembler {

    private val SENTENCE_END_PUNCTUATION = setOf('.', '!', '?', ':', '"', '”', '\'', '’', '…')
    private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
    private val TABLE_ROW_REGEX = Regex("""^\|.*\|$""")
    private val TABLE_SEPARATOR_REGEX = Regex("""^\|\s*(?::?-+:?\s*\|)+\s*$""")

    fun assemble(
        pages: List<String>,
        pageNumbers: List<Int>? = null
    ): String {
        if (pages.isEmpty() || pages.all { it.isBlank() }) return ""

        val effectiveNumbers = pageNumbers ?: (1..pages.size).toList()

        // Single page without explicit pageNumbers preserves plain page text
        if (pages.size == 1 && pageNumbers == null) {
            return normalizeHeadings(pages[0].trim())
        }

        val blocks = mutableListOf<String>()
        for (i in pages.indices) {
            val pageNum = effectiveNumbers.getOrElse(i) { i + 1 }
            val rawPage = pages[i].trim()
            val marker = "<!-- page: $pageNum -->"

            if (rawPage.isBlank() || rawPage == "<!-- blank page -->") {
                blocks.add("$marker\n<!-- blank page -->")
                continue
            }

            val pageText = if (rawPage.startsWith("<!-- page:")) {
                rawPage
            } else if (pageNumbers != null || i > 0) {
                "$marker\n\n$rawPage"
            } else {
                rawPage
            }

            blocks.add(pageText)
        }

        val merged = mutableListOf<String>()
        for (i in blocks.indices) {
            if (merged.isEmpty()) {
                merged.add(blocks[i])
                continue
            }

            val prevBlock = merged.last()
            val currBlock = blocks[i]

            // If current block is blank page, keep it separate
            if (currBlock.contains("<!-- blank page -->")) {
                merged.add(currBlock)
                continue
            }

            // 1. Check for split table across page boundary
            val tableMergeResult = tryMergeSplitTable(prevBlock, currBlock)
            if (tableMergeResult != null) {
                merged[merged.lastIndex] = tableMergeResult
                continue
            }

            // 2. Check for split paragraph across page boundary
            val paragraphMergeResult = tryMergeSplitParagraph(prevBlock, currBlock)
            if (paragraphMergeResult != null) {
                merged[merged.lastIndex] = paragraphMergeResult
                continue
            }

            // Standard page separation
            merged.add(currBlock)
        }

        val joined = merged.joinToString("\n\n")
        return normalizeHeadings(joined)
    }

    /**
     * Checks if prevBlock ends with a table and currBlock starts with a continuation of the same table.
     * Merges them only if column counts and headers match.
     */
    private fun tryMergeSplitTable(prevBlock: String, currBlock: String): String? {
        val prevLines = prevBlock.lines().dropLastWhile { it.isBlank() }
        val currLines = currBlock.lines().dropWhile { it.isBlank() }

        if (prevLines.isEmpty() || currLines.isEmpty()) return null

        val lastPrevLine = prevLines.last().trim()
        if (!TABLE_ROW_REGEX.matches(lastPrevLine)) return null

        // Locate first content line in currBlock past the page marker comment
        var currIdx = 0
        if (currLines[currIdx].trim().startsWith("<!-- page:")) {
            currIdx++
            while (currIdx < currLines.size && currLines[currIdx].isBlank()) {
                currIdx++
            }
        }
        if (currIdx >= currLines.size) return null
        val firstContentLine = currLines[currIdx].trim()
        if (!TABLE_ROW_REGEX.matches(firstContentLine)) return null

        // Find table separator in prevLines to locate header
        var prevSepIdx = -1
        for (i in prevLines.indices.reversed()) {
            val l = prevLines[i].trim()
            if (!TABLE_ROW_REGEX.matches(l)) break
            if (TABLE_SEPARATOR_REGEX.matches(l)) {
                prevSepIdx = i
                break
            }
        }
        if (prevSepIdx <= 0) return null
        val prevHeader = prevLines[prevSepIdx - 1].trim()
        val prevCols = prevHeader.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // Check if currBlock repeats header + separator
        val hasRepeatedHeader = currIdx + 1 < currLines.size &&
                TABLE_SEPARATOR_REGEX.matches(currLines[currIdx + 1].trim())

        if (hasRepeatedHeader) {
            val currHeader = firstContentLine
            val currCols = currHeader.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (prevCols != currCols) return null // Different headers -> different tables!
            currIdx += 2 // Skip repeated header and separator
        } else {
            val currCols = firstContentLine.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (currCols.size != prevCols.size || prevCols.size < 2) return null
        }

        while (currIdx < currLines.size && currLines[currIdx].isBlank()) {
            currIdx++
        }
        val remainingCurr = currLines.drop(currIdx)
        return (prevLines + remainingCurr).joinToString("\n")
    }

    /**
     * Checks if prevBlock ends with a paragraph that continues on currBlock.
     */
    private fun tryMergeSplitParagraph(prevBlock: String, currBlock: String): String? {
        val prevLines = prevBlock.lines().dropLastWhile { it.isBlank() }
        val currLines = currBlock.lines().dropWhile { it.isBlank() }

        if (prevLines.isEmpty() || currLines.isEmpty()) return null

        val lastPrevLine = prevLines.last().trim()

        var currIdx = 0
        var pageNum: Int? = null
        if (currLines[currIdx].trim().startsWith("<!-- page:")) {
            val m = Regex("""<!--\s*page:\s*(\d+)\s*-->""").find(currLines[currIdx])
            pageNum = m?.groupValues?.get(1)?.toIntOrNull()
            currIdx++
            while (currIdx < currLines.size && currLines[currIdx].isBlank()) {
                currIdx++
            }
        }
        if (currIdx >= currLines.size) return null
        val firstCurrLine = currLines[currIdx].trim()

        if (HEADING_REGEX.matches(lastPrevLine) ||
            TABLE_ROW_REGEX.matches(lastPrevLine) ||
            lastPrevLine.startsWith("- ") ||
            lastPrevLine.startsWith("* ") ||
            lastPrevLine.startsWith("```") ||
            lastPrevLine.startsWith(">")
        ) {
            return null
        }

        if (HEADING_REGEX.matches(firstCurrLine) ||
            TABLE_ROW_REGEX.matches(firstCurrLine) ||
            firstCurrLine.startsWith("- ") ||
            firstCurrLine.startsWith("* ") ||
            firstCurrLine.startsWith("```") ||
            firstCurrLine.startsWith(">")
        ) {
            return null
        }

        val lastChar = lastPrevLine.lastOrNull() ?: return null
        val firstChar = firstCurrLine.firstOrNull() ?: return null

        val isSentenceTerminal = SENTENCE_END_PUNCTUATION.contains(lastChar)
        val isContinuation = firstChar.isLowerCase()

        if (!isSentenceTerminal && isContinuation) {
            val prefix = prevLines.dropLast(1)
            val marker = if (pageNum != null) " <!-- page: $pageNum --> " else " "
            val mergedLastLine = if (lastPrevLine.endsWith("-") && firstChar.isLowerCase()) {
                lastPrevLine.dropLast(1) + firstCurrLine
            } else {
                "$lastPrevLine$marker$firstCurrLine"
            }
            val remainingCurr = currLines.drop(currIdx + 1)
            return (prefix + listOf(mergedLastLine) + remainingCurr).joinToString("\n")
        }

        return null
    }

    /**
     * Normalizes heading levels:
     * - If document has no H1 (#) but starts at H2 or H3, shifts all headings up (unless question headings).
     * - Prevents large skips (e.g. H1 followed immediately by H3 is normalized to H2).
     */
    fun normalizeHeadings(markdown: String): String {
        val lines = markdown.lines()
        val headingLines = lines.mapIndexedNotNull { index, line ->
            val match = HEADING_REGEX.matchEntire(line.trim())
            if (match != null) Pair(index, match.groupValues[1].length) else null
        }

        if (headingLines.isEmpty()) return markdown

        val minLevel = headingLines.minOf { it.second }
        val shift = if (minLevel > 1) minLevel - 1 else 0

        val mutableLines = lines.toMutableList()
        var currentMaxLevel = 0

        for ((lineIndex, originalLevel) in headingLines) {
            var newLevel = originalLevel - shift

            // Prevent skips greater than 1 level deeper than current parent
            if (currentMaxLevel > 0 && newLevel > currentMaxLevel + 1) {
                newLevel = currentMaxLevel + 1
            }
            currentMaxLevel = maxOf(currentMaxLevel, newLevel)

            val line = mutableLines[lineIndex].trim()
            val match = HEADING_REGEX.matchEntire(line)
            if (match != null) {
                val text = match.groupValues[2]
                mutableLines[lineIndex] = "${"#".repeat(newLevel)} $text"
            }
        }

        return mutableLines.joinToString("\n")
    }
}
