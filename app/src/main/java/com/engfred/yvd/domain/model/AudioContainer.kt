package com.engfred.yvd.domain.model

/**
 * Target container for an audio download.
 *
 * [M4A] is the byte-for-byte original AAC stream from YouTube (no conversion).
 * [MP3] is a device-side transcode of the best available audio stream, producing
 * a broadly compatible .mp3 file.
 */
enum class AudioContainer(val extension: String, val label: String) {
    M4A("m4a", "M4A"),
    MP3("mp3", "MP3")
}

/**
 * A concrete download choice picked in the format sheets and threaded through
 * HomeViewModel → DownloadWorker → YoutubeRepository.
 *
 * @param formatId  The source ITAG / quality descriptor. Ignored for [AudioContainer.MP3],
 *                  which always takes the best available audio stream before transcoding.
 * @param container Only set for audio downloads (null for video).
 * @param bitrateKbps Target MP3 bitrate, only for [AudioContainer.MP3].
 */
data class FormatSelection(
    val formatId: String,
    val isAudio: Boolean,
    val container: AudioContainer? = null,
    val bitrateKbps: Int? = null
)