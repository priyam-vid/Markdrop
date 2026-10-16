package com.markdrop.app.conversion.markdown

import com.markdrop.app.conversion.ocr.OcrBlock

/**
 * A strictly transitive 2D comparator for ordering OCR blocks/lines in natural reading order.
 *
 * Mathematically prevents "Comparison method violates its general contract" (TimSort crash)
 * by establishing a strict total ordering via quantized vertical bands (rows) and horizontal coordinates.
 */
class ReadingOrderComparator(
    private val bandTolerance: Int = 12
) : Comparator<OcrBlock> {

    override fun compare(b1: OcrBlock, b2: OcrBlock): Int {
        val top1 = b1.boundingBox?.top ?: 0
        val top2 = b2.boundingBox?.top ?: 0

        val row1 = if (bandTolerance > 0) top1 / bandTolerance else top1
        val row2 = if (bandTolerance > 0) top2 / bandTolerance else top2

        if (row1 != row2) {
            return row1.compareTo(row2)
        }

        val left1 = b1.boundingBox?.left ?: 0
        val left2 = b2.boundingBox?.left ?: 0
        if (left1 != left2) {
            return left1.compareTo(left2)
        }

        return top1.compareTo(top2)
    }
}
