package com.markdrop.app.conversion.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

class PdfPageRenderer(
    private val context: Context,
    private val uri: Uri
) : Closeable {

    private val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF descriptor for URI: $uri")

    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int
        get() = renderer.pageCount

    suspend fun renderPageToBitmap(
        pageIndex: Int,
        targetDpi: Int = 200,
        isOcrMode: Boolean = false
    ): Bitmap = withContext(Dispatchers.IO) {
        require(pageIndex in 0 until pageCount) {
            "Invalid pageIndex $pageIndex. Total pages: $pageCount"
        }

        // PdfRenderer strictly enforces only one open page at a time per renderer instance
        synchronized(this@PdfPageRenderer) {
            val page = renderer.openPage(pageIndex)
            try {
                // PDF points are 72 per inch: scale = targetDpi / 72f
                val requestedScale = targetDpi / 72f

                // Target long edge: ~2200px for OCR, ~2000px for Cloud Vision
                var maxDimension = if (isOcrMode) 2200f else 2000f

                // Memory guard: Check JVM heap headroom
                val runtime = Runtime.getRuntime()
                val freeMemBytes = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
                val freeMemMb = freeMemBytes / (1024 * 1024)
                if (freeMemMb < 64) {
                    maxDimension = 1600f // Fallback to safe scale under memory pressure to prevent OOM
                }

                val maxPageDim = maxOf(page.width, page.height).toFloat()
                val effectiveScale = if (maxPageDim > 0f) {
                    minOf(maxDimension / maxPageDim, requestedScale)
                } else {
                    requestedScale
                }

                val targetWidth = (page.width * effectiveScale).toInt().coerceAtLeast(1)
                val targetHeight = (page.height * effectiveScale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888
                )

                // PDF backgrounds can be transparent; fill with white canvas
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                val matrix = Matrix().apply {
                    postScale(effectiveScale, effectiveScale)
                }

                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    /**
     * Backward-compatible overload for existing callers specifying scaleFactor.
     */
    suspend fun renderPageToBitmap(
        pageIndex: Int,
        scaleFactor: Float
    ): Bitmap {
        val targetDpi = (scaleFactor * 72f).toInt().coerceAtLeast(150)
        return renderPageToBitmap(pageIndex, targetDpi = targetDpi, isOcrMode = scaleFactor >= 2.2f)
    }

    override fun close() {
        try {
            renderer.close()
        } catch (_: Exception) {}
        try {
            pfd.close()
        } catch (_: Exception) {}
    }
}
