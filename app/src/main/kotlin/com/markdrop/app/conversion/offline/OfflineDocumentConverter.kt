package com.markdrop.app.conversion.offline

import com.markdrop.app.conversion.assemble.Assembler
import com.markdrop.app.conversion.layout.LayoutPage
import com.markdrop.app.conversion.layout.LayoutToMarkdown
import com.markdrop.app.conversion.markdown.HeaderFooterCleaner

data class PageInput(
    val pageNumber: Int, // 1-based original page number
    val layoutPage: LayoutPage
)

data class OfflineResult(
    val markdown: String,
    val perPage: List<String>
)

/**
 * Pure Kotlin document converter for offline workflows (text layer and OCR).
 * Applies running-header/footer filtering, layout-to-markdown reconstruction, and cross-page assembly.
 * Free of Android dependencies to ensure identical execution between the Android application
 * and the JVM evaluation test harness.
 */
class OfflineDocumentConverter(
    private val stripHeaderFooter: Boolean = true
) {
    fun convert(pages: List<PageInput>): OfflineResult {
        if (pages.isEmpty()) return OfflineResult("", emptyList())

        // 1. Render layout to markdown for each page
        val perPageMarkdown = pages.map { pageInput ->
            LayoutToMarkdown.formatPage(pageInput.layoutPage)
        }

        // 2. Running header/footer cleaning
        val cleanedPages = if (stripHeaderFooter && pages.size >= 2) {
            HeaderFooterCleaner.cleanPages(perPageMarkdown)
        } else {
            perPageMarkdown
        }

        // 3. Cross-page assembly
        val assembledMarkdown = if (pages.size == 1) {
            cleanedPages[0]
        } else {
            Assembler.assemble(cleanedPages)
        }

        return OfflineResult(
            markdown = assembledMarkdown,
            perPage = cleanedPages
        )
    }
}
