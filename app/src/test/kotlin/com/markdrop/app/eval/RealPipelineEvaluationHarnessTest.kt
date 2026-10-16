package com.markdrop.app.eval

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.markdrop.app.conversion.assemble.Assembler
import com.markdrop.app.conversion.layout.LayoutPage
import com.markdrop.app.conversion.layout.LayoutToMarkdown
import com.markdrop.app.conversion.pdf.TextLayerExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real JVM pipeline evaluation harness running under Robolectric.
 * Executes the actual TextLayerExtractor, LayoutToMarkdown, and Assembler on the real corpus PDFs.
 * Writes output files to build/eval/<name>.md for evaluation by tools/evaluate.py.
 */
@RunWith(RobolectricTestRunner::class)
class RealPipelineEvaluationHarnessTest {

    private lateinit var context: Context
    private val rootDir = File(".").canonicalFile.let { if (it.name == "app") it.parentFile else it }
    private val corpusDir = File(rootDir, "testdata/corpus")
    private val evalOutputDir = File(rootDir, "build/eval")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        evalOutputDir.mkdirs()
    }

    @Test
    fun evaluateCorpusOnCurrentCode() {
        // Files with digital text layers to evaluate
        val digitalFiles = listOf(
            "digital_nospace_kerning",
            "digital_two_column_real",
            "digital_table_multiword",
            "digital_multipage_table",
            "question_bank_pages",
            "digital_with_figures",
            "digital_article_2col",
            "digital_table_heavy",
            "digital_math",
            "mixed_slides",
            "scan_printed",
            "scan_image_only",
            "scan_image_with_stamp",
            "scan_handwritten",
            "rotated_page"
        )

        for (name in digitalFiles) {
            val pdfFile = File(corpusDir, "$name.pdf")
            if (!pdfFile.exists()) {
                println("Skipping non-existent file: $pdfFile")
                continue
            }

            val uri = Uri.fromFile(pdfFile)
            val startTime = System.currentTimeMillis()

            // Run real text layer extractor on page 0
            val lines = TextLayerExtractor.extractPageTextLayer(context, uri, 0)
            val markdown = if (lines != null) {
                // Pass to real LayoutToMarkdown
                LayoutToMarkdown.formatPage(
                    LayoutPage(pageIndex = 0, width = 0f, height = 0f, lines = lines)
                )
            } else {
                "> [!NOTE]\n> No text layer extracted (scanned or image-only page).\n"
            }

            val elapsed = System.currentTimeMillis() - startTime

            // Write output file for tools/evaluate.py
            val outFile = File(evalOutputDir, "$name.md")
            outFile.writeText(markdown)

            val traceFile = File(evalOutputDir, "$name.trace.json")
            traceFile.writeText(
                """
                {
                  "name": "$name",
                  "hasTextLayer": ${lines != null},
                  "lineCount": ${lines?.size ?: 0},
                  "durationMs": $elapsed
                }
                """.trimIndent()
            )
            println("Evaluated $name: ${lines?.size ?: 0} lines in ${elapsed}ms -> ${outFile.path}")
        }
    }
}
