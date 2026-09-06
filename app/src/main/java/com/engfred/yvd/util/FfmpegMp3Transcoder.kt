package com.engfred.yvd.util

import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File

/**
 * FFmpeg-backed MP3 conversion using the `libmp3lame` encoder inside the
 * ffmpeg-kit `audio` build. Native decode + encode run entirely in C without
 * per-buffer JNI copies, which is typically 5–10x faster (near realtime or
 * better) than the Android MediaCodec + LAME path on arm64 devices.
 *
 * The ffmpeg-kit AAR only ships 64-bit ABIs (arm64-v8a / x86_64). On 32-bit
 * devices [isAvailable] returns false and the caller falls back to
 * [Mp3Transcoder].
 */
object FfmpegMp3Transcoder {

    private const val TAG = "FfmpegMp3Transcoder"

    @Volatile private var availabilityChecked = false
    @Volatile private var available = false

    fun isAvailable(): Boolean {
        if (!availabilityChecked) {
            available = try {
                !FFmpegKitConfig.getVersion().isNullOrEmpty()
            } catch (t: Throwable) {
                Log.w(TAG, "ffmpeg-kit unavailable on this device: ${t.message}")
                false
            } finally {
                availabilityChecked = true
            }
        }
        return available
    }

    /**
     * Converts [inputPath] (AAC/M4A) into an MP3 at [outputPath] in one native
     * pass. Progress (%) is derived from the MP3 file size versus the expected
     * CBR size — ffmpeg writes the file streaming, so this tracks progress
     * without needing its statistics/progress hooks.
     *
     * @return `true` when the command exit code was success and the output exists.
     */
    fun transcode(
        inputPath: String,
        outputPath: String,
        bitrateKbps: Int,
        progress: (Float) -> Unit
    ): Boolean {
        val outFile = File(outputPath)
        val expectedBytes = estimateCbrBytes(inputPath, bitrateKbps)
        var lastPct = -1

        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val poller = expectedBytes?.let { total ->
            Thread({
                try {
                    while (running.get()) {
                        val size = outFile.length()
                        val pct = (size * 100L / total).toInt()
                        if (pct > lastPct) {
                            lastPct = pct
                            progress(pct.coerceIn(0, 99).toFloat())
                        }
                        Thread.sleep(200)
                    }
                } catch (_: InterruptedException) {
                    // Woken by the finally block to stop polling after the session ends.
                }
            }, "yvd-ffmpeg-progress").apply { isDaemon = true; start() }
        }

        val command = arrayOf(
            "-y", "-hide_banner", "-nostdin",
            "-i", inputPath,
            "-vn",            // drop the embedded album-art/video track
            "-c:a", "libmp3lame",
            "-b:a", "${bitrateKbps.coerceIn(64, 320)}k",
            "-compression_level", "9", // LAME fast preset — big speedup, negligible at ≥128k
            "-map_metadata", "-1", // strip source metadata; tagged later
            outputPath
        )

        val startNs = System.nanoTime()
        val ok = try {
            val session = FFmpegKit.executeWithArguments(command)
            val rc = session?.returnCode
            val hasOutput = outFile.exists() && outFile.length() > 0L
            if (rc == null || !rc.isValueSuccess || !hasOutput) {
                Log.e(TAG, "ffmpeg failed rc=${rc?.value}: ${session?.output?.take(400)}")
                false
            } else {
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ffmpeg crashed: ${t.message}")
            false
        } finally {
            running.set(false)
            poller?.let { it.interrupt(); it.join(500) }
        }

        val elapsed = (System.nanoTime() - startNs) / 1_000_000_000.0
        Log.i(
            TAG,
            "ffmpeg mp3 %s: %.2fs → %s (%d kbps)"
                .format(inputPath.substringAfterLast('/'), elapsed, outFile.name, bitrateKbps)
        )
        if (ok) progress(100f)
        return ok
    }

    /** Expected CBR MP3 size in bytes: bitrate × duration. */
    private fun estimateCbrBytes(inputPath: String, bitrateKbps: Int): Long? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(inputPath)
        val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
        retriever.release()
        // bitrateKbps * 1000 bits/s ÷ 8 = bytes per second.
        durMs?.let { bitrateKbps * 125L * it / 1000L }
    } catch (_: Throwable) {
        null
    }
}