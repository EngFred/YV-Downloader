package com.engfred.yvd.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.engfred.yvd.domain.model.DownloadQueueStatus

@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val id: String,
    val videoUrl: String,
    val videoTitle: String,
    val thumbnailUrl: String,
    val formatId: String,
    val isAudio: Boolean,
    val workManagerId: String?,
    val status: DownloadQueueStatus,
    val progress: Float,
    val statusText: String,
    val errorMessage: String?,
    val outputFilePath: String?,
    val createdAt: Long,
    val playlistTitle: String?
)