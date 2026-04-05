package com.engfred.yvd.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {

    // Observe everything that is NOT done/cancelled — drives the Queue tab
    @Query("SELECT * FROM download_queue WHERE status NOT IN ('DONE', 'CANCELLED') ORDER BY createdAt ASC")
    fun observeActiveQueue(): Flow<List<DownloadQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadQueueEntity>)

    // Using String for status params in @Query avoids TypeConverter edge-cases
    @Query("UPDATE download_queue SET status = :status, workManagerId = :workId, statusText = :statusText WHERE id = :id")
    suspend fun updateStatusAndWorkId(id: String, status: String, workId: String?, statusText: String)

    @Query("UPDATE download_queue SET status = :status, progress = :progress, statusText = :statusText WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float, statusText: String)

    @Query("UPDATE download_queue SET status = 'DONE', outputFilePath = :filePath, progress = 100.0, statusText = 'Complete' WHERE id = :id")
    suspend fun markDone(id: String, filePath: String)

    @Query("UPDATE download_queue SET status = 'FAILED', errorMessage = :error, statusText = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE download_queue SET status = 'PAUSED', statusText = 'Paused' WHERE id = :id")
    suspend fun markPaused(id: String)

    @Query("SELECT * FROM download_queue WHERE id = :id")
    suspend fun getById(id: String): DownloadQueueEntity?

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_queue WHERE status IN ('DONE', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished()
}