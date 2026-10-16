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
import java.io.File

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
        scaleFactor: Float = 2.0f
    ): Bitmap = withContext(Dispatchers.IO) {
        require(pageIndex in 0 until pageCount) {
            "Invalid pageIndex $pageIndex. Total pages: $pageCount"
        }

        // PdfRenderer strictly enforces only one open page at a time per renderer instance
        synchronized(this@PdfPageRenderer) {
            val page = renderer.openPage(pageIndex)
            try {
                val maxDimension = 1800f
                val maxPageDim = maxOf(page.width, page.height).toFloat()
                val effectiveScale = if (maxPageDim > 0f) {
                    minOf(maxDimension / maxPageDim, scaleFactor)
                } else {
                    scaleFactor
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

    override fun close() {
        try {
            renderer.close()
        } catch (_: Exception) {}
        try {
            pfd.close()
        } catch (_: Exception) {}
    }
}
