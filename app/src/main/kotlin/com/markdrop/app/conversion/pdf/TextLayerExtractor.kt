package com.markdrop.app.conversion.pdf

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.layout.LayoutElement
import com.markdrop.app.conversion.layout.LayoutLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

object TextLayerExtractor {

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        PDFBoxResourceLoader.init(context.applicationContext)
                    } catch (_: Exception) {
                        // Already initialized or running in unit test
                    }
                    isInitialized = true
                }
            }
        }
    }

    /**
     * Extracts structured text layer lines from a PDF page if a usable embedded text layer exists.
     * Returns null if the page has no text layer, is a scanned image, or has corrupted/invisible text.
     */
    fun extractPageTextLayer(context: Context, pdfUri: Uri, pageIndex: Int): List<LayoutLine>? {
        ensureInitialized(context)

        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(pdfUri)
            if (inputStream == null) return null

            inputStream.use { stream ->
                PDDocument.load(stream).use { document ->
                    if (pageIndex < 0 || pageIndex >= document.numberOfPages) return null
                    extractFromDocument(document, pageIndex)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extractFromDocument(document: PDDocument, pageIndex: Int): List<LayoutLine>? {
        val collectedPositions = mutableListOf<TextPosition>()

        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode
                if (!unicode.isNullOrBlank()) {
                    collectedPositions.add(text)
                }
                super.processTextPosition(text)
            }
        }

        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.sortByPosition = true

        val rawText = try {
            val writer = java.io.StringWriter()
            stripper.writeText(document, writer)
            writer.toString()
        } catch (e: Exception) {
            return null
        }

        if (collectedPositions.isEmpty()) return null

        // Group text positions into horizontal lines by Y baseline
        val lines = groupPositionsIntoLines(collectedPositions)

        return if (hasUsableTextLayer(lines, rawText)) {
            lines
        } else {
            null
        }
    }

    private fun groupPositionsIntoLines(positions: List<TextPosition>): List<LayoutLine> {
        val sorted = positions.sortedWith(compareBy<TextPosition> { it.yDirAdj }.thenBy { it.xDirAdj })
        val lineGroups = mutableListOf<MutableList<TextPosition>>()

        for (pos in sorted) {
            val y = pos.yDirAdj
            val height = pos.heightDir.takeIf { it > 0 } ?: pos.fontSizeInPt
            val tolerance = (height * 0.4f).coerceAtLeast(2.5f)

            val matchingGroup = lineGroups.find { group ->
                val groupY = group.first().yDirAdj
                abs(y - groupY) <= tolerance
            }

            if (matchingGroup != null) {
                matchingGroup.add(pos)
            } else {
                lineGroups.add(mutableListOf(pos))
            }
        }

        return lineGroups.map { group ->
            val sortedGroup = group.sortedBy { it.xDirAdj }
            val lineText = sortedGroup.joinToString("") { it.unicode }
            val y = sortedGroup.map { it.yDirAdj }.average().toFloat()
            val height = sortedGroup.map { it.heightDir }.maxOrNull()?.toFloat() ?: 12f
            val fontSize = sortedGroup.map { it.fontSizeInPt }.average().toFloat()
            val left = sortedGroup.first().xDirAdj
            val right = sortedGroup.last().let { it.xDirAdj + it.widthDirAdj }

            val isBold = sortedGroup.any { pos ->
                val name = pos.font?.name?.lowercase() ?: ""
                name.contains("bold") || name.contains("black") || name.contains("heavy")
            }
            val isItalic = sortedGroup.any { pos ->
                val name = pos.font?.name?.lowercase() ?: ""
                name.contains("italic") || name.contains("oblique")
            }

            val elements = sortedGroup.map { pos ->
                LayoutElement(
                    text = pos.unicode,
                    left = pos.xDirAdj,
                    right = pos.xDirAdj + pos.widthDirAdj,
                    isBold = isBold,
                    isItalic = isItalic
                )
            }

            LayoutLine(
                text = lineText,
                y = y,
                height = height,
                fontSize = fontSize,
                isBold = isBold,
                isItalic = isItalic,
                left = left,
                right = right,
                elements = elements
            )
        }
    }

    /**
     * Determines whether the extracted text layer represents genuine, readable document text.
     */
    fun hasUsableTextLayer(lines: List<LayoutLine>, rawText: String): Boolean {
        if (lines.isEmpty() || rawText.isBlank()) return false

        // 1. Minimum content threshold
        val printableChars = rawText.count { !it.isWhitespace() && it.code in 32..126 || it.code >= 160 }
        val words = rawText.split(Regex("""\s+""")).filter { it.length >= 2 }
        val hasSufficientContent = printableChars >= 50 || (words.size >= 10 && words.map { it.length }.average() >= 3.0)
        if (!hasSufficientContent) return false

        // 2. Garbage / replacement character check
        val replacementCharCount = rawText.count { it == '\uFFFD' || (it.code in 0..31 && it != '\n' && it != '\r' && it != '\t') }
        val garbageRatio = replacementCharCount.toDouble() / rawText.length.coerceAtLeast(1)
        if (garbageRatio > 0.05) return false

        // 3. Spatial diversity check (reject single-point OCR coordinate dumps)
        if (lines.size >= 5) {
            val uniqueCoords = lines.map { Pair(it.left.roundToInt(), it.y.roundToInt()) }.toSet()
            val diversityRatio = uniqueCoords.size.toDouble() / lines.size
            if (diversityRatio < 0.5) return false
        }

        return true
    }
}
