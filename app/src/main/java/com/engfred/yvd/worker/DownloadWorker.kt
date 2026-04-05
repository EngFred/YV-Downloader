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

/**
 * WorkManager worker that drives a single download from the queue.
 *
 * Lifecycle contract:
 * - On start  : marks the queue item RUNNING in Room and shows a foreground notification.
 * - On success: marks the queue item DONE, triggers MediaScanner, shows a completion notification.
 * - On pause  : WorkManager job is cancelled externally (via [CancelReceiver] or UI button);
 *               [isStopped] becomes true and the item is marked PAUSED so it can be resumed.
 *               The partial file and its ResumeState are deliberately preserved on disk.
 * - On failure: marks the queue item FAILED with a human-readable message and shows an error
 *               notification.  The partial file and ResumeState are also preserved so a Retry
 *               will resume from the last completed chunk rather than starting from zero.
 *
 * ## File integrity gate
 * A [DownloadStatus.Success] event from the repository is not trusted blindly.  Before marking
 * the item DONE the worker verifies that the output file exists and is larger than [MIN_VALID_FILE_SIZE].
 * This guards against the rare edge-case where a stream URL expires mid-download and the server
 * returns an empty or near-empty error body that is written to disk as the "file".
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: YoutubeRepository,
    private val queueRepository: DownloadQueueRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Flipped to true only after [DownloadStatus.Success] is received AND the output file
     * passes the integrity check.  Guards the `finally` block from accidentally marking a
     * successful download as PAUSED if WorkManager signals stop at the exact same moment.
     */
    private var downloadCompleted = false

    override suspend fun doWork(): Result {
        val queueItemId = inputData.getString("queueItemId") ?: return Result.failure()
        val url         = inputData.getString("url")         ?: return Result.failure()
        val formatId    = inputData.getString("formatId")    ?: return Result.failure()
        val title       = inputData.getString("title") ?: "Media"
        val isAudio     = inputData.getBoolean("isAudio", false)

        // Each item gets a stable notification ID derived from its queue ID so that
        // progress notifications update in-place rather than spawning new ones on retry.
        val progressNotifId    = queueItemId.hashCode()
        val completionNotifId  = progressNotifId + 1
        val typeLabel          = if (isAudio) "Audio" else "Video"

        // Mark as RUNNING immediately so the UI reflects the true state before any
        // async work begins — prevents the item from appearing stuck on "Queued" while
        // the worker is initialising or waiting for a foreground service slot.
        queueRepository.updateStatusAndWorkId(
            id = queueItemId,
            status = DownloadQueueStatus.RUNNING,
            workId = id.toString(),
            statusText = "Starting…"
        )

        try {
            setForeground(
                buildForegroundInfo(
                    notifId      = progressNotifId,
                    title        = title,
                    progress     = 0,
                    indeterminate = true,
                    typeLabel    = typeLabel,
                    queueItemId  = queueItemId
                )
            )

            var resultFile: File? = null

            repository.downloadVideo(url, formatId, title, isAudio).collectLatest { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        queueRepository.updateProgress(
                            id         = queueItemId,
                            status     = DownloadQueueStatus.RUNNING,
                            progress   = status.progress,
                            statusText = status.text
                        )
                        // Only update the foreground notification once real progress is
                        // available — avoids a jarring indeterminate→0% flash on fast starts.
                        if (status.progress > 0f) {
                            setForeground(
                                buildForegroundInfo(
                                    notifId       = progressNotifId,
                                    title         = title,
                                    progress      = status.progress.toInt(),
                                    indeterminate = false,
                                    typeLabel     = typeLabel,
                                    queueItemId   = queueItemId
                                )
                            )
                        }
                    }

                    is DownloadStatus.Success -> resultFile = status.file

                    is DownloadStatus.Error -> throw Exception(status.message)
                }
            }

            // ── File integrity gate ───────────────────────────────────────────
            // Do NOT trust the Success event alone. Verify the output file exists
            // and has a meaningful size before declaring victory.  A tiny file
            // (< MIN_VALID_FILE_SIZE) indicates a failed stream URL that returned
            // an error payload rather than media bytes.
            val file = resultFile
                ?.takeIf { it.exists() && it.length() >= MIN_VALID_FILE_SIZE }
                ?: run {
                    val len = resultFile?.length() ?: 0L
                    throw Exception(
                        if (resultFile == null || !resultFile.exists())
                            "Output file missing after download completed"
                        else
                            "Output file is too small to be valid (${len}B) — the stream may have expired"
                    )
                }

            downloadCompleted = true

            queueRepository.markDone(queueItemId, file.absolutePath)

            // Notify the media store so the file appears in gallery/music apps immediately.
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ -> }

            showCompletionNotification(completionNotifId, title, file, isAudio)

            return Result.success(workDataOf("filePath" to file.absolutePath))

        } catch (e: Exception) {
            return if (isStopped) {
                // Worker was cancelled externally (pause button / notification action /
                // system resource pressure).  Partial file + ResumeState are preserved
                // on disk so the next Resume picks up from the last completed chunk.
                withContext(NonCancellable) {
                    val item = queueRepository.getById(queueItemId)
                    if (item != null &&
                        (item.status == DownloadQueueStatus.RUNNING ||
                                item.status == DownloadQueueStatus.QUEUED)
                    ) {
                        queueRepository.markPaused(queueItemId)
                    }
                }
                Result.failure()
            } else {
                // Genuine failure (network error, expired URL, IO error, etc.).
                // Partial file + ResumeState are intentionally preserved so a Retry will
                // resume from the last completed chunk rather than re-downloading everything.
                val msg = buildUserFriendlyErrorMessage(e)
                queueRepository.markFailed(queueItemId, msg)
                showFailureNotification(completionNotifId, title, msg)
                Result.failure(workDataOf("error" to msg))
            }

        } finally {
            // Safety net: if WorkManager stops the worker at the exact moment the
            // try-block succeeds, `isStopped` might flip to true after `downloadCompleted`
            // is already set — do nothing in that case.
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

    // ─── Error Message Helpers ─────────────────────────────────────────────────

    /**
     * Converts a raw exception into a concise, user-facing message.
     * Strips Java package prefixes and long stack-trace noise that would otherwise
     * appear verbatim in the "Download Failed" notification body and the queue card.
     */
    private fun buildUserFriendlyErrorMessage(e: Exception): String {
        val raw = e.message ?: "Unknown error"
        return when {
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                    raw.contains("failed to connect",      ignoreCase = true) ||
                    raw.contains("SocketTimeout",          ignoreCase = true) ||
                    raw.contains("Connection reset",       ignoreCase = true) ->
                "Network error — check your connection and retry"

            raw.contains("HTTP 403", ignoreCase = true) ||
                    raw.contains("HTTP 401", ignoreCase = true) ->
                "Stream URL expired — tap Retry to refresh and resume"

            raw.contains("No space left", ignoreCase = true) ->
                "Storage full — free up space and retry"

            raw.contains("too small to be valid", ignoreCase = true) ->
                "Download produced an invalid file — tap Retry"

            raw.contains("Output file missing", ignoreCase = true) ->
                "File was lost after download — tap Retry"

            else -> raw.take(120) // Truncate very long messages for notification display
        }
    }

    // ─── Notifications ─────────────────────────────────────────────────────────

    private fun buildForegroundInfo(
        notifId: Int,
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
        // The worker's isStopped path then marks the Room item as PAUSED and leaves the
        // partial file + ResumeState intact for the next Resume.
        val pauseIntent = PendingIntent.getBroadcast(
            context,
            queueItemId.hashCode(),
            Intent(context, CancelReceiver::class.java).apply {
                action = "PAUSE_DOWNLOAD"
                putExtra("workManagerId", this@DownloadWorker.id.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = when {
            indeterminate -> title
            progress > 0  -> "$title ($progress%)"
            else          -> title
        }

        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Downloading $typeLabel")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun showCompletionNotification(
        notifId: Int,
        title: String,
        file: File,
        isAudio: Boolean
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val mimeType = if (isAudio) "audio/*" else "video/*"

        val openIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
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

        notificationManager.notify(notifId, notification)
    }

    private fun showFailureNotification(notifId: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Failed")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n\n$error"))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Minimum byte size for an output file to be considered a valid media file.
         * Files smaller than this after a "successful" download are treated as failures.
         * 10 KB is well below any real audio/video file but above typical HTTP error payloads.
         */
        private const val MIN_VALID_FILE_SIZE = 10 * 1024L // 10 KB
    }
}