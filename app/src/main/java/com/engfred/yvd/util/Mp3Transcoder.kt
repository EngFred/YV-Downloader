package com.engfred.yvd.util

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import jaygoo.library.converter.Mp3Converter
import java.io.File
import java.io.FileOutputStream

/**
 * Transcodes an M4A/AAC file to MP3 entirely on-device:
 *
 *   1. [MediaCodec] decodes AAC → 16-bit PCM (`android.media` built-in decoder).
 *   2. LAME (via the Jay-Goo `Mp3Converter` JNI wrapper) re-encodes the PCM → MP3.
 *
 * This is necessary because Android's AOSP software MP3 encoder was removed on
 * Android 13+, so on-device MP3 conversion must go through LAME.
 *
 * The process runs synchronously — call from a background thread and feed
 * [progress] callbacks for UI updates.
 */
class Mp3Transcoder {

    class InvalidAudioException(message: String) : Exception(message)

    /**
     * Converts [inputPath] (AAC/M4A) into MP3 at [outputPath].
     *
     * @return `true` when the MP3 was written (file exists and is non-empty).
     * @throws InvalidAudioException for unsupported input (channels, bit depth, no track).
     */
    @Suppress("DEPRECATION") // MediaCodec.inputBuffers/outputBuffers are still valid for sync mode.
    fun transcode(
        inputPath: String,
        outputPath: String,
        bitrateKbps: Int,
        progress: (Float) -> Unit
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var out: FileOutputStream? = null
        var lameInitialized = false
        var lameClosed = false

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            val trackIndex = findAudioTrack(extractor)
                ?: throw InvalidAudioException("No audio track found in source")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw InvalidAudioException("Unknown audio codec in source")
            val sampleRate = format.safeInt(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channels = format.safeInt(MediaFormat.KEY_CHANNEL_COUNT, 2)

            if (channels !in 1..2) {
                throw InvalidAudioException("Unsupported channel count: $channels")
            }
            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                throw InvalidAudioException("Source is already raw PCM")
            }

            val pcmEncoding = format.safeInt(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT
            )

            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val inputBuffers = decoder.inputBuffers
            val outputBuffers = decoder.outputBuffers
            val info = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false

            val totalSamples: Long =
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION) * sampleRate / 1_000_000L
                } else -1L
            var encodedSamples = 0L
            var lastPct = -1

            Mp3Converter.init(
                sampleRate, channels,
                MODE_CBR, sampleRate,
                bitrateKbps.coerceIn(64, 320), QUALITY_GOOD
            )
            lameInitialized = true
            Log.d(TAG, "LAME init: ${sampleRate}Hz ${channels}ch ${bitrateKbps}kbps")

            out = FileOutputStream(outputPath)

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuf = inputBuffers[inputIndex]
                        val size = extractor.readSampleData(inputBuf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                    outputIndex >= 0 -> {
                        if (info.size > 0) {
                            val pcmBuf = outputBuffers[outputIndex]
                            pcmBuf.position(info.offset)
                            pcmBuf.limit(info.offset + info.size)

                            val samplesPerChannel = info.size / (2 * channels)
                            val shorts = toShorts(pcmBuf, samplesPerChannel * channels, pcmEncoding)

                            // mp3buf must be at least "7200 + 1.25 * samples" bytes.
                            val mp3Buf = ByteArray((1.25 * samplesPerChannel + 7200).toInt())
                            val encoded = encodeChunk(shorts, samplesPerChannel, channels, mp3Buf)
                            if (encoded < 0) {
                                throw InvalidAudioException("MP3 encoder error (code=$encoded)")
                            }
                            if (encoded > 0) out.write(mp3Buf, 0, encoded)

                            encodedSamples += samplesPerChannel
                            if (totalSamples > 0) {
                                val pct = (encodedSamples * 100L / totalSamples).toInt().coerceIn(0, 99)
                                if (pct > lastPct) {
                                    lastPct = pct
                                    progress(pct.toFloat())
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(outputIndex, false)

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            // ── Flush LAME's internal PCM/MP3 buffers ──────────────────────────
            // Do NOT call Mp3Converter.close() here — the finally block closes the
            // session exactly once.  Calling close() twice frees LAME's global state
            // twice and native code segfaults, killing the app.
            val flushBuf = ByteArray(7200)
            while (true) {
                val n = Mp3Converter.flush(flushBuf)
                if (n <= 0) break
                out.write(flushBuf, 0, n)
            }

            out.flush()

            val finalFile = File(outputPath)
            return finalFile.exists() && finalFile.length() > 0L

        } finally {
            try { out?.flush(); out?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor?.let {
                try { it.release() } catch (_: Exception) {}
            }
            // Close the LAME session at most once, and only if init succeeded.
            if (lameInitialized && !lameClosed) {
                lameClosed = true
                runCatching { Mp3Converter.close() }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun encodeChunk(
        pcmShorts: ShortArray,
        samples: Int,
        channels: Int,
        mp3Buf: ByteArray
    ): Int {
        if (channels == 2) {
            val left = ShortArray(samples)
            val right = ShortArray(samples)
            for (i in 0 until samples) {
                left[i] = pcmShorts[i * 2]
                right[i] = pcmShorts[i * 2 + 1]
            }
            return Mp3Converter.encode(left, right, samples, mp3Buf)
        }
        // Mono: LAME ignores the right channel — pass the same buffer for both.
        return Mp3Converter.encode(pcmShorts, pcmShorts, samples, mp3Buf)
    }

    private fun toShorts(buffer: java.nio.ByteBuffer, count: Int, encoding: Int): ShortArray {
        val shorts = ShortArray(count)
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = FloatArray(count)
            buffer.asFloatBuffer().get(floats)
            for (i in floats.indices) {
                val clamped = floats[i].coerceIn(-1f, 1f)
                shorts[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }
        } else {
            buffer.asShortBuffer().get(shorts)
        }
        return shorts
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun MediaFormat.safeInt(key: String, def: Int): Int =
        if (containsKey(key)) getInteger(key) else def

    companion object {
        private const val TAG = "Mp3Transcoder"
        // LAME mode 0 = CBR; quality 5 = good quality, fast.
        private const val MODE_CBR = 0
        private const val QUALITY_GOOD = 5
    }
}