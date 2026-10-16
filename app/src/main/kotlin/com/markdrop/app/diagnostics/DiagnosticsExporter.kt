package com.markdrop.app.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticsExporter {

    fun exportLastJobDiagnostics(context: Context): Result<File> {
        val lastJobId = PipelineTrace.getLastJobId()
            ?: return Result.failure(IllegalStateException("No diagnostics recorded yet for any job."))

        val jobDir = PipelineTrace.getJobDiagnosticsDir(context, lastJobId)
        if (!jobDir.exists() || jobDir.listFiles().isNullOrEmpty()) {
            return Result.failure(IllegalStateException("Diagnostics folder for job '$lastJobId' is empty or missing."))
        }

        return try {
            val exportsDir = File(context.cacheDir, "diagnostics_export").apply { mkdirs() }
            val zipFile = File(exportsDir, "diagnostics_${jobDir.name}.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zipDirectory(jobDir, jobDir, zos)
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareDiagnosticsZip(context: Context, zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun zipDirectory(rootDir: File, currentFile: File, zos: ZipOutputStream) {
        val files = currentFile.listFiles() ?: return
        val buffer = ByteArray(8192)

        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(rootDir, file, zos)
            } else {
                val relativePath = rootDir.toURI().relativize(file.toURI()).path
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
                zos.closeEntry()
            }
        }
    }
}
