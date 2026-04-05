package com.engfred.yvd.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.engfred.yvd.TAG_DOWNLOAD_JOB
import com.engfred.yvd.data.local.DownloadQueueEntity
import com.engfred.yvd.data.repository.DownloadsRepository
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.domain.model.DownloadQueueStatus
import com.engfred.yvd.domain.repository.DownloadQueueRepository
import com.engfred.yvd.util.MediaHelper
import com.engfred.yvd.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class DeleteMode { NONE, SINGLE, SELECTED, ALL }

data class LibraryState(
    val files: List<DownloadItem> = emptyList(),
    val selectedItems: Set<DownloadItem> = emptySet(),
    val deleteMode: DeleteMode = DeleteMode.NONE,
    val singleItemToDelete: DownloadItem? = null
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val queueRepository: DownloadQueueRepository,
    private val downloadsRepository: DownloadsRepository,
    private val mediaHelper: MediaHelper,
    private val workManager: WorkManager
) : ViewModel() {

    // Live queue driven by Room — automatically updates UI on any change
    val activeQueue = queueRepository.observeActiveQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState = _libraryState.asStateFlow()

    init {
        loadLibraryFiles()
    }

    // ─── Queue Actions ─────────────────────────────────────────────────────────

    fun pauseDownload(item: DownloadQueueEntity) {
        val workId = item.workManagerId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return

        // Cancelling the WorkManager job triggers the worker's isStopped path,
        // which then calls queueRepository.markPaused() via NonCancellable context.
        workManager.cancelWorkById(workId)

        // Fallback: if the worker doesn't update Room (e.g. it hadn't started yet), we do it here
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            val current = queueRepository.getById(item.id)
            if (current?.status == DownloadQueueStatus.RUNNING ||
                current?.status == DownloadQueueStatus.QUEUED) {
                queueRepository.markPaused(item.id)
            }
        }
    }

    fun resumeDownload(item: DownloadQueueEntity) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                workDataOf(
                    "queueItemId" to item.id,
                    "url"         to item.videoUrl,
                    "formatId"    to item.formatId,
                    "title"       to item.videoTitle,
                    "isAudio"     to item.isAudio
                )
            )
            .addTag(TAG_DOWNLOAD_JOB)
            .build()

        viewModelScope.launch {
            queueRepository.updateStatusAndWorkId(
                item.id, DownloadQueueStatus.QUEUED, request.id.toString(), "Queued…"
            )
        }
        workManager.enqueue(request)
    }

    /** Retry failed item — identical to resume since ResumeStateStore handles partial progress */
    fun retryDownload(item: DownloadQueueEntity) = resumeDownload(item)

    fun cancelDownload(item: DownloadQueueEntity) {
        item.workManagerId?.let { id ->
            runCatching { UUID.fromString(id) }.getOrNull()
                ?.let { workManager.cancelWorkById(it) }
        }
        viewModelScope.launch { queueRepository.deleteById(item.id) }
    }

    fun clearFinishedJobs() {
        viewModelScope.launch { queueRepository.clearFinished() }
    }

    // ─── Library ───────────────────────────────────────────────────────────────

    fun loadLibraryFiles() {
        viewModelScope.launch {
            val files = downloadsRepository.getDownloadedFiles()
            _libraryState.update { it.copy(files = files) }
        }
    }

    fun playFile(item: DownloadItem) {
        if (_libraryState.value.isSelectionMode) return
        runCatching { mediaHelper.openMediaFile(item.file) }
    }

    fun shareFile(item: DownloadItem) {
        runCatching { mediaHelper.shareMediaFile(item.file) }
    }

    // ─── Library Selection ────────────────────────────────────────────────────

    fun toggleSelection(item: DownloadItem) {
        _libraryState.update {
            val updated = it.selectedItems.toMutableSet().also { set ->
                if (!set.add(item)) set.remove(item)
            }
            it.copy(selectedItems = updated)
        }
    }

    fun selectSingleItemForLongPress(item: DownloadItem) =
        _libraryState.update { it.copy(selectedItems = setOf(item)) }

    fun clearLibrarySelection() =
        _libraryState.update { it.copy(selectedItems = emptySet()) }

    // ─── Library Deletion ─────────────────────────────────────────────────────

    fun showDeleteSingleDialog(item: DownloadItem) =
        _libraryState.update { it.copy(deleteMode = DeleteMode.SINGLE, singleItemToDelete = item) }

    fun showDeleteSelectedDialog() {
        if (_libraryState.value.selectedItems.isNotEmpty())
            _libraryState.update { it.copy(deleteMode = DeleteMode.SELECTED) }
    }

    fun showDeleteAllDialog() {
        if (_libraryState.value.files.isNotEmpty())
            _libraryState.update { it.copy(deleteMode = DeleteMode.ALL) }
    }

    fun dismissDeleteDialog() =
        _libraryState.update { it.copy(deleteMode = DeleteMode.NONE, singleItemToDelete = null) }

    fun confirmDelete() {
        val state = _libraryState.value
        viewModelScope.launch {
            when (state.deleteMode) {
                DeleteMode.SINGLE   -> state.singleItemToDelete?.file?.delete()
                DeleteMode.SELECTED -> state.selectedItems.forEach { it.file.delete() }
                DeleteMode.ALL      -> state.files.forEach { it.file.delete() }
                DeleteMode.NONE     -> Unit
            }
            clearLibrarySelection()
            dismissDeleteDialog()
            loadLibraryFiles()
        }
    }
}