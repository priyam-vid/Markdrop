package com.markdrop.app.conversion.markdown

import android.graphics.Rect
import com.markdrop.app.conversion.ocr.OcrBlock
import com.markdrop.app.conversion.ocr.OcrElement
import com.markdrop.app.conversion.ocr.OcrLine
import com.markdrop.app.conversion.ocr.OcrPageResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicMarkdownFormatterTest {

    @Test
    fun testHeadingDetection() {
        // Page with baseline body text height 20, and a large heading of height 40
        val headingLine = OcrLine(
            text = "Executive Summary",
            boundingBox = Rect(10, 10, 200, 50),
            lineHeight = 40,
            elements = emptyList()
        )
        val headingBlock = OcrBlock(
            text = "Executive Summary",
            boundingBox = Rect(10, 10, 200, 50),
            lines = listOf(headingLine)
        )

        val bodyLine = OcrLine(
            text = "This is body text describing the document contents.",
            boundingBox = Rect(10, 60, 400, 80),
            lineHeight = 20,
            elements = emptyList()
        )
        val bodyLine2 = OcrLine(
            text = "Second line of body text in the document.",
            boundingBox = Rect(10, 85, 400, 105),
            lineHeight = 20,
            elements = emptyList()
        )
        val bodyBlock = OcrBlock(
            text = "This is body text describing the document contents.\nSecond line of body text in the document.",
            boundingBox = Rect(10, 60, 400, 105),
            lines = listOf(bodyLine, bodyLine2)
        )

        val page = OcrPageResult(
            pageIndex = 0,
            width = 500,
            height = 800,
            blocks = listOf(headingBlock, bodyBlock)
        )

        val markdown = HeuristicMarkdownFormatter.formatPage(page)
        assertTrue("Expected H1 heading", markdown.contains("# Executive Summary"))
        assertTrue("Expected body text", markdown.contains("This is body text describing the document contents."))
    }

    @Test
    fun testBulletListDetection() {
        val bullet1 = OcrLine("• First item", Rect(10, 10, 100, 30), 20, emptyList())
        val bullet2 = OcrLine("• Second item", Rect(10, 35, 100, 55), 20, emptyList())
        val block = OcrBlock(
            text = "• First item\n• Second item",
            boundingBox = Rect(10, 10, 100, 55),
            lines = listOf(bullet1, bullet2)
        )

        val page = OcrPageResult(0, 500, 500, listOf(block))
        val markdown = HeuristicMarkdownFormatter.formatPage(page)

        assertTrue(markdown.contains("- First item"))
        assertTrue(markdown.contains("- Second item"))
    }

    @Test
    fun testNumberedListDetection() {
        val num1 = OcrLine("1. Step one", Rect(10, 10, 100, 30), 20, emptyList())
        val num2 = OcrLine("2. Step two", Rect(10, 35, 100, 55), 20, emptyList())
        val block = OcrBlock(
            text = "1. Step one\n2. Step two",
            boundingBox = Rect(10, 10, 100, 55),
            lines = listOf(num1, num2)
        )

        val page = OcrPageResult(0, 500, 500, listOf(block))
        val markdown = HeuristicMarkdownFormatter.formatPage(page)

        assertTrue(markdown.contains("1. Step one"))
        assertTrue(markdown.contains("2. Step two"))
    }

    @Test
    fun testMultiPageSeparator() {
        val page1 = OcrPageResult(
            pageIndex = 0,
            width = 500,
            height = 500,
            blocks = listOf(
                OcrBlock(
                    "Page One Content",
                    Rect(10, 10, 200, 30),
                    listOf(OcrLine("Page One Content", Rect(10, 10, 200, 30), 20, emptyList()))
                )
            )
        )
        val page2 = OcrPageResult(
            pageIndex = 1,
            width = 500,
            height = 500,
            blocks = listOf(
                OcrBlock(
                    "Page Two Content",
                    Rect(10, 10, 200, 30),
                    listOf(OcrLine("Page Two Content", Rect(10, 10, 200, 30), 20, emptyList()))
                )
            )
        )

        val doc = HeuristicMarkdownFormatter.formatDocument(listOf(page1, page2))
        assertTrue(doc.contains("Page One Content"))
        assertTrue(doc.contains("---"))
        assertTrue(doc.contains("Page Two Content"))
    }

    @Test
    fun testParagraphWithElementsNotTurnedIntoTable() {
        // Multi-line paragraph where each line contains multiple word elements
        val line1 = OcrLine(
            text = "This is the first line of a real paragraph",
            boundingBox = Rect(10, 10, 350, 30),
            lineHeight = 20,
            elements = listOf(
                OcrElement("This", Rect(10, 10, 45, 30), 0.99f),
                OcrElement("is", Rect(50, 10, 65, 30), 0.99f),
                OcrElement("the", Rect(70, 10, 95, 30), 0.99f),
                OcrElement("first", Rect(100, 10, 135, 30), 0.99f),
                OcrElement("line", Rect(140, 10, 170, 30), 0.99f),
                OcrElement("of", Rect(175, 10, 190, 30), 0.99f),
                OcrElement("a", Rect(195, 10, 205, 30), 0.99f),
                OcrElement("real", Rect(210, 10, 240, 30), 0.99f),
                OcrElement("paragraph", Rect(245, 10, 350, 30), 0.99f),
            )
        )
        val line2 = OcrLine(
            text = "and this is the continuing second line.",
            boundingBox = Rect(10, 35, 320, 55),
            lineHeight = 20,
            elements = listOf(
                OcrElement("and", Rect(10, 35, 40, 55), 0.99f),
                OcrElement("this", Rect(45, 35, 75, 55), 0.99f),
                OcrElement("is", Rect(80, 35, 95, 55), 0.99f),
                OcrElement("the", Rect(100, 35, 125, 55), 0.99f),
                OcrElement("continuing", Rect(130, 35, 220, 55), 0.99f),
                OcrElement("second", Rect(225, 35, 275, 55), 0.99f),
                OcrElement("line.", Rect(280, 35, 320, 55), 0.99f),
            )
        )
        val block = OcrBlock(
            text = "This is the first line of a real paragraph\nand this is the continuing second line.",
            boundingBox = Rect(10, 10, 350, 55),
            lines = listOf(line1, line2)
        )

        val page = OcrPageResult(0, 500, 500, listOf(block))
        val markdown = HeuristicMarkdownFormatter.formatPage(page)

        // Must NOT contain pipe characters from false table detection
        assertTrue("Should not format paragraph as table", !markdown.contains("|"))
        assertTrue("Should contain smooth continuous text", markdown.contains("This is the first line of a real paragraph and this is the continuing second line."))
    }

    @Test
    fun testDehyphenationAcrossLines() {
        val line1 = OcrLine("The appli-", Rect(10, 10, 100, 30), 20, emptyList())
        val line2 = OcrLine("cation was successfully built.", Rect(10, 35, 250, 55), 20, emptyList())
        val block = OcrBlock(
            text = "The appli-\ncation was successfully built.",
            boundingBox = Rect(10, 10, 250, 55),
            lines = listOf(line1, line2)
        )
        val page = OcrPageResult(0, 500, 500, listOf(block))
        val markdown = HeuristicMarkdownFormatter.formatPage(page)

        assertTrue(markdown.contains("The application was successfully built."))
    }

    @Test
    fun testDelimitedTableFormatting() {
        val line1 = OcrLine("Item | Qty | Price", Rect(10, 10, 200, 30), 20, emptyList())
        val line2 = OcrLine("Apple | 5 | $2.50", Rect(10, 35, 200, 55), 20, emptyList())
        val line3 = OcrLine("Banana | 3 | $1.20", Rect(10, 60, 200, 80), 20, emptyList())
        val block = OcrBlock(
            text = "Item | Qty | Price\nApple | 5 | $2.50\nBanana | 3 | $1.20",
            boundingBox = Rect(10, 10, 200, 80),
            lines = listOf(line1, line2, line3)
        )
        val page = OcrPageResult(0, 500, 500, listOf(block))
        val markdown = HeuristicMarkdownFormatter.formatPage(page)

        assertTrue(markdown.contains("| Item | Qty | Price |"))
        assertTrue(markdown.contains("| --- | --- | --- |"))
        assertTrue(markdown.contains("| Apple | 5 | $2.50 |"))
    }
}
