package com.markdrop.app.conversion.pdf

import android.content.Context
import android.net.Uri
import com.markdrop.app.conversion.PdfCorruptException
import com.markdrop.app.conversion.PdfEncryptedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.FileNotFoundException

sealed class ProbeResult : Closeable {
    data class Success(
        val textSession: TextLayerSession?,
        val renderer: PdfPageRenderer,
        val pageCount: Int
    ) : ProbeResult() {
        override fun close() {
            try { textSession?.close() } catch (_: Exception) {}
            try { renderer.close() } catch (_: Exception) {}
        }
    }

    data class Encrypted(val message: String = "Document is password protected.") : ProbeResult() {
        override fun close() {}
    }

    data class Corrupted(
        val message: String = "Document appears corrupted or invalid PDF.",
        val cause: Throwable? = null
    ) : ProbeResult() {
        override fun close() {}
    }

    data class NotFound(val message: String = "Document not found.") : ProbeResult() {
        override fun close() {}
    }
}

object PdfProbe {

    suspend fun open(context: Context, uri: Uri): ProbeResult = withContext(Dispatchers.IO) {
        var textSession: TextLayerSession? = null

        // 1. Attempt TextLayerSession first (PDFBox)
        try {
            textSession = TextLayerSession.open(context, uri)
        } catch (e: PdfOpenException) {
            when (e.error) {
                PdfOpenError.PASSWORD_REQUIRED -> return@withContext ProbeResult.Encrypted()
                PdfOpenError.CORRUPT -> return@withContext ProbeResult.Corrupted(cause = e)
                PdfOpenError.NOT_FOUND -> return@withContext ProbeResult.NotFound()
            }
        } catch (e: FileNotFoundException) {
            return@withContext ProbeResult.NotFound()
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("not found") || msg.contains("no such file")) {
                return@withContext ProbeResult.NotFound()
            }
            if (msg.contains("password") || msg.contains("encrypted")) {
                return@withContext ProbeResult.Encrypted()
            }
            return@withContext ProbeResult.Corrupted(cause = e)
        }

        // 2. Attempt PdfPageRenderer (Android OS PdfRenderer)
        val renderer = try {
            PdfPageRenderer(context, uri)
        } catch (e: SecurityException) {
            try { textSession?.close() } catch (_: Exception) {}
            return@withContext ProbeResult.Encrypted()
        } catch (e: FileNotFoundException) {
            try { textSession?.close() } catch (_: Exception) {}
            return@withContext ProbeResult.NotFound()
        } catch (e: Exception) {
            try { textSession?.close() } catch (_: Exception) {}
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted")) {
                return@withContext ProbeResult.Encrypted()
            }
            return@withContext ProbeResult.Corrupted(cause = e)
        }

        ProbeResult.Success(
            textSession = textSession,
            renderer = renderer,
            pageCount = renderer.pageCount
        )
    }
}
