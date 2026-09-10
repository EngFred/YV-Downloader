package com.engfred.yvd.domain.model

/**
 * Represents a single item returned from a YouTube search query.
 *
 * @property url           Direct video/playlist/channel URL (ready for NewPipe extraction).
 * @property title         Title text shown in the search result card.
 * @property thumbnailUrl  Highest-resolution thumbnail URL.
 * @property duration      Formatted duration string (e.g. "12:34"), empty for channels.
 * @property uploaderName  Channel / uploader display name.
 * @property viewCount     Total views as a raw number; -1 when unavailable.
 * @property infoType      Whether this result is a video, channel, or playlist.
 */
data class SearchResult(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val uploaderName: String,
    val viewCount: Long,
    val infoType: InfoType
)

enum class InfoType { VIDEO, CHANNEL, PLAYLIST }
