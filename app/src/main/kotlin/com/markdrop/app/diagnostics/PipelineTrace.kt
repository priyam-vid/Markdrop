package com.markdrop.app.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class RequestMeta(
    val engine: String,
    val provider: String? = null,
    val modelId: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val imageByteSize: Long = 0,
    val promptSha256: String = "",
    val maxTokens: Int = 0,
    val temperature: Float = 0f
)

@Serializable
data class ResponseMeta(
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val httpStatus: Int? = null,
    val latencyMs: Long = 0
)

@Serializable
data class TraceBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class TraceLine(
    val text: String,
    val box: TraceBoundingBox? = null,
    val confidence: Float? = null,
    val fontSize: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontName: String? = null
)

@Serializable
data class TraceLayerDump(
    val pageIndex: Int,
    val source: String,
    val lines: List<TraceLine>
)

object PipelineTrace {
    private const val TAG = "MarkdropPipe"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var isDiagnosticsEnabled: Boolean = false
    private var lastActiveJobId: String? = null

    fun getDiagnosticsDir(context: Context): File {
        return File(context.filesDir, "diagnostics").apply { mkdirs() }
    }

    fun getJobDiagnosticsDir(context: Context, jobId: String): File {
        return File(getDiagnosticsDir(context), sanitize(jobId)).apply { mkdirs() }
    }

    fun getPageDiagnosticsDir(context: Context, jobId: String, pageIndex: Int): File {
        return File(getJobDiagnosticsDir(context, jobId), "p$pageIndex").apply { mkdirs() }
    }

    fun getLastJobId(): String? = lastActiveJobId

    fun logStage(pageIndex: Int, stage: String, details: String, durationMs: Long? = null, sizeBytes: Long? = null) {
        val timingStr = if (durationMs != null) " in ${durationMs}ms" else ""
        val sizeStr = if (sizeBytes != null) " (${sizeBytes} bytes)" else ""
        Log.i(TAG, "[Page $pageIndex] $stage$timingStr$sizeStr - $details")
    }

    fun recordPageBitmap(context: Context, jobId: String, pageIndex: Int, bitmap: Bitmap) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            val file = File(dir, "page.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            logStage(pageIndex, "Trace:SavedBitmap", "${file.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record page bitmap trace: ${e.message}")
        }
    }

    fun recordTextLayer(context: Context, jobId: String, pageIndex: Int, lines: List<TraceLine>) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            val dump = TraceLayerDump(pageIndex = pageIndex, source = "TEXT_LAYER", lines = lines)
            File(dir, "text_layer.json").writeText(json.encodeToString(dump))
            logStage(pageIndex, "Trace:SavedTextLayer", "${lines.size} lines")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record text layer trace: ${e.message}")
        }
    }

    fun recordOcr(context: Context, jobId: String, pageIndex: Int, lines: List<TraceLine>) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            val dump = TraceLayerDump(pageIndex = pageIndex, source = "OCR", lines = lines)
            File(dir, "ocr.json").writeText(json.encodeToString(dump))
            logStage(pageIndex, "Trace:SavedOcr", "${lines.size} lines")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record ocr trace: ${e.message}")
        }
    }

    fun recordRequestMeta(context: Context, jobId: String, pageIndex: Int, meta: RequestMeta) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            File(dir, "request_meta.json").writeText(json.encodeToString(meta))
            logStage(pageIndex, "Trace:SavedRequestMeta", "engine=${meta.engine}, model=${meta.modelId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record request meta trace: ${e.message}")
        }
    }

    fun recordResponseRaw(context: Context, jobId: String, pageIndex: Int, rawResponse: String) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            val redacted = redactSensitiveKeys(rawResponse)
            File(dir, "response_raw.txt").writeText(redacted)
            logStage(pageIndex, "Trace:SavedResponseRaw", "${redacted.length} chars")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record raw response trace: ${e.message}")
        }
    }

    fun recordResponseMeta(context: Context, jobId: String, pageIndex: Int, meta: ResponseMeta) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            File(dir, "response_meta.json").writeText(json.encodeToString(meta))
            logStage(pageIndex, "Trace:SavedResponseMeta", "status=${meta.httpStatus}, finish=${meta.finishReason}, latency=${meta.latencyMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record response meta trace: ${e.message}")
        }
    }

    fun recordPageMarkdown(context: Context, jobId: String, pageIndex: Int, markdown: String) {
        lastActiveJobId = jobId
        if (!isDiagnosticsEnabled) return
        try {
            val dir = getPageDiagnosticsDir(context, jobId, pageIndex)
            File(dir, "page.md").writeText(markdown)
            logStage(pageIndex, "Trace:SavedPageMarkdown", "${markdown.length} chars")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record page markdown trace: ${e.message}")
        }
    }

    fun computeSha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun redactSensitiveKeys(text: String): String {
        return text
            .replace(Regex("""(AIzaSy[A-Za-z0-9_-]{33})"""), "AIzaSy[REDACTED]")
            .replace(Regex("""(sk-[A-Za-z0-9_-]{20,})"""), "sk-[REDACTED]")
            .replace(Regex("""(sk-ant-[A-Za-z0-9_-]{20,})"""), "sk-ant-[REDACTED]")
            .replace(Regex("""(Bearer\s+)[A-Za-z0-9._-]{10,}"""), "$1[REDACTED]")
            .replace(Regex("""(key=)[A-Za-z0-9_-]{10,}"""), "$1[REDACTED]")
            .replace(Regex("""(x-api-key["':\s]+)[A-Za-z0-9._-]{10,}""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            .replace(Regex("""(x-goog-api-key["':\s]+)[A-Za-z0-9._-]{10,}""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
    }

    private fun sanitize(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
