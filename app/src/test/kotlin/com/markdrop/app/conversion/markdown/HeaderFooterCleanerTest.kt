package com.markdrop.app.conversion.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderFooterCleanerTest {

    @Test
    fun testWatermarkKeywordsInBodyArePreserved() {
        // Body text containing words that old buggy regex stripped
        val page = """
            # Quarterly Financial Report
            
            This report contains preliminary draft findings from the committee.
            All confidential discussions must remain within the working group.
            Please do not distribute this document outside of the team.
            
            Section 2: Market Analysis
            The copyright © of all cited sources is acknowledged.
            The results are scanned with high precision sensors.
            
            Conclusion and Next Steps.
            Approved by the board.
        """.trimIndent()

        val cleaned = HeaderFooterCleaner.cleanPages(listOf(page)).first()
        assertTrue("Draft keyword in body must be preserved", cleaned.contains("preliminary draft findings"))
        assertTrue("Confidential keyword in body must be preserved", cleaned.contains("All confidential discussions"))
        assertTrue("Do not distribute in body must be preserved", cleaned.contains("do not distribute"))
        assertTrue("Copyright in body must be preserved", cleaned.contains("copyright ©"))
    }

    @Test
    fun testShortPagesAreNeverStripped() {
        // Short page with < 10 lines (e.g. standalone answers, table fragments, bare numbers)
        val shortPage = """
            1
            Yes
            2
            No
            3
            Maybe
        """.trimIndent()

        val cleaned = HeaderFooterCleaner.cleanPages(listOf(shortPage)).first()
        assertEquals("Short pages must not have lines stripped", shortPage, cleaned)
    }

    @Test
    fun testRecurringHeadersAndFootersStrippedOnMultiPageDocs() {
        val recurringHeader = "ACME Corporation — Internal Document"
        val recurringFooter = "Confidential Report | Archive Copy"
        val firstLines = listOf(
            "Revenue increased by 15% year-over-year.",
            "Operating expenses decreased after restructuring.",
            "Capital investments expanded globally.",
            "Cash flow from operations remained steady.",
            "Outlook for the upcoming year is optimistic."
        )
        val lastLines = listOf(
            "End of section 1 data table.",
            "All regional offices confirmed receipts.",
            "Independent audit underway for Q3.",
            "Final projections approved by committee.",
            "Closing remarks by the chairman."
        )

        val pages = (0 until 5).map { idx ->
            """
            $recurringHeader
            
            ${firstLines[idx]}
            Line 2 data block
            Line 3 data block
            Line 4 data block
            Line 5 data block
            Line 6 data block
            Line 7 data block
            Line 8 data block
            Line 9 data block
            ${lastLines[idx]}
            
            $recurringFooter
            """.trimIndent()
        }

        val cleaned = HeaderFooterCleaner.cleanPages(pages)

        cleaned.forEachIndexed { i, p ->
            assertFalse("Recurring header must be stripped on page ${i + 1}", p.contains(recurringHeader))
            assertFalse("Recurring footer must be stripped on page ${i + 1}", p.contains(recurringFooter))
            assertTrue("Body start must be preserved on page ${i + 1}", p.contains(firstLines[i]))
            assertTrue("Body end must be preserved on page ${i + 1}", p.contains(lastLines[i]))
        }
    }

    @Test
    fun testRecurringRuleDisabledOnOneAndTwoPageDocs() {
        val header = "Annual Summary Report"
        val page1 = """
            $header
            Line 1
            Line 2
            Line 3
            Line 4
            Line 5
            Line 6
            Line 7
            Line 8
            Line 9
            Line 10
        """.trimIndent()
        val page2 = """
            $header
            Line 1b
            Line 2b
            Line 3b
            Line 4b
            Line 5b
            Line 6b
            Line 7b
            Line 8b
            Line 9b
            Line 10b
        """.trimIndent()

        val cleaned = HeaderFooterCleaner.cleanPages(listOf(page1, page2))
        assertTrue("Recurring rule must not strip on 2-page documents", cleaned[0].contains(header))
        assertTrue("Recurring rule must not strip on 2-page documents", cleaned[1].contains(header))
    }

    @Test
    fun testPositionalPageNumbersStrippedInCandidateBand() {
        val page = """
            Line 1 of body
            Line 2 of body
            Line 3 of body
            Line 4 of body
            Line 5 of body
            Line 6 of body
            Line 7 of body
            Line 8 of body
            Line 9 of body
            Line 10 of body
            
            Page 1 of 12
        """.trimIndent()

        val cleaned = HeaderFooterCleaner.cleanPages(listOf(page)).first()
        assertFalse("Page number in footer band must be stripped", cleaned.contains("Page 1 of 12"))
        assertTrue("Body text must remain", cleaned.contains("Line 1 of body"))
        assertTrue("Body text must remain", cleaned.contains("Line 10 of body"))
    }
}
