package com.markdrop.app.conversion.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsFromGlyphsTest {

    @Test
    fun `group splits words separated by explicit space glyphs`() {
        val glyphs = listOf(
            Glyph("H", 10f, 100f, 8f, 12f, 12f),
            Glyph("i", 18f, 100f, 4f, 12f, 12f),
            Glyph(" ", 22f, 100f, 4f, 12f, 12f),
            Glyph("Y", 26f, 100f, 8f, 12f, 12f),
            Glyph("o", 34f, 100f, 6f, 12f, 12f),
            Glyph("u", 40f, 100f, 6f, 12f, 12f)
        )

        val words = WordsFromGlyphs.group(glyphs)
        assertEquals(2, words.size)
        assertEquals("Hi", words[0].text)
        assertEquals("You", words[1].text)
    }

    @Test
    fun `group splits words separated only by kerning gaps without space glyphs`() {
        // Kerning gap of 4.5f between "Neural" and "Network" (fontSize 12f, maxIntraWordGap ~ 2.6f)
        val glyphs = listOf(
            Glyph("N", 10f, 100f, 8f, 12f, 12f),
            Glyph("e", 18f, 100f, 6f, 12f, 12f),
            Glyph("u", 24f, 100f, 6f, 12f, 12f),
            Glyph("r", 30f, 100f, 5f, 12f, 12f),
            Glyph("a", 35f, 100f, 6f, 12f, 12f),
            Glyph("l", 41f, 100f, 3f, 12f, 12f),
            // Kerning offset jumps from x=44 to x=49 (gap = 5.0f)
            Glyph("N", 49f, 100f, 8f, 12f, 12f),
            Glyph("e", 57f, 100f, 6f, 12f, 12f),
            Glyph("t", 63f, 100f, 4f, 12f, 12f)
        )

        val words = WordsFromGlyphs.group(glyphs)
        assertEquals(2, words.size)
        assertEquals("Neural", words[0].text)
        assertEquals("Net", words[1].text)
    }

    @Test
    fun `group normalizes ligatures correctly`() {
        val glyphs = listOf(
            Glyph("o", 10f, 100f, 6f, 12f, 12f),
            Glyph("\uFB03", 16f, 100f, 12f, 12f, 12f), // ffi ligature
            Glyph("c", 28f, 100f, 6f, 12f, 12f),
            Glyph("e", 34f, 100f, 6f, 12f, 12f)
        )

        val words = WordsFromGlyphs.group(glyphs)
        assertEquals(1, words.size)
        assertEquals("office", words[0].text)
    }

    @Test
    fun `group captures word level bold and italic styles`() {
        val glyphs = listOf(
            Glyph("B", 10f, 100f, 8f, 12f, 12f, bold = true),
            Glyph("o", 18f, 100f, 6f, 12f, 12f, bold = true),
            Glyph("l", 24f, 100f, 4f, 12f, 12f, bold = true),
            Glyph("d", 28f, 100f, 6f, 12f, 12f, bold = true),
            Glyph(" ", 34f, 100f, 4f, 12f, 12f),
            Glyph("P", 38f, 100f, 8f, 12f, 12f, bold = false),
            Glyph("l", 46f, 100f, 4f, 12f, 12f, bold = false),
            Glyph("a", 50f, 100f, 6f, 12f, 12f, bold = false),
            Glyph("i", 56f, 100f, 4f, 12f, 12f, bold = false),
            Glyph("n", 60f, 100f, 6f, 12f, 12f, bold = false)
        )

        val words = WordsFromGlyphs.group(glyphs)
        assertEquals(2, words.size)
        assertEquals("Bold", words[0].text)
        assertTrue(words[0].bold)
        assertEquals("Plain", words[1].text)
        assertTrue(!words[1].bold)
    }
}
