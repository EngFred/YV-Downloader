package com.engfred.yvd.domain.model

data class PlaylistMetadata(
    val title: String,
    val videoCount: Int,
    val videos: List<PlaylistVideoItem>
)

data class PlaylistVideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String
)