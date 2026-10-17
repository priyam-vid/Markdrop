package com.markdrop.app.eval

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markdrop.app.conversion.layout.LayoutToMarkdown
import com.markdrop.app.conversion.ocr.MlKitOcrEngine
import com.markdrop.app.conversion.offline.OfflineDocumentConverter
import com.markdrop.app.conversion.offline.PageInput
import com.markdrop.app.conversion.pdf.PdfPageRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * On-device instrumented evaluation test for raster/scanned PDF documents.
 * Executes on connected physical devices or emulators with Google Play Services / ML Kit Text Recognition.
 *
 * Evaluates the 3 scanned corpus documents:
 * 1. scan_image_only.pdf (single-page raster scan)
 * 2. scan_image_with_stamp.pdf (raster scan with skewed Bates stamp)
 * 3. scan_raster_multipage.pdf (3-page skewed multi-page scanned log)
 */
@RunWith(AndroidJUnit4::class)
class DeviceEvaluationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun evaluateScanImageOnly() = runBlocking {
        evaluateScannedDocument(
            pdfRelativePath = "scan_image_only.pdf",
            goldenRelativePath = "golden/scan_image_only.page1.md",
            maxWer = 0.15,
            tier = "page"
        )
    }

    @Test
    fun evaluateScanImageWithStamp() = runBlocking {
        evaluateScannedDocument(
            pdfRelativePath = "scan_image_with_stamp.pdf",
            goldenRelativePath = "golden/scan_image_with_stamp.page1.md",
            maxWer = 0.15,
            tier = "page"
        )
    }

    @Test
    fun evaluateScanRasterMultipage() = runBlocking {
        evaluateScannedDocument(
            pdfRelativePath = "scan_raster_multipage.pdf",
            goldenRelativePath = "golden/scan_raster_multipage.doc.md",
            maxWer = 0.15,
            tier = "document"
        )
    }

    private suspend fun evaluateScannedDocument(
        pdfRelativePath: String,
        goldenRelativePath: String,
        maxWer: Double,
        tier: String
    ) {
        val corpusDir = locateCorpusDir()
        val pdfFile = File(corpusDir, pdfRelativePath)
        assertTrue("Corpus PDF must exist: ${pdfFile.absolutePath}", pdfFile.exists())

        val goldenFile = File(corpusDir, goldenRelativePath)
        assertTrue("Golden markdown must exist: ${goldenFile.absolutePath}", goldenFile.exists())
        val goldenContent = goldenFile.readText().replace("\r\n", "\n")

        val pdfUri = Uri.fromFile(pdfFile)
        val ocrEngine = MlKitOcrEngine()
        val renderer = PdfPageRenderer.open(context, pdfUri)
        assertNotNull("PdfPageRenderer must open scanned PDF", renderer)

        val pageInputs = mutableListOf<PageInput>()

        renderer!!.use { pdfRenderer ->
            ocrEngine.use { engine ->
                for (pageIdx in 0 until pdfRenderer.pageCount) {
                    val bitmap = pdfRenderer.renderPageToBitmap(pageIdx, targetDpi = 200, isOcrMode = true)
                    assertNotNull("Rendered bitmap must not be null", bitmap)
                    val ocrResult = engine.processImage(bitmap!!, pageIdx)
                    val layoutPage = LayoutToMarkdown.fromOcrPageResult(ocrResult)
                    pageInputs.add(PageInput(pageNumber = pageIdx + 1, layoutPage = layoutPage))
                }
            }
        }

        val converter = OfflineDocumentConverter(stripHeaderFooter = true)
        val outputMarkdown = if (tier == "page") {
            converter.convert(listOf(pageInputs[0])).perPage.first()
        } else {
            converter.convert(pageInputs).markdown
        }

        val wer = computeWer(goldenContent, outputMarkdown)
        println("DeviceEvaluationTest: $pdfRelativePath | Tier: $tier | WER: ${String.format("%.2f%%", wer * 100)}")
        assertTrue("WER ($wer) must be <= $maxWer for $pdfRelativePath", wer <= maxWer)
    }

    private fun locateCorpusDir(): File {
        val candidates = listOf(
            File("testdata/corpus"),
            File("../testdata/corpus"),
            File("../../testdata/corpus"),
            File(context.filesDir, "testdata/corpus")
        )
        for (c in candidates) {
            if (c.exists() && c.isDirectory) return c
        }
        return File("testdata/corpus")
    }

    private fun tokenize(text: String): List<String> {
        val cleaned = text.replace(Regex("""[*_#`~|>]"""), " ")
        return Regex("""\b\w+\b""").findAll(cleaned.lowercase()).map { it.value }.toList()
    }

    private fun computeWer(ref: String, hyp: String): Double {
        val refWords = tokenize(ref)
        val hypWords = tokenize(hyp)
        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0 else 1.0

        val d = Array(refWords.size + 1) { IntArray(hypWords.size + 1) }
        for (i in 0..refWords.size) d[i][0] = i
        for (j in 0..hypWords.size) d[0][j] = j

        for (i in 1..refWords.size) {
            for (j in 1..hypWords.size) {
                val cost = if (refWords[i - 1] == hypWords[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
            }
        }
        return d[refWords.size][hypWords.size].toDouble() / refWords.size.toDouble()
    }
}
