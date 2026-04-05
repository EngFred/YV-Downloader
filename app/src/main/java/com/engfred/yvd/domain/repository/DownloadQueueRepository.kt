package com.engfred.yvd.domain.repository

import com.engfred.yvd.data.local.DownloadQueueEntity
import com.engfred.yvd.domain.model.DownloadQueueStatus
import kotlinx.coroutines.flow.Flow

interface DownloadQueueRepository {
    fun observeActiveQueue(): Flow<List<DownloadQueueEntity>>
    suspend fun enqueue(item: DownloadQueueEntity)
    suspend fun enqueueAll(items: List<DownloadQueueEntity>)
    suspend fun updateStatusAndWorkId(id: String, status: DownloadQueueStatus, workId: String?, statusText: String)
    suspend fun updateProgress(id: String, status: DownloadQueueStatus, progress: Float, statusText: String)
    suspend fun markDone(id: String, filePath: String)
    suspend fun markFailed(id: String, error: String)
    suspend fun markPaused(id: String)
    suspend fun getById(id: String): DownloadQueueEntity?
    suspend fun deleteById(id: String)
    suspend fun clearFinished()
}