package com.markdrop.app.conversion.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanMarkdownOutputTest {

    @Test
    fun testOuterMarkdownWrapperIsStripped() {
        val input = """
            ```markdown
            # Title
            
            Paragraph text.
            ```
        """.trimIndent()

        val expected = """
            # Title
            
            Paragraph text.
        """.trimIndent()

        assertEquals(expected, cleanMarkdownOuterWrapper(input))
    }

    @Test
    fun testOuterGenericCodeFenceIsStripped() {
        val input = """
            ```
            # Title
            
            Paragraph text.
            ```
        """.trimIndent()

        val expected = """
            # Title
            
            Paragraph text.
        """.trimIndent()

        assertEquals(expected, cleanMarkdownOuterWrapper(input))
    }

    @Test
    fun testLegitimateLeadingCodeBlockNotStripped() {
        // A page that starts with a code snippet and is followed by body text
        val input = """
            ```kotlin
            fun main() {
                println("Hello, World!")
            }
            ```
            
            This code illustrates the main entry point for Kotlin applications.
        """.trimIndent()

        // Because the code block does not enclose the whole output, it must NOT be stripped!
        assertEquals(input, cleanMarkdownOuterWrapper(input))
    }

    @Test
    fun testUnwrappedMarkdownPreserved() {
        val input = "# Plain Header\n\nSome paragraph."
        assertEquals(input, cleanMarkdownOuterWrapper(input))
    }
}
