package com.markdrop.app.conversion.layout

/**
 * Robust list detector and formatter.
 * Handles standard and OCR bullet symbols, multi-line continuation, and nested indentation.
 */
object ListDetector {

    val EXTENDED_BULLET_REGEX = Regex("""^([\u2022\u2043\u25E6\u25AA\u25CF\u00B0\u00A9\-\*\+])\s*(.*)$""")
    val NUMBERED_LIST_REGEX = Regex("""^(\(?\d+[\.\)]|\(?[a-zA-Z][\.\)])\s*(.*)$""")

    fun isListCandidate(lines: List<LayoutLine>): Boolean {
        if (lines.isEmpty()) return false
        val firstRaw = lines.first().text.trim()
        val hasBullet = EXTENDED_BULLET_REGEX.matches(firstRaw)
        val hasNumber = NUMBERED_LIST_REGEX.matches(firstRaw)
        return hasBullet || hasNumber
    }

    /**
     * Formats a sequence of lines into a Markdown list.
     * Preserves indentation for nested items and folds continuation lines.
     */
    fun formatList(lines: List<LayoutLine>, formatWordsInline: (List<LayoutWord>) -> String): String {
        if (lines.isEmpty()) return ""

        val listItems = mutableListOf<String>()
        val baseLeft = lines.minOfOrNull { it.left } ?: 0f
        var inList = false

        for (line in lines) {
            val raw = line.text.trim()
            val bulletMatch = EXTENDED_BULLET_REGEX.matchEntire(raw)
            val numberedMatch = NUMBERED_LIST_REGEX.matchEntire(raw)

            // Compute indentation based on left coordinate
            val indentLevel = ((line.left - baseLeft) / 16f).toInt().coerceIn(0, 4)
            val indentPrefix = "  ".repeat(indentLevel)

            when {
                bulletMatch != null -> {
                    val contentWords = if (line.words.size > 1) line.words.drop(1) else emptyList()
                    val formatted = formatWordsInline(contentWords)
                    val content = if (formatted.isNotBlank()) formatted else bulletMatch.groupValues[2]
                    listItems.add("$indentPrefix- $content")
                    inList = true
                }
                numberedMatch != null -> {
                    val num = numberedMatch.groupValues[1]
                    val contentWords = if (line.words.size > 1) line.words.drop(1) else emptyList()
                    val formatted = formatWordsInline(contentWords)
                    val content = if (formatted.isNotBlank()) formatted else numberedMatch.groupValues[2]
                    val cleanNum = if (num.endsWith(".") || num.endsWith(")")) num else "$num."
                    listItems.add("$indentPrefix$cleanNum $content")
                    inList = true
                }
                inList -> {
                    val lastIdx = listItems.lastIndex
                    if (lastIdx >= 0 && (line.left > baseLeft + 8f || raw.length < 80)) {
                        val continuation = formatWordsInline(line.words)
                        if (continuation.isNotBlank()) {
                            listItems[lastIdx] = listItems[lastIdx] + " " + continuation
                        }
                    } else {
                        inList = false
                        listItems.add(formatWordsInline(line.words))
                    }
                }
                else -> {
                    listItems.add(formatWordsInline(line.words))
                }
            }
        }

        return listItems.joinToString("\n")
    }
}
