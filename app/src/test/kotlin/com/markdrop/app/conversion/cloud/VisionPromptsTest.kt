package com.markdrop.app.conversion.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPromptsTest {

    @Test
    fun `buildUserPrompt without previous tail produces standard prompt`() {
        val prompt = VisionPrompts.buildUserPrompt(pageNumber = 1, totalPages = 5, previousTail = null)
        assertEquals("Transcribe page 1 of 5.", prompt)
    }

    @Test
    fun `buildUserPrompt with previous tail includes continuity section`() {
        val tail = "This was the end of previous page."
        val prompt = VisionPrompts.buildUserPrompt(pageNumber = 2, totalPages = 5, previousTail = tail)
        
        assertTrue(prompt.startsWith("Transcribe page 2 of 5."))
        assertTrue(prompt.contains("For continuity only (do not repeat it), the previous page ended with:"))
        assertTrue(prompt.contains("<<<\n$tail\n>>>"))
    }

    @Test
    fun `buildUserPrompt trims tail longer than 400 characters`() {
        val longTail = "A".repeat(600)
        val prompt = VisionPrompts.buildUserPrompt(pageNumber = 3, totalPages = 5, previousTail = longTail)

        assertTrue(prompt.contains("A".repeat(400)))
        assertTrue(!prompt.contains("A".repeat(401)))
    }

    @Test
    fun `system prompt contains all required fidelity rules`() {
        val systemPrompt = VisionPrompts.SYSTEM_INSTRUCTION
        assertTrue("Expected GitHub-Flavored Markdown rule", systemPrompt.contains("GitHub-Flavored Markdown"))
        assertTrue("Expected LaTeX math rule", systemPrompt.contains("LaTeX"))
        assertTrue("Expected handwriting rule", systemPrompt.contains("Handwriting"))
        assertTrue("Expected table rule", systemPrompt.contains("pipe tables"))
        assertTrue("Expected blank page rule", systemPrompt.contains("<!-- blank page -->"))
    }
}
