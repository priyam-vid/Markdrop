package com.markdrop.app.conversion.pdf

import com.markdrop.app.conversion.layout.LayoutWord
import kotlin.math.abs

/*
 * PDFBox Coordinate & Orientation Semantics (verified from com.tom_roush.pdfbox.text.TextPosition bytecode):
 * 1. getYDirAdj() vs getY():
 *    getY() returns the unadjusted y-coordinate in default PDF user space (origin at bottom-left, y increasing upward).
 *    getYDirAdj() accounts for page rotation and text direction, transforming into a top-down display coordinate system
 *    where (0,0) is at top-left and y increases downward (computed as `pageHeight - translateY` for 0-degree rotation).
 * 2. getHeightDir() vs getHeight():
 *    getHeightDir() returns the character height (maxHeight) aligned with text direction, reflecting font ascender height
 *    scaled to font size above the baseline.
 * 3. top vs bottom:
 *    Because (0,0) is at top-left and yDirAdj represents the baseline y-position in top-down coordinates:
 *    bottom = getYDirAdj() (the baseline)
 *    top = getYDirAdj() - getHeightDir() (ascender top, having smaller y).
 */

/**
 * Pure Kotlin glyph-to-word grouping algorithm.
 * Reconstructs words from individual glyphs and kerning offsets without losing spaces.
 * Free of Android framework dependencies.
 */
data class Glyph(
    val unicode: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val bold: Boolean = false,
    val italic: Boolean = false
)

object WordsFromGlyphs {

    private val LIGATURE_MAP = mapOf(
        "\uFB00" to "ff",
        "\uFB01" to "fi",
        "\uFB02" to "fl",
        "\uFB03" to "ffi",
        "\uFB04" to "ffl",
        "\uFB05" to "st",
        "\uFB06" to "st"
    )

    fun normalizeLigatures(text: String): String {
        var result = text
        for ((ligature, replacement) in LIGATURE_MAP) {
            if (result.contains(ligature)) {
                result = result.replace(ligature, replacement)
            }
        }
        return result
    }

    /**
     * Groups a collection of positioned glyphs into coherent LayoutWord instances.
     * Evaluates baseline alignment and inter-character gaps.
     */
    fun group(glyphs: List<Glyph>): List<LayoutWord> {
        if (glyphs.isEmpty()) return emptyList()

        // 1. Cluster glyphs by baseline y (tolerance 0.4 * fontSize)
        class BaselineGroup(var y: Float, var fontSize: Float) {
            val glyphList = mutableListOf<Glyph>()
        }

        val baselines = mutableListOf<BaselineGroup>()
        // Sort glyphs primarily by y, secondarily by x
        val sortedGlyphs = glyphs.sortedWith(compareBy<Glyph> { it.y }.thenBy { it.x })

        for (g in sortedGlyphs) {
            val tol = (g.fontSize * 0.4f).coerceAtLeast(2.0f)
            val matched = baselines.find { abs(it.y - g.y) <= tol }
            if (matched != null) {
                matched.glyphList.add(g)
                // Running weighted average for baseline
                matched.y = (matched.y * 0.7f) + (g.y * 0.3f)
                matched.fontSize = maxOf(matched.fontSize, g.fontSize)
            } else {
                val newGroup = BaselineGroup(g.y, g.fontSize)
                newGroup.glyphList.add(g)
                baselines.add(newGroup)
            }
        }

        // Sort baselines top to bottom
        baselines.sortBy { it.y }

        val words = mutableListOf<LayoutWord>()

        // 2. In each baseline, split words on explicit spaces or geometric gaps
        for (base in baselines) {
            val lineGlyphs = base.glyphList.sortedBy { it.x }
            val currentWordGlyphs = mutableListOf<Glyph>()

            fun flushCurrentWord() {
                if (currentWordGlyphs.isEmpty()) return
                val rawText = currentWordGlyphs.joinToString("") { it.unicode }.trim()
                val text = normalizeLigatures(rawText)
                if (text.isNotEmpty()) {
                    val left = currentWordGlyphs.minOf { it.x }
                    val top = currentWordGlyphs.minOf { it.y - it.height }
                    val right = currentWordGlyphs.maxOf { it.x + it.width }
                    val bottom = currentWordGlyphs.maxOf { it.y }
                    val fontSize = currentWordGlyphs.map { it.fontSize }.average().toFloat()
                    val bold = currentWordGlyphs.count { it.bold } >= (currentWordGlyphs.size * 0.6)
                    val italic = currentWordGlyphs.count { it.italic } >= (currentWordGlyphs.size * 0.6)

                    words.add(
                        LayoutWord(
                            text = text,
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            fontSize = fontSize,
                            bold = bold,
                            italic = italic
                        )
                    )
                }
                currentWordGlyphs.clear()
            }

            for (i in lineGlyphs.indices) {
                val glyph = lineGlyphs[i]
                val isSpaceChar = glyph.unicode.isBlank() || glyph.unicode == "\u00A0"

                if (isSpaceChar) {
                    flushCurrentWord()
                    continue
                }

                if (currentWordGlyphs.isNotEmpty()) {
                    val prev = currentWordGlyphs.last()
                    val gap = glyph.x - (prev.x + prev.width)
                    val maxIntraWordGap = (prev.fontSize * 0.22f).coerceAtLeast(1.2f)

                    if (glyph.x < prev.x + prev.width) {
                        val overlap = (prev.x + prev.width) - glyph.x
                        if (overlap >= 0.3f * prev.width) {
                            // Significant backwards jump / overlap: treat as word boundary
                            flushCurrentWord()
                        }
                        // else: overlap < 0.3 * prev.width -> font kerning within the same word
                    } else if (gap > maxIntraWordGap) {
                        // Word break due to kerning gap or space offset
                        flushCurrentWord()
                    }
                }

                currentWordGlyphs.add(glyph)
            }

            flushCurrentWord()
        }

        return words
    }
}
