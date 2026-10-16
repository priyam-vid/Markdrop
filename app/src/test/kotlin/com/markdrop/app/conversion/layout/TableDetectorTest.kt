package com.markdrop.app.conversion.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableDetectorTest {

    private fun makeWord(text: String, left: Float, right: Float, y: Float = 100f, height: Float = 12f) =
        LayoutWord(text, left, y, right, y + height, fontSize = height)

    private fun makeLineWithWords(words: List<LayoutWord>, y: Float) =
        LayoutLine(words = words, y = y, height = 12f, fontSize = 12f)

    @Test
    fun `findTable correctly formats multiword table with headers and data`() {
        val header = makeLineWithWords(
            listOf(
                makeWord("Server", 50f, 90f, y = 50f),
                makeWord("Node", 95f, 130f, y = 50f),
                makeWord("CPU", 180f, 210f, y = 50f),
                makeWord("Cores", 215f, 255f, y = 50f),
                makeWord("Status", 320f, 360f, y = 50f)
            ),
            y = 50f
        )
        val row1 = makeLineWithWords(
            listOf(
                makeWord("Node", 50f, 85f, y = 75f),
                makeWord("Alpha", 90f, 130f, y = 75f),
                makeWord("32", 180f, 200f, y = 75f),
                makeWord("Online", 320f, 365f, y = 75f)
            ),
            y = 75f
        )
        val row2 = makeLineWithWords(
            listOf(
                makeWord("Node", 50f, 85f, y = 100f),
                makeWord("Beta", 90f, 125f, y = 100f),
                makeWord("64", 180f, 200f, y = 100f),
                makeWord("Active", 320f, 365f, y = 100f)
            ),
            y = 100f
        )

        val table = TableDetector.findTable(listOf(header, row1, row2))
        assertNotNull("Expected table to be detected", table)
        assertEquals(3, table?.columnCount)

        val md = table!!.markdown
        assertTrue(md.contains("| Server Node | CPU Cores | Status |"))
        assertTrue(md.contains("| --- | --- | --- |"))
        assertTrue(md.contains("| Node Alpha | 32 | Online |"))
        assertTrue(md.contains("| Node Beta | 64 | Active |"))
    }

    @Test
    fun `findTable handles missing middle cell gracefully`() {
        val header = makeLineWithWords(
            listOf(
                makeWord("ColA", 50f, 90f, y = 50f),
                makeWord("ColB", 180f, 220f, y = 50f),
                makeWord("ColC", 320f, 360f, y = 50f)
            ),
            y = 50f
        )
        val row1 = makeLineWithWords(
            listOf(
                makeWord("ValA1", 50f, 90f, y = 75f),
                makeWord("ValC1", 320f, 360f, y = 75f) // ColB is missing
            ),
            y = 75f
        )
        val row2 = makeLineWithWords(
            listOf(
                makeWord("ValA2", 50f, 90f, y = 100f),
                makeWord("ValB2", 180f, 220f, y = 100f),
                makeWord("ValC2", 320f, 360f, y = 100f)
            ),
            y = 100f
        )

        val table = TableDetector.findTable(listOf(header, row1, row2))
        assertNotNull(table)
        assertEquals(3, table?.columnCount)
        assertTrue(table!!.markdown.contains("| ValA1 | | ValC1 |"))
    }

    @Test
    fun `findTable rejects ordinary prose paragraphs`() {
        // 3 prose lines where word spaces are all standard (~4-5pt)
        val lines = (1..3).map { lineIdx ->
            val y = lineIdx * 20f
            val words = (1..6).map { wordIdx ->
                val left = 50f + (wordIdx - 1) * 45f
                makeWord("word$wordIdx", left, left + 40f, y = y)
            }
            makeLineWithWords(words, y = y)
        }

        val table = TableDetector.findTable(lines)
        assertNull("Ordinary prose must not be detected as table", table)
    }
}
