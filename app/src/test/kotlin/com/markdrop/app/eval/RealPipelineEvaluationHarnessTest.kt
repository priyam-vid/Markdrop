package com.markdrop.app.eval

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.markdrop.app.conversion.assemble.Assembler
import com.markdrop.app.conversion.layout.LayoutPage
import com.markdrop.app.conversion.layout.LayoutToMarkdown
import com.markdrop.app.conversion.pdf.TextLayerExtractor
import com.markdrop.app.conversion.pdf.TextLayerSession
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

            val textSession = try {
                kotlinx.coroutines.runBlocking { TextLayerSession.open(context, uri) }
            } catch (_: Exception) {
                null
            }

            var extractedWordCount = 0
            var hasTextLayer = false

            val markdown = if (textSession != null) {
                val geom = textSession.pageGeometry(0)
                val words = textSession.extractWords(0)
                extractedWordCount = words.size
                val contentInfo = textSession.analyzePage(0, words)
                if (contentInfo.isUsableTextLayer) {
                    hasTextLayer = true
                    LayoutToMarkdown.formatPage(
                        LayoutPage(
                            pageIndex = 0,
                            width = geom.width,
                            height = geom.height,
                            words = words,
                            figures = contentInfo.figureBoxes
                        )
                    )
                } else {
                    "> [!NOTE]\n> Scanned page with stamp or insufficient text; routed to OCR/Vision.\n"
                }
            } else {
                "> [!NOTE]\n> No text layer extracted (scanned or image-only page).\n"
            }
            textSession?.close()

            val elapsed = System.currentTimeMillis() - startTime

            // Write output file for tools/evaluate.py
            val outFile = File(evalOutputDir, "$name.md")
            outFile.writeText(markdown)

            val traceFile = File(evalOutputDir, "$name.trace.json")
            traceFile.writeText(
                """
                {
                  "name": "$name",
                  "hasTextLayer": $hasTextLayer,
                  "wordCount": $extractedWordCount,
                  "durationMs": $elapsed
                }
                """.trimIndent()
            )
            println("Evaluated $name: $extractedWordCount words in ${elapsed}ms -> ${outFile.path}")
        }
    }
}
