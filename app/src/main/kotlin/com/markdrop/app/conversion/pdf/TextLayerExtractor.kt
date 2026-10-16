package com.markdrop.app.conversion.pdf

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.layout.LayoutLine
import com.markdrop.app.conversion.layout.LineClusterer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.io.StringWriter

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
                    }
                    isInitialized = true
                }
            }
        }
    }

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
        } catch (_: Exception) {
            null
        }
    }

    fun extractFromDocument(document: PDDocument, pageIndex: Int): List<LayoutLine>? {
        val collectedGlyphs = mutableListOf<Glyph>()

        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode ?: ""
                val font = text.font
                val desc = font?.fontDescriptor
                val fontName = font?.name?.lowercase() ?: ""

                val isBold = desc?.isForceBold == true ||
                        (desc?.fontWeight ?: 400f) >= 700f ||
                        fontName.contains("bold")

                val isItalic = desc?.isItalic == true ||
                        (desc?.italicAngle ?: 0f) != 0f ||
                        fontName.contains("italic") ||
                        fontName.contains("oblique")

                collectedGlyphs.add(
                    Glyph(
                        unicode = unicode,
                        x = text.xDirAdj,
                        y = text.yDirAdj,
                        width = text.widthDirAdj,
                        height = text.heightDir,
                        fontSize = text.fontSizeInPt,
                        bold = isBold,
                        italic = isItalic
                    )
                )
                super.processTextPosition(text)
            }
        }

        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.sortByPosition = false

        try {
            stripper.writeText(document, StringWriter())
        } catch (_: Exception) {
            return null
        }

        val words = WordsFromGlyphs.group(collectedGlyphs)
        if (words.isEmpty()) return null

        val page = document.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox
        val contentInfo = PageContentAnalyzer.analyze(page, words, box.width, box.height)
        if (!contentInfo.isUsableTextLayer) return null

        return LineClusterer.cluster(words)
    }
}
