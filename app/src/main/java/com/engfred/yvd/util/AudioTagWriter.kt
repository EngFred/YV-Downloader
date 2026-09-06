package com.engfred.yvd.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Embeds title/artist metadata and album art into audio files.
 *
 * - MP3 files get an ID3v2.4 tag with an APIC artwork frame.
 * - M4A/M4B files get an MP4 (`©nam` / `©ART` / `covr`) tag.
 *
 * Everything is best-effort: a failure here (unsupported file, corrupt tags)
 * must never fail the overall download, so callers wrap calls where needed.
 */
object AudioTagWriter {

    private const val TAG = "AudioTagWriter"

    private const val ARTWORK_MAX_DIMENSION = 512

    /**
     * Writes [artist] / [song] and the optional JPEG [artworkBytes] into [file].
     */
    fun embed(file: File, artist: String, song: String, artworkBytes: ByteArray?) {
        if (artworkBytes == null && artist.isBlank() && song.isBlank()) return

        val tag = runCatching {
            val audioFile = AudioFileIO.read(file)
            val existing = audioFile.tag
            if (existing != null) {
                existing
            } else {
                val fresh = if (file.extension.equals("mp3", ignoreCase = true)) ID3v24Tag() else Mp4Tag()
                audioFile.tag = fresh
                fresh
            }
        }.getOrElse { e ->
            Log.w(TAG, "Could not read tags for ${file.name}: ${e.message}")
            return
        }

        runCatching { if (song.isNotBlank()) tag.setField(FieldKey.TITLE, song) }
        runCatching { if (artist.isNotBlank()) tag.setField(FieldKey.ARTIST, artist) }

        artworkBytes?.let { bytes ->
            runCatching {
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = bytes
                artwork.mimeType = "image/jpeg"
                artwork.description = ""
                tag.addField(artwork)
            }.onFailure { Log.w(TAG, "Could not embed artwork: ${it.message}") }
        }

        runCatching {
            val audioFile = AudioFileIO.read(file)
            audioFile.tag = tag
            audioFile.commit()
        }.onFailure { Log.w(TAG, "Could not commit tags for ${file.name}: ${it.message}") }
    }

    /**
     * Downscales raw artwork bytes (webp/jpg/png) to a centered-square JPEG
     * capped at [ARTWORK_MAX_DIMENSION], so embedded covers stay small
     * regardless of the original thumbnail dimensions.
     *
     * @return JPEG bytes, or `null` when the bytes can't be decoded.
     */
    fun prepareArtwork(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= ARTWORK_MAX_DIMENSION ||
            bounds.outHeight / (sampleSize * 2) >= ARTWORK_MAX_DIMENSION
        ) {
            sampleSize *= 2
        }

        val source = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null

        try {
            // Center-crop to a square, then scale to the cap.
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val cropped = Bitmap.createBitmap(source, left, top, side, side)
            val scale = if (side > ARTWORK_MAX_DIMENSION) {
                Bitmap.createScaledBitmap(cropped, ARTWORK_MAX_DIMENSION, ARTWORK_MAX_DIMENSION, true)
            } else cropped

            return ByteArrayOutputStream().use { baos ->
                scale.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                baos.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Artwork prepare failed: ${e.message}")
            return null
        } finally {
            if (source.isRecycled.not()) source.recycle()
        }
    }
}