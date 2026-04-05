package com.engfred.yvd.data.local

import androidx.room.TypeConverter
import com.engfred.yvd.domain.model.DownloadQueueStatus

class Converters {
    @TypeConverter
    fun fromStatus(status: DownloadQueueStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadQueueStatus =
        runCatching { DownloadQueueStatus.valueOf(value) }.getOrDefault(DownloadQueueStatus.FAILED)
}