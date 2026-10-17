package com.markdrop.app.conversion.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfProbeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `probe returns NotFound for non-existent file`() = runBlocking {
        val nonExistentUri = Uri.fromFile(File(context.cacheDir, "non_existent_${System.currentTimeMillis()}.pdf"))
        val result = PdfProbe.open(context, nonExistentUri)
        assertTrue("Expected NotFound but got $result", result is ProbeResult.NotFound)
    }

    @Test
    fun `probe returns Corrupted for corrupt file content`() = runBlocking {
        val corruptFile = File(context.cacheDir, "corrupt_${System.currentTimeMillis()}.pdf")
        corruptFile.writeText("THIS IS NOT A VALID PDF FILE AT ALL 1234567890")
        try {
            val result = PdfProbe.open(context, Uri.fromFile(corruptFile))
            assertTrue("Expected Corrupted but got $result", result is ProbeResult.Corrupted)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun `probe returns Success for valid corpus PDF`() = runBlocking {
        val rootDir = File(".").canonicalFile.let { if (it.name == "app") it.parentFile else it }
        val samplePdf = File(rootDir, "testdata/corpus/digital_simple_article.pdf")
        if (samplePdf.exists()) {
            val result = PdfProbe.open(context, Uri.fromFile(samplePdf))
            assertTrue("Expected Success but got $result", result is ProbeResult.Success)
            val success = result as ProbeResult.Success
            assertEquals(1, success.pageCount)
            success.close()
        }
    }
}
