package com.markdrop.app.conversion.markdown

object MathTablePostProcessor {

    private val MATH_SYMBOLS_REGEX = Regex("""(?i)\b(?:\\frac|\\sqrt|\\sum|\\int|\\partial|\\alpha|\\beta|\\gamma|\\theta|\\pi|\\lambda|\\sigma|\\Delta|\\Omega)\b""")
    private val EQUATION_PATTERNS = Regex("""(?m)^\s*([a-zA-Z]\([a-zA-Z0-9,\s]+\)\s*=|E\s*=\s*mc\^2|[a-zA-Z]\s*=\s*[-+]?[0-9a-zA-Z\s*/+\-^()_]+[+\-*/^][0-9a-zA-Z\s*/+\-^()_]+)\s*$""")
    private val INLINE_MATH_PATTERNS = Regex("""(?<!\$)\b([a-zA-Z]_[0-9a-zA-Z]+|[a-zA-Z]\^[0-9a-zA-Z]+)\b(?!\$)""")

    fun hasUnformattedMath(markdown: String): Boolean {
        val lines = markdown.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("$") && trimmed.endsWith("$")) continue
            if (MATH_SYMBOLS_REGEX.containsMatchIn(trimmed)) return true
            if (EQUATION_PATTERNS.containsMatchIn(trimmed)) return true
            if (INLINE_MATH_PATTERNS.containsMatchIn(trimmed)) return true
        }
        return false
    }

    fun hasUnformattedTable(markdown: String): Boolean {
        val lines = markdown.lines()
        var consecutiveTabularLines = 0

        for (line in lines) {
            val trimmed = line.trim()
            // Ignore already valid markdown tables
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                consecutiveTabularLines = 0
                continue
            }

            // Check for multiple tabs or multiple double-spaces separating columns
            val tabCount = line.count { it == '\t' }
            val spaceColumns = line.split(Regex("""\s{3,}""")).filter { it.isNotBlank() }

            if (tabCount >= 2 || spaceColumns.size >= 3) {
                consecutiveTabularLines++
                if (consecutiveTabularLines >= 2) return true
            } else {
                consecutiveTabularLines = 0
            }
        }
        return false
    }

    fun fixMathEquations(markdown: String): String {
        val lines = markdown.lines().toMutableList()

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            // Already a block equation
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$")) continue
            // Already an inline equation
            if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length > 2) continue

            // Check for standalone equation line
            if (EQUATION_PATTERNS.matches(trimmed) || (MATH_SYMBOLS_REGEX.containsMatchIn(trimmed) && !trimmed.contains("```"))) {
                var processed = trimmed
                    .replace("+-", "\\pm ")
                    .replace("<=", "\\le ")
                    .replace(">=", "\\ge ")
                    .replace("!=", "\\ne ")
                    .replace(Regex("""sqrt\(([^)]+)\)"""), "\\sqrt{$1}")

                lines[i] = "$$ $processed $$"
            } else {
                // Wrap isolated inline sub/superscripts: e.g. x_1 or x^2
                val replaced = INLINE_MATH_PATTERNS.replace(line) { match ->
                    "$" + match.value + "$"
                }
                lines[i] = replaced
            }
        }

        return lines.joinToString("\n")
    }

    fun formatAsciiTables(markdown: String): String {
        val lines = markdown.lines()
        val result = mutableListOf<String>()
        var buffer = mutableListOf<List<String>>()

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            if (buffer.size >= 2) {
                val maxCols = buffer.maxOf { it.size }
                val paddedRows = buffer.map { row ->
                    val padded = row.toMutableList()
                    while (padded.size < maxCols) padded.add("")
                    padded
                }

                // Header row
                val header = paddedRows[0]
                result.add("| " + header.joinToString(" | ") + " |")

                // Separator row
                val separator = List(maxCols) { "---" }
                result.add("| " + separator.joinToString(" | ") + " |")

                // Data rows
                for (r in 1 until paddedRows.size) {
                    result.add("| " + paddedRows[r].joinToString(" | ") + " |")
                }
            } else {
                buffer.forEach { row ->
                    result.add(row.joinToString("   "))
                }
            }
            buffer.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flushBuffer()
                result.add(line)
                continue
            }

            val tabs = line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
            val spaces = line.split(Regex("""\s{3,}""")).map { it.trim() }.filter { it.isNotEmpty() }

            val cols = if (tabs.size >= 2) tabs else if (spaces.size >= 3) spaces else null

            if (cols != null) {
                buffer.add(cols)
            } else {
                flushBuffer()
                result.add(line)
            }
        }
        flushBuffer()

        return result.joinToString("\n")
    }
}
