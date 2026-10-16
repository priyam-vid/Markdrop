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

    fun assemble(pages: List<String>): String {
        val nonBlankPages = pages.filter { it.isNotBlank() }
        if (nonBlankPages.isEmpty()) return ""
        if (nonBlankPages.size == 1) {
            return normalizeHeadings(nonBlankPages[0])
        }

        val merged = mutableListOf<String>()
        merged.add(nonBlankPages[0].trim())

        for (i in 1 until nonBlankPages.size) {
            val prevPage = merged.last()
            val currPage = nonBlankPages[i].trim()
            val pageNum = i + 1

            // 1. Check for split table across page boundary
            val tableMergeResult = tryMergeSplitTable(prevPage, currPage)
            if (tableMergeResult != null) {
                merged[merged.lastIndex] = tableMergeResult
                continue
            }

            // 2. Check for split paragraph across page boundary
            val paragraphMergeResult = tryMergeSplitParagraph(prevPage, currPage, pageNum)
            if (paragraphMergeResult != null) {
                merged[merged.lastIndex] = paragraphMergeResult
                continue
            }

            // Standard page separation: emit page comment marker
            merged.add("<!-- page: $pageNum -->\n\n$currPage")
        }

        val joined = merged.joinToString("\n\n")
        return normalizeHeadings(joined)
    }

    /**
     * Checks if prevPage ends with a table and currPage starts with a table of identical columns.
     * Merges them by removing the duplicated header/separator row from currPage.
     */
    private fun tryMergeSplitTable(prevPage: String, currPage: String): String? {
        val prevLines = prevPage.lines().dropLastWhile { it.isBlank() }
        val currLines = currPage.lines().dropWhile { it.isBlank() }

        if (prevLines.isEmpty() || currLines.isEmpty()) return null

        val lastPrevLine = prevLines.last().trim()
        val firstCurrLine = currLines.first().trim()

        if (!TABLE_ROW_REGEX.matches(lastPrevLine) || !TABLE_ROW_REGEX.matches(firstCurrLine)) {
            return null
        }

        val prevColCount = lastPrevLine.split("|").filter { it.isNotBlank() }.size
        val currColCount = firstCurrLine.split("|").filter { it.isNotBlank() }.size

        if (prevColCount != currColCount || prevColCount < 2) return null

        // Check if currPage starts with a duplicate table header + separator
        var rowsToSkip = 0
        if (currLines.size >= 2 && TABLE_SEPARATOR_REGEX.matches(currLines[1].trim())) {
            rowsToSkip = 2 // Skip header and separator
        }

        val remainingCurrLines = currLines.drop(rowsToSkip)
        val combinedLines = prevLines + remainingCurrLines
        return combinedLines.joinToString("\n")
    }

    /**
     * Checks if prevPage ends with a paragraph that continues on currPage.
     */
    private fun tryMergeSplitParagraph(prevPage: String, currPage: String, pageNum: Int): String? {
        val prevLines = prevPage.lines().dropLastWhile { it.isBlank() }
        val currLines = currPage.lines().dropWhile { it.isBlank() }

        if (prevLines.isEmpty() || currLines.isEmpty()) return null

        val lastPrevLine = prevLines.last().trim()
        val firstCurrLine = currLines.first().trim()

        // If previous ends with heading, table, list, or code fence, do not merge
        if (HEADING_REGEX.matches(lastPrevLine) ||
            TABLE_ROW_REGEX.matches(lastPrevLine) ||
            lastPrevLine.startsWith("- ") ||
            lastPrevLine.startsWith("* ") ||
            lastPrevLine.startsWith("```")
        ) {
            return null
        }

        // If current starts with heading, table, list, or code fence, do not merge
        if (HEADING_REGEX.matches(firstCurrLine) ||
            TABLE_ROW_REGEX.matches(firstCurrLine) ||
            firstCurrLine.startsWith("- ") ||
            firstCurrLine.startsWith("* ") ||
            firstCurrLine.startsWith("```")
        ) {
            return null
        }

        val lastChar = lastPrevLine.lastOrNull() ?: return null
        val firstChar = firstCurrLine.firstOrNull() ?: return null

        // Split paragraph signal: previous line does NOT end with sentence-terminal punctuation
        // AND next line starts with lowercase letter or continued words
        val isSentenceTerminal = SENTENCE_END_PUNCTUATION.contains(lastChar)
        val isContinuation = firstChar.isLowerCase() || !isSentenceTerminal

        if (!isSentenceTerminal && isContinuation) {
            val prefix = prevLines.dropLast(1)
            val mergedLastLine = if (lastPrevLine.endsWith("-") && firstChar.isLowerCase()) {
                lastPrevLine.dropLast(1) + firstCurrLine
            } else {
                "$lastPrevLine <!-- page: $pageNum --> $firstCurrLine"
            }
            val remainingCurr = currLines.drop(1)
            return (prefix + listOf(mergedLastLine) + remainingCurr).joinToString("\n")
        }

        return null
    }

    /**
     * Normalizes heading levels:
     * - If document has no H1 (#) but starts at H2 or H3, shifts all headings up.
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
