package com.markdrop.app.conversion.pdf

import com.markdrop.app.conversion.layout.FigureBox
import com.markdrop.app.conversion.layout.LayoutWord
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject

data class PageContentInfo(
    val charCount: Int,
    val hasFullPageImage: Boolean,
    val figureBoxes: List<FigureBox>,
    val imageAreaRatio: Float,
    val isSearchableScan: Boolean
) {
    val isUsableTextLayer: Boolean
        get() {
            // If page is empty or nearly empty of text, not a usable text layer
            if (charCount < 50) return false

            // If a full page image exists and character count is low (< 200),
            // it is a scanned page with Bates numbers, stamps, or headers -> route to OCR/Vision
            if (hasFullPageImage && charCount < 200) return false

            return true
        }
}

object PageContentAnalyzer {

    fun analyze(page: PDPage, words: List<LayoutWord>, pageWidth: Float, pageHeight: Float): PageContentInfo {
        val charCount = words.sumOf { it.text.length }
        val pageArea = (pageWidth * pageHeight).coerceAtLeast(1f)

        val figureBoxes = mutableListOf<FigureBox>()
        var totalImageArea = 0f
        var hasFullPageImage = false

        try {
            val resources = page.resources
            if (resources != null) {
                for (name in resources.xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val xobject = resources.getXObject(name)
                        if (xobject is PDImageXObject) {
                            val imgW = xobject.width.toFloat()
                            val imgH = xobject.height.toFloat()
                            // Rough estimation of image coverage on page
                            val estimatedArea = (imgW * imgH).coerceAtLeast(0f)
                            totalImageArea += estimatedArea

                            // If single image resolution is large (e.g. typical scanned A4 page at 150-300 DPI)
                            if (imgW >= 800 && imgH >= 1000) {
                                hasFullPageImage = true
                            } else if (imgW >= 150 && imgH >= 150) {
                                // Sub-page image or diagram
                                figureBoxes.add(
                                    FigureBox(
                                        left = 50f,
                                        top = 150f,
                                        right = pageWidth - 50f,
                                        bottom = 350f,
                                        description = "Embedded raster image (${xobject.width}x${xobject.height})"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Non-critical resource parsing fallback
        }

        val imageAreaRatio = (totalImageArea / pageArea).coerceIn(0f, 10f)
        val isSearchableScan = hasFullPageImage && charCount >= 200

        return PageContentInfo(
            charCount = charCount,
            hasFullPageImage = hasFullPageImage,
            figureBoxes = figureBoxes,
            imageAreaRatio = imageAreaRatio,
            isSearchableScan = isSearchableScan
        )
    }
}
