package com.markdrop.app.conversion.assemble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblerTest {

    @Test
    fun `assemble empty list returns empty string`() {
        assertEquals("", Assembler.assemble(emptyList()))
        assertEquals("", Assembler.assemble(listOf("", "   ", "\n")))
    }

    @Test
    fun `assemble single page normalizes headings`() {
        val page = "### Section 1\n\nSome body text."
        val result = Assembler.assemble(listOf(page))
        assertEquals("# Section 1\n\nSome body text.", result)
    }

    @Test
    fun `assemble merges split table across pages without duplicate headers`() {
        val page1 = """
            # Results
            
            | Model | Precision | Recall |
            |---|---|---|
            | Baseline | 0.82 | 0.79 |
            | Variant A | 0.88 | 0.85 |
        """.trimIndent()

        val page2 = """
            | Model | Precision | Recall |
            |---|---|---|
            | Variant B | 0.91 | 0.89 |
            | Variant C | 0.94 | 0.92 |
            
            End of table analysis.
        """.trimIndent()

        val assembled = Assembler.assemble(listOf(page1, page2))
        
        // Should not have the page divider between merged tables
        assertTrue("Expected merged table without page break", !assembled.contains("\n---\n"))
        assertTrue("Expected Variant A followed by Variant B", assembled.contains("| Variant A | 0.88 | 0.85 |\n| Variant B | 0.91 | 0.89 |"))
        // Should only have one header separator row
        val separatorCount = assembled.lines().count { it.trim() == "|---|---|---|" }
        assertEquals(1, separatorCount)
    }

    @Test
    fun `assemble merges hyphenated split paragraph across pages`() {
        val page1 = "We investigated whether the experimental condi-"
        val page2 = "tions were reproducible across multiple trials."

        val assembled = Assembler.assemble(listOf(page1, page2))
        assertEquals("We investigated whether the experimental conditions were reproducible across multiple trials.", assembled)
    }

    @Test
    fun `assemble merges unpunctuated split paragraph across pages`() {
        val page1 = "We investigated whether the experimental conditions"
        val page2 = "were reproducible across multiple independent trials."

        val assembled = Assembler.assemble(listOf(page1, page2))
        assertEquals("We investigated whether the experimental conditions <!-- page: 2 --> were reproducible across multiple independent trials.", assembled)
    }

    @Test
    fun `assemble does not merge when page ends with sentence terminal punctuation`() {
        val page1 = "The trial concluded successfully with significant results."
        val page2 = "In section 2, we present secondary outcomes."

        val assembled = Assembler.assemble(listOf(page1, page2))
        assertTrue(assembled.contains("<!-- page: 2 -->"))
        assertTrue(assembled.contains(page1))
        assertTrue(assembled.contains(page2))
    }

    @Test
    fun `normalizeHeadings shifts levels when document starts at H2 or H3`() {
        val input = """
            ## Main Section
            Some text
            ### Subsection 1
            More text
            ### Subsection 2
        """.trimIndent()

        val expected = """
            # Main Section
            Some text
            ## Subsection 1
            More text
            ## Subsection 2
        """.trimIndent()

        assertEquals(expected, Assembler.normalizeHeadings(input))
    }

    @Test
    fun `normalizeHeadings prevents large skips in heading levels`() {
        val input = """
            # Top Level
            ### Subtitle without H2
        """.trimIndent()

        val expected = """
            # Top Level
            ## Subtitle without H2
        """.trimIndent()

        assertEquals(expected, Assembler.normalizeHeadings(input))
    }
}
