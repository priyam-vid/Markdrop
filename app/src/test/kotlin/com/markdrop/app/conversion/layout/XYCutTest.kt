package com.markdrop.app.conversion.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class XYCutTest {

    private fun makeWord(text: String, left: Float, top: Float, width: Float = 40f, height: Float = 12f) =
        LayoutWord(text, left, top, left + width, top + height, fontSize = height)

    private fun makeLine(text: String, left: Float, top: Float, width: Float = 100f, height: Float = 12f) =
        LayoutLine(
            words = listOf(makeWord(text, left, top, width, height)),
            y = top,
            height = height,
            fontSize = height,
            left = left,
            right = left + width
        )

    @Test
    fun `segment orders two-column layout down left column then right column`() {
        val lines = listOf(
            makeLine("Left 1", left = 50f, top = 100f),
            makeLine("Right 1", left = 320f, top = 100f),
            makeLine("Left 2", left = 50f, top = 130f),
            makeLine("Right 2", left = 320f, top = 130f),
            makeLine("Left 3", left = 50f, top = 160f),
            makeLine("Right 3", left = 320f, top = 160f)
        )

        val blocks = XYCut.segment(lines, pageWidth = 595f, pageHeight = 842f)
        val orderedText = blocks.flatMap { it.lines }.map { it.text }

        // Expected: Left 1, Left 2, Left 3, Right 1, Right 2, Right 3
        assertEquals(listOf("Left 1", "Left 2", "Left 3", "Right 1", "Right 2", "Right 3"), orderedText)
    }

    @Test
    fun `segment preserves full-width title then two columns then footnote band`() {
        val lines = listOf(
            makeLine("Document Title", left = 50f, top = 40f, width = 450f),
            makeLine("Left 1", left = 50f, top = 120f),
            makeLine("Right 1", left = 320f, top = 120f),
            makeLine("Left 2", left = 50f, top = 150f),
            makeLine("Right 2", left = 320f, top = 150f),
            makeLine("Footnote text", left = 50f, top = 750f, width = 400f)
        )

        val blocks = XYCut.segment(lines, pageWidth = 595f, pageHeight = 842f)
        val orderedText = blocks.flatMap { it.lines }.map { it.text }

        assertEquals("Document Title", orderedText.first())
        assertEquals("Footnote text", orderedText.last())
        val mid = orderedText.subList(1, orderedText.size - 1)
        assertEquals(listOf("Left 1", "Left 2", "Right 1", "Right 2"), mid)
    }

    @Test
    fun `segment handles 500 random words deterministically without crashing`() {
        val random = Random(42)
        val randomWords = (1..500).map { i ->
            val left = random.nextFloat() * 500f
            val top = random.nextFloat() * 750f
            makeWord("w$i", left, top)
        }
        val lines = LineClusterer.cluster(randomWords)
        val blocks = XYCut.segment(lines, 600f, 800f)

        assertTrue(blocks.isNotEmpty())
        assertEquals(lines.size, blocks.sumOf { it.lines.size })
    }
}
