package com.markdrop.app.conversion.markdown

object HeaderFooterCleaner {

    private val PAGE_NUMBER_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:page\s+)?\d+\s*(?:of|/)\s*\d+\s*$"""),
        Regex("""(?i)^\s*page\s+\d+\s*$"""),
        Regex("""^\s*[-—–]\s*\d+\s*[-—–]\s*$"""),
        Regex("""^\s*\[\s*\d+\s*\]\s*$"""),
        Regex("""^\s*\d+\s*$""")
    )

    private val WATERMARK_PATTERNS = listOf(
        Regex("""(?i).*(?:confidential|draft|do not distribute|internal use only).*"""),
        Regex("""(?i).*(?:scanned with|scanned by)\s+.*"""),
        Regex("""(?i).*(?:all rights reserved|copyright\s*©).*""")
    )

    /**
     * Cleans recurring headers, footers, running watermarks, and page numbers across pages.
     */
    fun cleanPages(pageMarkdowns: List<String>): List<String> {
        if (pageMarkdowns.isEmpty()) return emptyList()
        if (pageMarkdowns.size == 1) {
            // For a single page, clean obvious isolated page number footers/headers and watermarks
            return listOf(cleanSinglePage(pageMarkdowns[0]))
        }

        // Split each page into lines
        val pagesLines = pageMarkdowns.map { it.lines().map { line -> line.trimEnd() } }

        // Find candidate header lines (top 1-3 non-empty lines) across all pages
        val topLinesByPage = pagesLines.map { lines ->
            lines.filter { it.isNotBlank() }.take(3)
        }

        // Find candidate footer lines (bottom 1-3 non-empty lines) across all pages
        val bottomLinesByPage = pagesLines.map { lines ->
            lines.filter { it.isNotBlank() }.takeLast(3)
        }

        // Frequency map of top lines
        val topFrequency = mutableMapOf<String, Int>()
        topLinesByPage.flatten().forEach { line ->
            val normalized = normalizeLine(line)
            if (normalized.isNotBlank()) {
                topFrequency[normalized] = (topFrequency[normalized] ?: 0) + 1
            }
        }

        // Frequency map of bottom lines
        val bottomFrequency = mutableMapOf<String, Int>()
        bottomLinesByPage.flatten().forEach { line ->
            val normalized = normalizeLine(line)
            if (normalized.isNotBlank()) {
                bottomFrequency[normalized] = (bottomFrequency[normalized] ?: 0) + 1
            }
        }

        val threshold = maxOf(2, (pageMarkdowns.size * 0.4).toInt())

        val recurringHeaders = topFrequency.filter { it.value >= threshold }.keys
        val recurringFooters = bottomFrequency.filter { it.value >= threshold }.keys

        return pagesLines.map { lines ->
            val filteredLines = mutableListOf<String>()
            val totalLines = lines.size

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val normalized = normalizeLine(trimmed)
                val isNearTop = index < 4
                val isNearBottom = index >= totalLines - 4

                val isPageNum = isPageNumber(trimmed)
                val isWatermark = isWatermarkLine(trimmed)

                val isRecurringHeader = isNearTop && recurringHeaders.contains(normalized)
                val isRecurringFooter = isNearBottom && recurringFooters.contains(normalized)

                if (isWatermark) {
                    // Skip watermark
                    return@forEachIndexed
                }

                if ((isNearTop || isNearBottom) && isPageNum) {
                    // Skip page number near boundaries
                    return@forEachIndexed
                }

                if (isRecurringHeader || isRecurringFooter) {
                    // Skip recurring header or footer
                    return@forEachIndexed
                }

                filteredLines.add(line)
            }

            // Strip redundant leading/trailing empty lines
            filteredLines.dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
                .joinToString("\n")
        }
    }

    private fun cleanSinglePage(markdown: String): String {
        val lines = markdown.lines()
        val filtered = lines.filterIndexed { index, line ->
            val trimmed = line.trim()
            val isBoundary = index < 3 || index >= lines.size - 3
            if (isWatermarkLine(trimmed)) return@filterIndexed false
            if (isBoundary && isPageNumber(trimmed)) return@filterIndexed false
            true
        }
        return filtered.dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }

    private fun isPageNumber(line: String): Boolean {
        if (line.isBlank()) return false
        return PAGE_NUMBER_PATTERNS.any { it.matches(line) }
    }

    private fun isWatermarkLine(line: String): Boolean {
        if (line.isBlank()) return false
        return WATERMARK_PATTERNS.any { it.matches(line) }
    }

    private fun normalizeLine(line: String): String {
        // Strip digits, punctuation, and multiple spaces to match recurring headers like "Markdrop App | Page 1"
        return line.trim()
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""\s+"""), " ")
            .lowercase()
    }
}
