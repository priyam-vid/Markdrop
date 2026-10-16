package com.markdrop.app.conversion.pdf

/**
 * Parses user page selection strings (e.g. "1,3,5-8", "2-4", "1") into sorted 0-based page indices.
 */
object PageSelectionParser {

    fun parse(selectionStr: String?, totalPages: Int): List<Int> {
        if (selectionStr.isNullOrBlank() || totalPages <= 0) {
            return (0 until totalPages).toList()
        }

        val indices = mutableSetOf<Int>()
        val parts = selectionStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        for (part in parts) {
            if (part.contains("-")) {
                val rangeParts = part.split("-").map { it.trim() }
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].toIntOrNull()
                    val end = rangeParts[1].toIntOrNull()
                    if (start != null && end != null) {
                        val min = minOf(start, end).coerceAtLeast(1)
                        val max = maxOf(start, end).coerceAtMost(totalPages)
                        for (p in min..max) {
                            indices.add(p - 1) // convert 1-based to 0-based
                        }
                    }
                }
            } else {
                val single = part.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    indices.add(single - 1)
                }
            }
        }

        val result = indices.sorted()
        return if (result.isNotEmpty()) result else (0 until totalPages).toList()
    }
}
