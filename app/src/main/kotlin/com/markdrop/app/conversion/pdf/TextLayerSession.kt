package com.markdrop.app.conversion.pdf

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.layout.LayoutWord
import com.markdrop.app.conversion.layout.PageGeometry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter

enum class PdfOpenError {
    PASSWORD_REQUIRED,
    CORRUPT,
    NOT_FOUND
}

class PdfOpenException(
    val error: PdfOpenError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * High-performance, session-based PDF text layer reader.
 * Opens the PDDocument once per conversion job using a file-backed memory policy.
 */
class TextLayerSession private constructor(
    private val document: PDDocument,
    private val tempFile: File?
) : Closeable {

    companion object {
        @Volatile
        private var isLoaderInitialized = false

        private fun ensureLoaderInitialized(context: Context) {
            if (!isLoaderInitialized) {
                synchronized(this) {
                    if (!isLoaderInitialized) {
                        try {
                            PDFBoxResourceLoader.init(context.applicationContext)
                        } catch (_: Exception) {
                        }
                        isLoaderInitialized = true
                    }
                }
            }
        }

        suspend fun open(context: Context, uri: Uri): TextLayerSession = withContext(Dispatchers.IO) {
            ensureLoaderInitialized(context)

            // Copy to temporary cache file to avoid loading entire document into Java heap
            val tempFile = File(context.cacheDir, "markdrop_session_${System.currentTimeMillis()}_${(1000..9999).random()}.pdf")
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw PdfOpenException(PdfOpenError.NOT_FOUND, "Could not open URI stream: $uri")

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // File-backed memory usage setting prevents OOM on large PDFs
                val memorySetting = MemoryUsageSetting.setupTempFileOnly()
                val document = try {
                    PDDocument.load(tempFile, memorySetting)
                } catch (e: Exception) {
                    tempFile.delete()
                    if (e.message?.contains("password", ignoreCase = true) == true ||
                        e.javaClass.name.contains("Encrypted", ignoreCase = true)
                    ) {
                        throw PdfOpenException(PdfOpenError.PASSWORD_REQUIRED, "This PDF is encrypted and requires a password to open.", e)
                    } else {
                        throw PdfOpenException(PdfOpenError.CORRUPT, "This PDF is corrupted or could not be parsed: ${e.message}", e)
                    }
                }

                if (document.isEncrypted) {
                    document.close()
                    tempFile.delete()
                    throw PdfOpenException(PdfOpenError.PASSWORD_REQUIRED, "This PDF is encrypted and requires a password to open.")
                }

                TextLayerSession(document, tempFile)
            } catch (e: PdfOpenException) {
                tempFile.delete()
                throw e
            } catch (e: java.io.FileNotFoundException) {
                tempFile.delete()
                throw PdfOpenException(PdfOpenError.NOT_FOUND, "Document file not found: ${e.message}", e)
            } catch (e: Exception) {
                tempFile.delete()
                throw PdfOpenException(PdfOpenError.CORRUPT, "Failed to open document: ${e.message}", e)
            }
        }
    }

    val pageCount: Int
        get() = document.numberOfPages

    fun pageGeometry(pageIndex: Int): PageGeometry {
        require(pageIndex in 0 until pageCount) { "Invalid page index: $pageIndex" }
        val page = document.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox
        val rotation = page.rotation

        return if (rotation == 90 || rotation == 270) {
            PageGeometry(width = box.height, height = box.width, rotation = rotation)
        } else {
            PageGeometry(width = box.width, height = box.height, rotation = rotation)
        }
    }

    fun extractWords(pageIndex: Int): List<LayoutWord> {
        require(pageIndex in 0 until pageCount) { "Invalid page index: $pageIndex" }
        val collectedGlyphs = mutableListOf<Glyph>()

        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode ?: ""
                val font = text.font
                val desc = font?.fontDescriptor
                val fontName = font?.name?.lowercase() ?: ""

                val isBold = desc?.isForceBold == true ||
                        (desc?.fontWeight ?: 400f) >= 700f ||
                        fontName.contains("bold") ||
                        fontName.contains("black") ||
                        fontName.contains("heavy")

                val isItalic = desc?.isItalic == true ||
                        (desc?.italicAngle ?: 0f) != 0f ||
                        fontName.contains("italic") ||
                        fontName.contains("oblique")

                collectedGlyphs.add(
                    Glyph(
                        unicode = unicode,
                        x = text.xDirAdj,
                        y = text.yDirAdj,
                        width = text.widthDirAdj,
                        height = text.heightDir,
                        fontSize = text.fontSizeInPt,
                        bold = isBold,
                        italic = isItalic
                    )
                )
                super.processTextPosition(text)
            }
        }

        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.sortByPosition = false

        try {
            stripper.writeText(document, StringWriter())
        } catch (_: Exception) {
            return emptyList()
        }

        return WordsFromGlyphs.group(collectedGlyphs)
    }

    fun analyzePage(pageIndex: Int, words: List<LayoutWord>): PageContentInfo {
        require(pageIndex in 0 until pageCount) { "Invalid page index: $pageIndex" }
        val page = document.getPage(pageIndex)
        val geom = pageGeometry(pageIndex)
        return PageContentAnalyzer.analyze(page, words, geom.width, geom.height)
    }

    override fun close() {
        try {
            document.close()
        } catch (_: Exception) {
        }
        try {
            tempFile?.delete()
        } catch (_: Exception) {
        }
    }
}
