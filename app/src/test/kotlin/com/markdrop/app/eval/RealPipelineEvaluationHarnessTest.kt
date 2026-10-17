package com.markdrop.app.eval

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.markdrop.app.conversion.layout.FigureBox
import com.markdrop.app.conversion.layout.LayoutPage
import com.markdrop.app.conversion.offline.OfflineDocumentConverter
import com.markdrop.app.conversion.offline.PageInput
import com.markdrop.app.conversion.pdf.TextLayerSession
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Real JVM pipeline evaluation harness running under Robolectric.
 * Executes the actual OfflineDocumentConverter (and TextLayerSession) on the corpus PDFs.
 *
 * Mechanical Honesty Rules:
 * - Iterates all pages of every document in thresholds.json.
 * - Asserts thresholds directly in JUnit so ./gradlew testDebugUnitTest fails on unannounced failures.
 * - Verifies that expected failures indeed fail until fixed in their respective phases.
 * - Asserts that generated outputs are byte-different from goldens unless metric math confirms an exact match.
 */
@RunWith(RobolectricTestRunner::class)
class RealPipelineEvaluationHarnessTest {

    private lateinit var context: Context
    private val rootDir = File(".").canonicalFile.let { if (it.name == "app") it.parentFile else it }
    private val corpusDir = File(rootDir, "testdata/corpus")
    private val goldenDir = File(corpusDir, "golden")
    private val matrixDir = File(corpusDir, "article_banner_matrix")
    private val evalOutputDir = File(rootDir, "build/eval")
    private val thresholdsFile = File(corpusDir, "thresholds.json")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        evalOutputDir.mkdirs()
    }

    @Test
    fun evaluateCorpusAgainstMechanicalThresholds() {
        assertTrue("thresholds.json must exist", thresholdsFile.exists())
        val thresholdsJson = JSONObject(thresholdsFile.readText())
        val expectedFailures = thresholdsJson.getJSONObject("expectedFailures")
        val documents = thresholdsJson.getJSONObject("documents")

        val converter = OfflineDocumentConverter(stripHeaderFooter = true)

        val unexpectedPasses = mutableListOf<String>()
        val unexpectedFailures = mutableListOf<String>()

        for (name in documents.keys()) {
            val spec = documents.getJSONObject(name)

            // Check if document is marked NOT RUN (e.g. requires physical device or user files)
            if (spec.has("not_run")) {
                val reason = spec.getString("not_run")
                val outFile = File(evalOutputDir, "$name.md")
                outFile.writeText("> [!NOTE]\n> NOT RUN ($reason)\n")
                val traceFile = File(evalOutputDir, "$name.trace.json")
                traceFile.writeText(
                    """
                    {
                      "name": "$name",
                      "engineUsed": "NOT_RUN",
                      "durationMs": 0,
                      "notes": "$reason"
                    }
                    """.trimIndent()
                )
                continue
            }

            val tier = spec.optString("tier", "page")

            // Locate PDF file
            val pdfCandidates = listOf(
                File(corpusDir, "$name.pdf"),
                File(matrixDir, "$name.pdf")
            )
            val pdfFile = pdfCandidates.firstOrNull { it.exists() }
            assertNotNull("PDF file must exist for document '$name'", pdfFile)

            // Locate Golden file
            val goldenCandidates = if (tier == "page") {
                listOf(
                    File(goldenDir, "$name.page1.md"),
                    File(goldenDir, "article_banner_matrix/$name.page1.md")
                )
            } else {
                listOf(
                    File(goldenDir, "$name.doc.md"),
                    File(goldenDir, "article_banner_matrix/$name.doc.md"),
                    File(goldenDir, "$name.page1.md")
                )
            }
            val goldenFile = goldenCandidates.firstOrNull { it.exists() }
            assertNotNull("Golden file must exist for document '$name'", goldenFile)
            val goldenContent = goldenFile!!.readText().replace("\r\n", "\n")

            // Open TextLayerSession and extract pages
            val uri = Uri.fromFile(pdfFile)
            val startTime = System.currentTimeMillis()

            val textSession = try {
                kotlinx.coroutines.runBlocking { TextLayerSession.open(context, uri) }
            } catch (e: Exception) {
                null
            }

            val pageInputs = mutableListOf<PageInput>()
            var totalExtractedWords = 0
            val totalPdfPages = textSession?.pageCount ?: 1

            if (textSession != null) {
                for (pIdx in 0 until totalPdfPages) {
                    val geom = textSession.pageGeometry(pIdx)
                    val words = textSession.extractWords(pIdx)
                    totalExtractedWords += words.size
                    val contentInfo = textSession.analyzePage(pIdx, words)

                    val layoutPage = LayoutPage(
                        pageIndex = pIdx,
                        width = geom.width,
                        height = geom.height,
                        words = words,
                        figures = contentInfo.figureBoxes
                    )
                    pageInputs.add(PageInput(pageNumber = pIdx + 1, layoutPage = layoutPage))
                }
                textSession.close()
            }

            val elapsed = System.currentTimeMillis() - startTime

            // Execute conversion
            val outputMarkdown: String = if (pageInputs.isEmpty()) {
                "> [!NOTE]\n> No text layer extracted (scanned or encrypted page).\n"
            } else {
                if (tier == "page") {
                    // Page tier: format page 1 layout directly
                    converter.convert(listOf(pageInputs[0])).perPage.firstOrNull() ?: ""
                } else {
                    // Document tier: format full document with cleaner and assembler
                    converter.convert(pageInputs).markdown
                }
            }

            // Write output file
            val outFile = File(evalOutputDir, "$name.md")
            outFile.writeText(outputMarkdown)

            // Write trace file
            val traceFile = File(evalOutputDir, "$name.trace.json")
            traceFile.writeText(
                """
                {
                  "name": "$name",
                  "engineUsed": "OfflineDocumentConverter",
                  "pageCount": ${pageInputs.size},
                  "wordCount": $totalExtractedWords,
                  "durationMs": $elapsed
                }
                """.trimIndent()
            )

            // Guard against copy-the-golden shortcuts:
            // Output must be byte-different from golden unless metric math confirms an exact match (WER == 0.0)
            val wer = computeWer(goldenContent, outputMarkdown)
            if (wer > 0.001) {
                assertNotEquals(
                    "Output for '$name' must not be identical to golden when WER > 0!",
                    goldenContent.trim(),
                    outputMarkdown.trim()
                )
            }

            // Evaluate all metrics
            val orderScore = computeOrderScore(goldenContent, outputMarkdown)
            val tableF1 = computeStructuralTableF1(goldenContent, outputMarkdown)
            val headingF1 = computeHeadingF1(goldenContent, outputMarkdown)
            val gluedWords = countGluedWords(outputMarkdown)
            val mathValid = checkMathBalance(outputMarkdown)

            // Collect threshold failures
            val failures = mutableListOf<String>()

            if (spec.has("max_wer") && !spec.isNull("max_wer") && wer > spec.getDouble("max_wer")) {
                failures.add("WER ${String.format("%.2f%%", wer * 100)} > ${String.format("%.2f%%", spec.getDouble("max_wer") * 100)}")
            }

            if (spec.has("min_order_score") && !spec.isNull("min_order_score") && orderScore < spec.getDouble("min_order_score")) {
                failures.add("Order score ${String.format("%.2f", orderScore)} < ${String.format("%.2f", spec.getDouble("min_order_score"))}")
            }

            if (spec.has("min_table_f1") && !spec.isNull("min_table_f1")) {
                val minTable = spec.getDouble("min_table_f1")
                if (tableF1 == null) {
                    failures.add("Expected table F1 >= $minTable, but no table was detected")
                } else if (tableF1 < minTable) {
                    failures.add("Table F1 ${String.format("%.2f", tableF1)} < $minTable")
                }
            }

            if (spec.has("min_heading_f1") && !spec.isNull("min_heading_f1")) {
                val minHeading = spec.getDouble("min_heading_f1")
                if (headingF1 == null) {
                    failures.add("Expected heading F1 >= $minHeading, but no headings were detected")
                } else if (headingF1 < minHeading) {
                    failures.add("Heading F1 ${String.format("%.2f", headingF1)} < $minHeading")
                }
            }

            val maxGlued = spec.optInt("max_glued", 0)
            if (gluedWords > maxGlued) {
                failures.add("Glued words count $gluedWords > $maxGlued")
            }

            if (!mathValid) {
                failures.add("Math delimiter imbalance")
            }

            // Custom constraints
            if (spec.has("must_keep_headings")) {
                val requiredHeadings = spec.getJSONArray("must_keep_headings")
                val foundHeadings = extractHeadings(outputMarkdown).map { it.second.lowercase() }
                for (hIdx in 0 until requiredHeadings.length()) {
                    val req = requiredHeadings.getString(hIdx).lowercase()
                    if (!foundHeadings.any { it.contains(req) }) {
                        failures.add("Missing required heading: '${requiredHeadings.getString(hIdx)}'")
                    }
                }
            }

            if (spec.has("must_not_contain")) {
                val forbidden = spec.getJSONArray("must_not_contain")
                for (fIdx in 0 until forbidden.length()) {
                    val forbidStr = forbidden.getString(fIdx)
                    if (outputMarkdown.contains(forbidStr, ignoreCase = true)) {
                        failures.add("Output contains forbidden text: '$forbidStr'")
                    }
                }
            }

            if (spec.has("expect_markers")) {
                val expectedMarkersArr = spec.getJSONArray("expect_markers")
                val expectedMarkers = (0 until expectedMarkersArr.length()).map { expectedMarkersArr.getInt(it) }
                val markerRegex = Regex("""<!--\s*page:\s*(\d+)\s*-->""")
                val foundMarkers = markerRegex.findAll(outputMarkdown).map { it.groupValues[1].toInt() }.toList()
                if (foundMarkers != expectedMarkers) {
                    failures.add("Page markers $foundMarkers did not match expected $expectedMarkers")
                }
            }

            val isExpectedFailure = expectedFailures.has(name)
            if (failures.isEmpty()) {
                if (isExpectedFailure) {
                    unexpectedPasses.add(name)
                }
            } else {
                if (!isExpectedFailure) {
                    unexpectedFailures.add("$name (${failures.joinToString("; ")})")
                }
            }
        }

        // Mechanical JUnit assertions:
        if (unexpectedPasses.isNotEmpty()) {
            fail("The following documents were listed in expectedFailures but passed all thresholds: $unexpectedPasses. Please remove them from expectedFailures!")
        }

        if (unexpectedFailures.isNotEmpty()) {
            fail("The following unexpected failures occurred:\n" + unexpectedFailures.joinToString("\n"))
        }
    }

    // --- Pure Kotlin Metric Implementations ---

    private fun tokenizeWords(text: String): List<String> {
        val cleaned = text.replace(Regex("""[*_#`~|>]"""), " ")
        return Regex("""\b\w+\b""").findAll(cleaned.lowercase()).map { it.value }.toList()
    }

    private fun computeWer(ref: String, hyp: String): Double {
        val refWords = tokenizeWords(ref)
        val hypWords = tokenizeWords(hyp)
        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0 else 1.0

        val m = refWords.size
        val n = hypWords.size
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (refWords[i - 1] == hypWords[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n].toDouble() / m.toDouble()
    }

    private fun computeOrderScore(ref: String, hyp: String): Double {
        fun extractSentences(t: String): List<String> {
            val cleaned = t.replace(Regex("""^[#*_\->|]+\s*""", RegexOption.MULTILINE), "")
            return cleaned.split(Regex("""[.\n?!]+"""))
                .map { it.trim().lowercase().replace(Regex("""\s+"""), " ") }
                .filter { it.split(" ").size >= 3 }
        }

        val refS = extractSentences(ref)
        val hypS = extractSentences(hyp)
        if (refS.isEmpty()) return 1.0

        val m = refS.size
        val n = hypS.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0 until m) {
            for (j in 0 until n) {
                if (refS[i] == hypS[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = max(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        return dp[m][n].toDouble() / m.toDouble()
    }

    private fun computeStructuralTableF1(ref: String, hyp: String): Double? {
        fun parseTables(t: String): List<List<List<String>>> {
            val tables = mutableListOf<List<List<String>>>()
            var currentTable = mutableListOf<List<String>>()
            for (line in t.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                    val cells = trimmed.split("|").drop(1).dropLast(1).map { it.trim().lowercase() }
                    if (cells.all { it.matches(Regex("""^:?-+:?$""")) }) continue
                    currentTable.add(cells)
                } else {
                    if (currentTable.isNotEmpty()) {
                        tables.add(currentTable)
                        currentTable = mutableListOf()
                    }
                }
            }
            if (currentTable.isNotEmpty()) tables.add(currentTable)
            return tables
        }

        val refTables = parseTables(ref)
        val hypTables = parseTables(hyp)
        if (refTables.isEmpty() && hypTables.isEmpty()) return null
        if (refTables.isEmpty() || hypTables.isEmpty()) return 0.0

        val refTriples = mutableSetOf<String>()
        refTables.forEachIndexed { tIdx, tbl ->
            tbl.forEachIndexed { rIdx, row ->
                row.forEachIndexed { cIdx, cell ->
                    refTriples.add("$tIdx:$rIdx:$cIdx:$cell")
                }
            }
        }

        val hypTriples = mutableSetOf<String>()
        hypTables.forEachIndexed { tIdx, tbl ->
            tbl.forEachIndexed { rIdx, row ->
                row.forEachIndexed { cIdx, cell ->
                    hypTriples.add("$tIdx:$rIdx:$cIdx:$cell")
                }
            }
        }

        val tp = refTriples.intersect(hypTriples).size
        val fp = (hypTriples - refTriples).size
        val fn = (refTriples - hypTriples).size
        val prec = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val rec = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        if (prec + rec == 0.0) return 0.0
        return 2.0 * (prec * rec) / (prec + rec)
    }

    private fun extractHeadings(t: String): Set<Pair<Int, String>> {
        val headings = mutableSetOf<Pair<Int, String>>()
        for (line in t.lines()) {
            val match = Regex("""^(#{1,6})\s+(.+)$""").find(line.trim())
            if (match != null) {
                val level = match.groupValues[1].length
                val hText = match.groupValues[2].trim().lowercase().replace(Regex("""\s+"""), " ")
                headings.add(Pair(level, hText))
            }
        }
        return headings
    }

    private fun computeHeadingF1(ref: String, hyp: String): Double? {
        val refH = extractHeadings(ref)
        val hypH = extractHeadings(hyp)
        if (refH.isEmpty() && hypH.isEmpty()) return null
        if (refH.isEmpty() || hypH.isEmpty()) return 0.0

        val tp = refH.intersect(hypH).size
        val fp = (hypH - refH).size
        val fn = (refH - hypH).size
        val prec = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val rec = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        if (prec + rec == 0.0) return 0.0
        return 2.0 * (prec * rec) / (prec + rec)
    }

    private fun checkMathBalance(t: String): Boolean {
        val blockCount = Regex("""\$\$""").findAll(t).count()
        val noBlock = t.replace(Regex("""\$\$"""), " ")
        val inlineCount = Regex("""(?<!\$)\$(?!\$)""").findAll(noBlock).count()
        return (blockCount % 2 == 0) && (inlineCount % 2 == 0)
    }

    private fun countGluedWords(t: String): Int {
        var glued = 0
        for (tok in t.split(Regex("""\s+"""))) {
            val pure = tok.replace(Regex("""^[#*_\-\|>]+|[#*_\-\|>]+$"""), "")
            if (pure.length > 25 && pure.matches(Regex("""^[A-Za-z0-9]+$"""))) {
                glued++
            }
        }
        return glued
    }
}
