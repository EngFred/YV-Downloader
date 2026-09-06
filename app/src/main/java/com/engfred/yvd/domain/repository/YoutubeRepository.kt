package com.engfred.yvd.domain.repository

import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.AudioContainer
import com.engfred.yvd.domain.model.DownloadStatus
import com.engfred.yvd.domain.model.PlaylistMetadata
import com.engfred.yvd.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface YoutubeRepository {
    fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>>

    /**
     * Downloads a video or audio stream.
     *
     * @param formatId    Source ITAG (or QUALITY_* descriptor for playlists). Ignored for
     *                    [AudioContainer.MP3], which transcodes the best audio stream.
     * @param container   Target container for audio: [AudioContainer.M4A] (original AAC) or
     *                    [AudioContainer.MP3] (device-side transcode). Null for video.
     * @param bitrateKbps Target bitrate for MP3 output (only used with [AudioContainer.MP3]).
     */
    fun downloadVideo(
        url: String,
        formatId: String,
        title: String,
        isAudio: Boolean,
        container: AudioContainer? = null,
        bitrateKbps: Int? = null
    ): Flow<DownloadStatus>

    fun getPlaylistMetadata(url: String): Flow<Resource<PlaylistMetadata>>
}