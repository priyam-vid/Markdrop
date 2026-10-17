package com.markdrop.app.conversion.pdf

import android.graphics.Path
import android.graphics.PointF
import com.markdrop.app.conversion.layout.FigureBox
import com.markdrop.app.conversion.layout.LayoutWord
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
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

    private class ImageLocatorEngine(private val targetPage: PDPage) : PDFGraphicsStreamEngine(targetPage) {
        val figureBoxes = mutableListOf<FigureBox>()
        var totalImageArea = 0f
        var hasFullPageImage = false
        private val currentPoint = PointF(0f, 0f)

        override fun drawImage(image: PDImage) {
            val ctm = graphicsState.currentTransformationMatrix
            val pageHeight = targetPage.cropBox?.height ?: targetPage.mediaBox?.height ?: 842f

            val x = ctm.translateX
            val y = ctm.translateY
            val w = ctm.scalingFactorX
            val h = ctm.scalingFactorY

            val left = x
            val top = pageHeight - (y + h)
            val right = x + w
            val bottom = pageHeight - y

            val imgArea = (w * h).coerceAtLeast(0f)
            totalImageArea += imgArea

            if (image.width >= 800 && image.height >= 1000) {
                hasFullPageImage = true
            } else if (w >= 50f && h >= 50f) {
                figureBoxes.add(
                    FigureBox(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        description = "Embedded raster image (${image.width}x${image.height})"
                    )
                )
            }
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {}
        override fun clip(fillType: Path.FillType) {}
        override fun moveTo(x: Float, y: Float) { currentPoint.set(x, y) }
        override fun lineTo(x: Float, y: Float) { currentPoint.set(x, y) }
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { currentPoint.set(x3, y3) }
        override fun getCurrentPoint(): PointF = currentPoint
        override fun closePath() {}
        override fun endPath() {}
        override fun strokePath() {}
        override fun fillPath(fillType: Path.FillType) {}
        override fun fillAndStrokePath(fillType: Path.FillType) {}
        override fun shadingFill(shadingName: COSName) {}
    }

    fun analyze(page: PDPage, words: List<LayoutWord>, pageWidth: Float, pageHeight: Float): PageContentInfo {
        val charCount = words.sumOf { it.text.length }
        val pageArea = (pageWidth * pageHeight).coerceAtLeast(1f)

        val figureBoxes = mutableListOf<FigureBox>()
        var totalImageArea = 0f
        var hasFullPageImage = false

        // 1. Try real coordinate extraction using PDFGraphicsStreamEngine
        try {
            val engine = ImageLocatorEngine(page)
            engine.processPage(page)
            figureBoxes.addAll(engine.figureBoxes)
            totalImageArea = engine.totalImageArea
            hasFullPageImage = engine.hasFullPageImage
        } catch (_: Throwable) {
            // Fallback to static resource inspection if stream processing encounters unsupported constructs
            try {
                val resources = page.resources
                if (resources != null) {
                    for (name in resources.xObjectNames) {
                        if (resources.isImageXObject(name)) {
                            val xobject = resources.getXObject(name)
                            if (xobject is PDImageXObject) {
                                val imgW = xobject.width.toFloat()
                                val imgH = xobject.height.toFloat()
                                val estimatedArea = (imgW * imgH).coerceAtLeast(0f)
                                totalImageArea += estimatedArea

                                if (imgW >= 800 && imgH >= 1000) {
                                    hasFullPageImage = true
                                } else if (imgW >= 150 && imgH >= 150 && figureBoxes.isEmpty()) {
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
            } catch (_: Throwable) {
            }
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
