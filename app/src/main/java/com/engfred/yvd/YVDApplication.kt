package com.engfred.yvd

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.engfred.yvd.data.network.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Application entry point.
 *
 * **Single responsibility here:** initialize app-wide singletons and Android system channels.
 * All heavy dependencies are provided by Hilt — we never call `new X()` in this class.
 *
 * ## NewPipe initialization contract
 * `NewPipe.init()` is called **exactly once**, here, with the **injected** [DownloaderImpl]
 * singleton. This guarantees that:
 * 1. The same [okhttp3.OkHttpClient] (and its connection pool) is used for both metadata
 *    extraction and file downloads.
 * 2. No component downstream (Repository, Worker, etc.) ever calls `NewPipe.init()` again,
 *    which would silently swap out the Downloader and break the shared connection pool.
 *
 * ## WorkManager concurrency cap
 * Downloads are driven by [com.engfred.yvd.worker.DownloadWorker].  Each worker spawns up to
 * 4 parallel IO threads (THREAD_COUNT in YoutubeRepositoryImpl).  Without an explicit executor
 * limit, enqueueing a 121-video playlist would schedule up to 121 simultaneous workers, each
 * with 4 threads — up to 484 concurrent network connections — which caused severe UI jank and
 * potential ANRs on mid-range devices.
 *
 * Setting a [Executors.newFixedThreadPool] with [MAX_CONCURRENT_WORKERS] threads on the
 * WorkManager [Configuration] limits actual worker concurrency to 3.  Combined with the
 * 4-thread-per-worker download engine the effective ceiling is 12 concurrent IO threads —
 * fast enough for good throughput, light enough to keep the UI responsive.
 *
 * Hilt field injection is performed during [attachBaseContext], so all `@Inject` fields
 * are guaranteed to be non-null by the time [onCreate] runs.
 */
@HiltAndroidApp
class YVDApplication : Application(), Configuration.Provider {

    private val TAG = "YVD_APP"

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * The same singleton injected into the Repository and used for file downloads.
     * Injected here solely to call [NewPipe.init] with it once at app startup.
     */
    @Inject
    lateinit var downloaderImpl: DownloaderImpl

    /**
     * WorkManager configuration.
     *
     * The custom [Executors.newFixedThreadPool] executor caps the number of concurrently
     * running workers.  WorkManager will still accept and queue any number of enqueued
     * work requests — only [MAX_CONCURRENT_WORKERS] will actually run at the same time.
     *
     * This is the primary fix for UI jank / ANR reports when downloading large playlists.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Executors.newFixedThreadPool(MAX_CONCURRENT_WORKERS))
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting…")

        // ── Single initialization point for NewPipe ──────────────────────────
        // Do NOT call NewPipe.init() anywhere else in the codebase.
        NewPipe.init(downloaderImpl)
        Log.d(TAG, "NewPipe initialized with shared DownloaderImpl singleton")

        createNotificationChannels()

        Log.d(TAG, "Application ready (WorkManager capped at $MAX_CONCURRENT_WORKERS concurrent workers)")
    }

    // ─── Notification Channels ────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low-importance channel for ongoing download progress (no sound/vibration).
            val progressChannel = NotificationChannel(
                "download_channel",
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active video/audio downloads"
                setShowBadge(false)
            }

            // High-importance channel for completion / failure (vibrates, heads-up).
            val completionChannel = NotificationChannel(
                "download_completed",
                "Download Completed",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a download finishes or fails"
                enableVibration(true)
            }

            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(completionChannel)
        }
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Maximum number of [com.engfred.yvd.worker.DownloadWorker] instances that WorkManager
         * will run simultaneously.
         *
         * Each worker spawns THREAD_COUNT (= 4) parallel IO threads internally, so the
         * effective ceiling for concurrent download threads is:
         *   MAX_CONCURRENT_WORKERS × 4 = 12 threads
         *
         * Set to 3 as a balance between download throughput and device responsiveness.
         * Increase to 4–5 only if targeting high-end devices and after profiling.
         */
        private const val MAX_CONCURRENT_WORKERS = 3
    }
}