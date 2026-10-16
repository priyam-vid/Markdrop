package com.markdrop.app.conversion.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSelectionParserTest {

    @Test
    fun `parse returns all pages when input is null or blank`() {
        assertEquals(listOf(0, 1, 2), PageSelectionParser.parse(null, 3))
        assertEquals(listOf(0, 1, 2), PageSelectionParser.parse("", 3))
        assertEquals(listOf(0, 1, 2), PageSelectionParser.parse("   ", 3))
    }

    @Test
    fun `parse handles comma-separated list of individual pages`() {
        val result = PageSelectionParser.parse("1, 3, 5", 10)
        assertEquals(listOf(0, 2, 4), result)
    }

    @Test
    fun `parse handles page ranges`() {
        val result = PageSelectionParser.parse("2-5", 10)
        assertEquals(listOf(1, 2, 3, 4), result)
    }

    @Test
    fun `parse handles mixed ranges and single pages and clamps to bounds`() {
        val result = PageSelectionParser.parse("1-3, 5, 8-12", 9)
        // Clamped at totalPages = 9: 1,2,3 -> 0,1,2; 5 -> 4; 8-9 -> 7,8
        assertEquals(listOf(0, 1, 2, 4, 7, 8), result)
    }

    @Test
    fun `parse handles inverted ranges gracefully`() {
        val result = PageSelectionParser.parse("5-3", 10)
        assertEquals(listOf(2, 3, 4), result)
    }
}
