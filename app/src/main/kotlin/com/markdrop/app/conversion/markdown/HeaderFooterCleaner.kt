package com.markdrop.app.conversion.markdown

import kotlin.math.max

object HeaderFooterCleaner {

    private val PAGE_NUMBER_REGEX = Regex("""(?i)^\s*(?:page\s*)?\d+(?:\s*(?:of|/)\s*\d+)?\s*$""")
    private val DECORATED_PAGE_NUM_REGEX = Regex("""^\s*(?:[-—–]|\[)\s*\d+\s*(?:[-—–]|\])\s*$""")
    private val BARE_NUMBER_REGEX = Regex("""^\s*\d+\s*$""")

    /**
     * Cleans recurring headers, footers, and positional page numbers across pages.
     *
     * Principles:
     * - Candidates for removal are strictly the first 2 and last 2 non-empty lines on pages with >= 10 lines.
     * - Short pages (< 10 lines) are never stripped.
     * - Recurring lines are removed only if appearing in candidate bands on >= max(3, 40%) of pages.
     * - Never strip on 1- or 2-page documents via the recurring rule.
     * - Zero keyword watermark regexes to protect legitimate document body text.
     */
    fun cleanPages(pageMarkdowns: List<String>): List<String> {
        if (pageMarkdowns.isEmpty()) return emptyList()

        // Break each page into lines and compute candidate indices
        data class PageInfo(
            val originalLines: List<String>,
            val nonEmptyIndices: List<Int>,
            val topCandidateIndices: Set<Int>,
            val bottomCandidateIndices: Set<Int>
        )

        val parsedPages = pageMarkdowns.map { pageText ->
            val lines = pageText.lines()
            val nonEmpty = lines.indices.filter { lines[it].isNotBlank() }
            if (nonEmpty.size < 10) {
                PageInfo(lines, nonEmpty, emptySet(), emptySet())
            } else {
                val topCandidates = nonEmpty.take(2).toSet()
                val bottomCandidates = nonEmpty.takeLast(2).toSet()
                PageInfo(lines, nonEmpty, topCandidates, bottomCandidates)
            }
        }

        val pageCount = pageMarkdowns.size
        val enableRecurringRule = pageCount >= 3
        val recurringThreshold = max(3, Math.ceil(pageCount * 0.4).toInt())

        // Collect candidate lines for recurring header/footer detection
        val topCandidatesByPage = parsedPages.map { page ->
            page.topCandidateIndices.map { page.originalLines[it].trim() }
        }
        val bottomCandidatesByPage = parsedPages.map { page ->
            page.bottomCandidateIndices.map { page.originalLines[it].trim() }
        }

        fun isRecurringInBand(candidate: String, pagesCandidates: List<List<String>>): Boolean {
            if (!enableRecurringRule || candidate.isBlank()) return false
            var matchCount = 0
            for (pageList in pagesCandidates) {
                val matchesPage = pageList.any { other ->
                    normalizeLine(candidate) == normalizeLine(other) ||
                            levenshteinRatio(candidate.lowercase(), other.lowercase()) >= 0.85
                }
                if (matchesPage) {
                    matchCount++
                }
            }
            return matchCount >= recurringThreshold
        }

        return parsedPages.map { page ->
            if (page.topCandidateIndices.isEmpty() && page.bottomCandidateIndices.isEmpty()) {
                // Short page: keep intact
                return@map page.originalLines.joinToString("\n")
            }

            val filteredLines = mutableListOf<String>()
            page.originalLines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val isTopCandidate = page.topCandidateIndices.contains(index)
                val isBottomCandidate = page.bottomCandidateIndices.contains(index)

                if (isTopCandidate || isBottomCandidate) {
                    // Check page number pattern in candidate band
                    val isPageNumber = PAGE_NUMBER_REGEX.matches(trimmed) ||
                            DECORATED_PAGE_NUM_REGEX.matches(trimmed) ||
                            (BARE_NUMBER_REGEX.matches(trimmed) && (index == page.nonEmptyIndices.firstOrNull() || index == page.nonEmptyIndices.lastOrNull()))

                    if (isPageNumber) {
                        return@forEachIndexed
                    }

                    // Check recurring rule
                    if (isTopCandidate && isRecurringInBand(trimmed, topCandidatesByPage)) {
                        return@forEachIndexed
                    }
                    if (isBottomCandidate && isRecurringInBand(trimmed, bottomCandidatesByPage)) {
                        return@forEachIndexed
                    }
                }

                filteredLines.add(line)
            }

            filteredLines.dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
                .joinToString("\n")
        }
    }

    fun normalizeLine(line: String): String {
        return line.trim()
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""\s+"""), " ")
            .lowercase()
    }

    fun levenshteinRatio(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val d = IntArray(s2.length + 1) { it }
        for (i in s1.indices) {
            var prev = d[0]
            d[0] = i + 1
            for (j in s2.indices) {
                val temp = d[j + 1]
                d[j + 1] = if (s1[i] == s2[j]) {
                    prev
                } else {
                    minOf(prev, d[j + 1], d[j]) + 1
                }
                prev = temp
            }
        }
        return 1.0 - (d[s2.length].toDouble() / maxLen)
    }
}
