package com.engfred.yvd.data.repository

import com.engfred.yvd.data.local.AppDatabase
import com.engfred.yvd.data.local.DownloadQueueEntity
import com.engfred.yvd.domain.model.DownloadQueueStatus
import com.engfred.yvd.domain.repository.DownloadQueueRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueueRepositoryImpl @Inject constructor(
    db: AppDatabase
) : DownloadQueueRepository {

    private val dao = db.downloadQueueDao()

    override fun observeActiveQueue(): Flow<List<DownloadQueueEntity>> = dao.observeActiveQueue()

    override suspend fun enqueue(item: DownloadQueueEntity) = dao.insert(item)
    override suspend fun enqueueAll(items: List<DownloadQueueEntity>) = dao.insertAll(items)

    override suspend fun updateStatusAndWorkId(id: String, status: DownloadQueueStatus, workId: String?, statusText: String) =
        dao.updateStatusAndWorkId(id, status.name, workId, statusText)

    override suspend fun updateProgress(id: String, status: DownloadQueueStatus, progress: Float, statusText: String) =
        dao.updateProgress(id, status.name, progress, statusText)

    override suspend fun markDone(id: String, filePath: String) = dao.markDone(id, filePath)
    override suspend fun markFailed(id: String, error: String) = dao.markFailed(id, error)
    override suspend fun markPaused(id: String) = dao.markPaused(id)
    override suspend fun getById(id: String): DownloadQueueEntity? = dao.getById(id)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
    override suspend fun clearFinished() = dao.clearFinished()
}