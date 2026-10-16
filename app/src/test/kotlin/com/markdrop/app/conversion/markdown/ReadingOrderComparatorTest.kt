package com.markdrop.app.conversion.markdown

import android.graphics.Rect
import com.markdrop.app.conversion.ocr.OcrBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReadingOrderComparatorTest {

    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun testTransitivityContractWithRandomBoxes() {
        val random = Random(42)
        val tolerance = 12
        val comparator = ReadingOrderComparator(bandTolerance = tolerance)

        // Generate 300 randomly placed and slightly overlapping bounding boxes
        val blocks = (0 until 300).map { i ->
            val left = random.nextInt(0, 800)
            val top = random.nextInt(0, 1200)
            val width = random.nextInt(30, 200)
            val height = random.nextInt(10, 40)
            OcrBlock(
                text = "Block $i",
                boundingBox = rect(left, top, left + width, top + height),
                lines = emptyList()
            )
        }

        // TimSort contract test: Must NOT throw "Comparison method violates its general contract"
        val sorted = blocks.sortedWith(comparator)
        assertEquals("Expected 300 sorted blocks", 300, sorted.size)

        // Verify ordering consistency
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val cmp = comparator.compare(a, b)
            assertTrue("Ordering must be non-decreasing (cmp <= 0)", cmp <= 0)

            val rowA = (a.boundingBox?.top ?: 0) / tolerance
            val rowB = (b.boundingBox?.top ?: 0) / tolerance
            assertTrue("Row of A must be <= Row of B", rowA <= rowB)

            if (rowA == rowB) {
                val leftA = a.boundingBox?.left ?: 0
                val leftB = b.boundingBox?.left ?: 0
                assertTrue("In the same row, Left of A must be <= Left of B", leftA <= leftB)
            }
        }
    }

    @Test
    fun testTwoColumnNaturalSort() {
        val comparator = ReadingOrderComparator(bandTolerance = 15)

        // Box in header
        val header = OcrBlock("Header", rect(50, 20, 500, 50), emptyList())
        // Left column item 1
        val left1 = OcrBlock("Left 1", rect(50, 100, 250, 130), emptyList())
        // Right column item 1
        val right1 = OcrBlock("Right 1", rect(300, 100, 500, 130), emptyList())
        // Left column item 2
        val left2 = OcrBlock("Left 2", rect(50, 150, 250, 180), emptyList())

        val blocks = listOf(right1, left2, header, left1)
        val sorted = blocks.sortedWith(comparator)

        val names = sorted.map { it.text }
        assertEquals("Header should be first", "Header", names[0])
        assertEquals("In row 1, Left 1 should precede Right 1", "Left 1", names[1])
        assertEquals("In row 1, Right 1 should follow Left 1", "Right 1", names[2])
        assertEquals("Left 2 should be last", "Left 2", names[3])
    }
}
