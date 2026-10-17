package com.markdrop.app.conversion.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.markdrop.app.R
import com.markdrop.app.conversion.ConversionPipeline
import com.markdrop.app.data.db.ConversionStatus
import com.markdrop.app.data.db.MarkdropDatabase

class ConversionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_JOB_ID = "conversion_job_id"
        const val CHANNEL_ID = "markdrop_conversion_channel"
        const val NOTIFICATION_ID = 4040
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getLong(KEY_JOB_ID, -1L)
        if (jobId == -1L) {
            return Result.failure(workDataOf("error" to "Missing job ID"))
        }

        val db = MarkdropDatabase.getInstance(context)
        val job = db.conversionDao().getById(jobId) ?: return Result.failure()

        // Set up foreground notification immediately
        try {
            val initialInfo = createForegroundInfo("Starting conversion for ${job.fileName}...", 0, job.pageCount)
            setForeground(initialInfo)
        } catch (_: Exception) {
            // Android 14+ foreground service start restrictions when backgrounded
        }

        val pipeline = ConversionPipeline(context)

        return try {
            pipeline.executeJob(
                jobId = jobId,
                onProgress = { current, total, message ->
                    val progressInfo = createForegroundInfo(message, current, total)
                    try {
                        notificationManager.notify(NOTIFICATION_ID, progressInfo.notification)
                    } catch (_: Exception) {}
                }
            )

            // Post completion notification
            showCompletionNotification(job.fileName)
            Result.success(workDataOf(KEY_JOB_ID to jobId))
        } catch (e: Exception) {
            val reason = stopReason
            val isExplicitCancel = reason == androidx.work.WorkInfo.STOP_REASON_CANCELLED_BY_APP ||
                    reason == androidx.work.WorkInfo.STOP_REASON_USER ||
                    e is com.markdrop.app.conversion.ConversionCancelledException

            if (isExplicitCancel) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    db.conversionDao().updateStatus(jobId, ConversionStatus.CANCELLED)
                }
                Result.failure(workDataOf("cancelled" to true))
            } else if (isStopped) {
                // Stopped due to system constraints (connectivity, battery, preemption) or timeout
                // Do not mark failed; preserve partial per-page results and request WorkManager retry with backoff
                Result.retry()
            } else {
                db.conversionDao().markFailed(jobId, e.message ?: "Conversion failed")
                showFailureNotification(job.fileName, e.message)
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createForegroundInfo(message: String, current: Int, total: Int): ForegroundInfo {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Converting Document")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (total > 0) {
            builder.setProgress(total, current, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletionNotification(fileName: String) {
        if (!hasNotificationPermission()) return
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Conversion Complete")
                .setContentText("$fileName was converted successfully.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID + 1, notification)
        } catch (_: Exception) {}
    }

    private fun showFailureNotification(fileName: String, error: String?) {
        if (!hasNotificationPermission()) return
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Conversion Failed")
                .setContentText("Could not convert $fileName: ${error?.take(80) ?: "Unknown error"}")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID + 2, notification)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Document Conversions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active background document conversions"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
