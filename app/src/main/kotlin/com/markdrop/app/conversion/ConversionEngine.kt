package com.markdrop.app.conversion

import android.content.Context
import android.net.Uri
import com.markdrop.app.MarkdropApplication
import com.markdrop.app.data.db.ConversionEntity
import com.markdrop.app.data.db.ConversionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversionEngine(
    private val context: Context
) {

    suspend fun convertPdf(
        pdfUri: Uri,
        mode: ConversionMode = ConversionMode.OFFLINE,
        selectedPages: List<Int>? = null,
        onProgress: (pageIndex: Int, totalPages: Int, statusMessage: String?) -> Unit = { _, _, _ -> }
    ): ConversionResult = withContext(Dispatchers.Default) {
        val app = context.applicationContext as MarkdropApplication
        val fileName = pdfUri.lastPathSegment?.substringAfterLast("/") ?: "document.pdf"
        val job = ConversionEntity(
            fileName = fileName,
            pdfUri = pdfUri.toString(),
            fileSizeBytes = 0L,
            pageCount = selectedPages?.size ?: 1,
            mode = mode,
            status = ConversionStatus.PROCESSING
        )
        val jobId = app.database.conversionDao().insert(job)
        ConversionPipeline(context).executeJob(jobId) { idx, total, msg ->
            onProgress(idx, total, msg)
        }
    }
}
