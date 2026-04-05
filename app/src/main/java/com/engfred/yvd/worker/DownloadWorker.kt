package com.engfred.yvd.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.engfred.yvd.MainActivity
import com.engfred.yvd.domain.model.DownloadQueueStatus
import com.engfred.yvd.domain.model.DownloadStatus
import com.engfred.yvd.domain.repository.DownloadQueueRepository
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.engfred.yvd.receiver.CancelReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: YoutubeRepository,
    private val queueRepository: DownloadQueueRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var downloadCompleted = false

    override suspend fun doWork(): Result {
        val queueItemId = inputData.getString("queueItemId") ?: return Result.failure()
        val url         = inputData.getString("url")         ?: return Result.failure()
        val formatId    = inputData.getString("formatId")    ?: return Result.failure()
        val title       = inputData.getString("title") ?: "Media"
        val isAudio     = inputData.getBoolean("isAudio", false)

        val notifId   = queueItemId.hashCode()
        val typeLabel = if (isAudio) "Audio" else "Video"

        // Mark as RUNNING in Room immediately
        queueRepository.updateStatusAndWorkId(
            queueItemId, DownloadQueueStatus.RUNNING, id.toString(), "Starting…"
        )

        try {
            setForeground(buildForegroundInfo(notifId, title, 0, true, typeLabel, queueItemId))

            var resultFile: File? = null

            repository.downloadVideo(url, formatId, title, isAudio).collectLatest { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        queueRepository.updateProgress(
                            queueItemId, DownloadQueueStatus.RUNNING, status.progress, status.text
                        )
                        if (status.progress > 0f) {
                            setForeground(buildForegroundInfo(notifId, title, status.progress.toInt(), false, typeLabel, queueItemId))
                        }
                    }
                    is DownloadStatus.Success -> resultFile = status.file
                    is DownloadStatus.Error   -> throw Exception(status.message)
                }
            }

            val file = resultFile?.takeIf { it.exists() }
                ?: throw Exception("File verification failed after download")

            downloadCompleted = true
            queueRepository.markDone(queueItemId, file.absolutePath)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ -> }
            showCompletionNotification(notifId + 1, title, file, isAudio)

            return Result.success(workDataOf("filePath" to file.absolutePath))

        } catch (e: Exception) {
            if (isStopped) {
                // Stopped externally (pause from UI or notification) — chunk state is safe on disk
                withContext(NonCancellable) {
                    val item = queueRepository.getById(queueItemId)
                    if (item != null &&
                        (item.status == DownloadQueueStatus.RUNNING || item.status == DownloadQueueStatus.QUEUED)
                    ) {
                        queueRepository.markPaused(queueItemId)
                    }
                }
                return Result.failure()
            }
            val msg = e.message ?: "Unknown error"
            queueRepository.markFailed(queueItemId, msg)
            showFailureNotification(notifId + 1, title, msg)
            return Result.failure(workDataOf("error" to msg))

        } finally {
            // Safety net for edge cases where isStopped wasn't caught above
            if (isStopped && !downloadCompleted) {
                withContext(NonCancellable) {
                    val item = queueRepository.getById(queueItemId)
                    if (item?.status == DownloadQueueStatus.RUNNING) {
                        queueRepository.markPaused(queueItemId)
                    }
                }
            }
        }
    }

    // ─── Notifications ─────────────────────────────────────────────────────────

    private fun buildForegroundInfo(
        id: Int,
        title: String,
        progress: Int,
        indeterminate: Boolean,
        typeLabel: String,
        queueItemId: String
    ): ForegroundInfo {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        // "Pause" broadcasts to CancelReceiver, which cancels the WorkManager job.
        // The worker's isStopped path then marks the Room item as PAUSED.
        val pauseIntent = PendingIntent.getBroadcast(
            context, queueItemId.hashCode(),
            Intent(context, CancelReceiver::class.java).apply {
                action = "PAUSE_DOWNLOAD"
                putExtra("workManagerId", this@DownloadWorker.id.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Downloading $typeLabel")
            .setContentText("$title${if (progress > 0) " ($progress%)" else ""}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun showCompletionNotification(id: Int, title: String, file: File, isAudio: Boolean) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = if (isAudio) "audio/*" else "video/*"
        val openIntent = PendingIntent.getActivity(
            context, id,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun showFailureNotification(id: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Failed")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$error"))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }
}