package com.markdrop.app.conversion.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDetectorTest {

    private fun makeLine(text: String, left: Float = 50f, y: Float = 100f) =
        LayoutLine(text = text, y = y, height = 12f, fontSize = 12f, left = left, right = left + 200f)

    @Test
    fun `formatList formats bullet lists with OCR symbols`() {
        val lines = listOf(
            makeLine("• First bullet item", y = 50f),
            makeLine("▪ Second bullet item", y = 70f),
            makeLine("● Third bullet item", y = 90f)
        )

        val md = ListDetector.formatList(lines) { it.joinToString(" ") { w -> w.text } }
        assertTrue(md.contains("- First bullet item"))
        assertTrue(md.contains("- Second bullet item"))
        assertTrue(md.contains("- Third bullet item"))
    }

    @Test
    fun `formatList handles nested indentation`() {
        val lines = listOf(
            makeLine("1. Parent item", left = 50f, y = 50f),
            makeLine("• Nested child item", left = 85f, y = 70f),
            makeLine("2. Second parent item", left = 50f, y = 90f)
        )

        val md = ListDetector.formatList(lines) { it.joinToString(" ") { w -> w.text } }
        val outputLines = md.lines()
        assertEquals("1. Parent item", outputLines[0])
        assertTrue("Expected 2-space or 4-space nested indent", outputLines[1].startsWith("  - Nested child item") || outputLines[1].startsWith("    - Nested child item"))
        assertEquals("2. Second parent item", outputLines[2])
    }

    @Test
    fun `formatList handles continuation lines by appending to previous item`() {
        val lines = listOf(
            makeLine("• Item with long text that", left = 50f, y = 50f),
            makeLine("wraps onto the next line gracefully.", left = 60f, y = 70f),
            makeLine("• Next item", left = 50f, y = 90f)
        )

        val md = ListDetector.formatList(lines) { it.joinToString(" ") { w -> w.text } }
        assertTrue(md.contains("- Item with long text that wraps onto the next line gracefully."))
        assertTrue(md.contains("- Next item"))
    }
}
