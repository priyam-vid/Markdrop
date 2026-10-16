package com.markdrop.app.conversion.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutToMarkdownTest {

    @Test
    fun `computeBodyModeSize finds statistical mode`() {
        val lines = listOf(
            LayoutLine(text = "Header", y = 10f, height = 24f, fontSize = 24f, left = 0f, right = 100f),
            LayoutLine(text = "Body 1", y = 40f, height = 12f, fontSize = 12f, left = 0f, right = 100f),
            LayoutLine(text = "Body 2", y = 60f, height = 12f, fontSize = 12f, left = 0f, right = 100f),
            LayoutLine(text = "Body 3", y = 80f, height = 12f, fontSize = 12f, left = 0f, right = 100f),
            LayoutLine(text = "Footer", y = 100f, height = 9f, fontSize = 9f, left = 0f, right = 100f)
        )

        val mode = LayoutToMarkdown.computeBodyModeSize(lines)
        assertEquals(12f, mode, 0.1f)
    }

    @Test
    fun `formatPage formats extended OCR bullet symbols as markdown list items`() {
        val bullets = listOf("•", "⁃", "◦", "▪", "●", "°", "©")
        val lines = bullets.mapIndexed { i, bullet ->
            LayoutLine(
                text = "$bullet Item $i",
                y = i * 20f,
                height = 14f,
                fontSize = 14f,
                left = 20f,
                right = 200f
            )
        }

        val page = LayoutPage(pageIndex = 0, width = 600f, height = 800f, lines = lines)
        val markdown = LayoutToMarkdown.formatPage(page)

        for (i in bullets.indices) {
            assertTrue("Expected item $i in list, got:\n$markdown", markdown.contains("- Item $i"))
        }
    }

    @Test
    fun `formatPage handles lowercase dehyphenation correctly`() {
        val lines = listOf(
            LayoutLine(text = "This is an inter-", y = 20f, height = 14f, fontSize = 14f, left = 20f, right = 200f),
            LayoutLine(text = "action with the user.", y = 38f, height = 14f, fontSize = 14f, left = 20f, right = 200f)
        )

        val page = LayoutPage(pageIndex = 0, width = 600f, height = 800f, lines = lines)
        val markdown = LayoutToMarkdown.formatPage(page)

        assertTrue("Expected merged interaction, got: $markdown", markdown.contains("interaction with the user."))
    }

    @Test
    fun `formatPage preserves uppercase hyphens without stripping`() {
        val lines = listOf(
            LayoutLine(text = "This is Anti-", y = 20f, height = 14f, fontSize = 14f, left = 20f, right = 200f),
            LayoutLine(text = "Gravity assistant.", y = 38f, height = 14f, fontSize = 14f, left = 20f, right = 200f)
        )

        val page = LayoutPage(pageIndex = 0, width = 600f, height = 800f, lines = lines)
        val markdown = LayoutToMarkdown.formatPage(page)

        assertTrue("Expected hyphen preserved, got: $markdown", markdown.contains("Anti- Gravity assistant."))
    }

    @Test
    fun `formatPage inserts low OCR confidence alert when average confidence is below threshold`() {
        val lines = listOf(
            LayoutLine(text = "Hard to read text line", y = 20f, height = 14f, fontSize = 14f, left = 20f, right = 200f)
        )

        val page = LayoutPage(pageIndex = 0, width = 600f, height = 800f, lines = lines, averageConfidence = 0.52f)
        val markdown = LayoutToMarkdown.formatPage(page)

        assertTrue("Expected low OCR warning alert", markdown.contains("> [!WARNING] Low OCR confidence detected"))
        assertTrue("Expected 52% mentioned in warning", markdown.contains("52%"))
    }

    @Test
    fun `formatPage formats heading based on font size ratio`() {
        val lines = listOf(
            LayoutLine(text = "Main Title", y = 20f, height = 24f, fontSize = 24f, left = 20f, right = 200f),
            LayoutLine(text = "First paragraph line providing body context.", y = 60f, height = 14f, fontSize = 14f, left = 20f, right = 400f),
            LayoutLine(text = "Second paragraph line providing body context.", y = 80f, height = 14f, fontSize = 14f, left = 20f, right = 400f)
        )

        val page = LayoutPage(pageIndex = 0, width = 600f, height = 800f, lines = lines)
        val markdown = LayoutToMarkdown.formatPage(page)

        assertTrue("Expected # Main Title, got: $markdown", markdown.contains("# Main Title"))
    }
}
