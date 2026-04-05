package com.engfred.yvd.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.engfred.yvd.TAG_DOWNLOAD_JOB
import com.engfred.yvd.common.Resource
import com.engfred.yvd.data.local.DownloadQueueEntity
import com.engfred.yvd.domain.model.AppTheme
import com.engfred.yvd.domain.model.DownloadQueueStatus
import com.engfred.yvd.domain.model.PlaylistMetadata
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.DownloadQueueRepository
import com.engfred.yvd.domain.repository.ThemeRepository
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.engfred.yvd.util.UrlValidator
import com.engfred.yvd.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class HomeState(
    // URL input
    val urlInput: String = "",
    val urlError: String? = null,
    // Metadata loading
    val isLoading: Boolean = false,
    val videoMetadata: VideoMetadata? = null,
    // Playlist
    val isPlaylistUrl: Boolean = false,
    val playlistMetadata: PlaylistMetadata? = null,
    // Global state
    val error: String? = null,
    val activeDownloadCount: Int = 0,
    val queuedSnackbarMessage: String? = null,
    // Dialogs
    val isFormatDialogVisible: Boolean = false,
    val isThemeDialogVisible: Boolean = false,
    val isPlaylistFormatDialogVisible: Boolean = false,
    // Incoming URL guard (triggers when user pastes a new link while queue is active)
    val pendingIncomingUrl: String? = null,
    val showActiveDownloadGuardDialog: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YoutubeRepository,
    private val queueRepository: DownloadQueueRepository,
    private val themeRepository: ThemeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    val currentTheme = themeRepository.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppTheme.SYSTEM
    )

    init {
        observeActiveDownloadCount()
    }

    private fun observeActiveDownloadCount() {
        viewModelScope.launch {
            queueRepository.observeActiveQueue().collect { queue ->
                _state.update {
                    it.copy(
                        activeDownloadCount = queue.count { item ->
                            item.status == DownloadQueueStatus.RUNNING ||
                                    item.status == DownloadQueueStatus.QUEUED
                        }
                    )
                }
            }
        }
    }

    // ─── URL Input ─────────────────────────────────────────────────────────────

    fun onUrlInputChanged(newUrl: String) {
        _state.update {
            it.copy(
                urlInput = newUrl,
                urlError = null,
                videoMetadata = if (newUrl.isBlank()) null else it.videoMetadata,
                playlistMetadata = if (newUrl.isBlank()) null else it.playlistMetadata,
                isPlaylistUrl = false,
                error = null
            )
        }
    }

    // ─── Metadata Loading ──────────────────────────────────────────────────────

    fun loadVideoInfo(url: String) {
        val sanitized = UrlValidator.sanitize(url)
        if (sanitized != _state.value.urlInput) {
            _state.update { it.copy(urlInput = sanitized) }
        }

        if (UrlValidator.isPlaylistUrl(sanitized)) {
            loadPlaylistInfo(sanitized)
            return
        }

        if (!UrlValidator.isValidYouTubeUrl(sanitized)) {
            _state.update {
                it.copy(urlError = "Please paste a valid YouTube link (youtube.com or youtu.be)")
            }
            return
        }

        _state.update {
            it.copy(
                isLoading = true, urlError = null, error = null,
                videoMetadata = null, playlistMetadata = null, isPlaylistUrl = false
            )
        }

        repository.getVideoMetadata(sanitized)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> _state.update { it.copy(isLoading = true) }
                    is Resource.Success -> _state.update { it.copy(isLoading = false, videoMetadata = result.data) }
                    is Resource.Error   -> _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadPlaylistInfo(url: String) {
        _state.update {
            it.copy(
                isLoading = true, urlError = null, error = null,
                videoMetadata = null, playlistMetadata = null, isPlaylistUrl = true
            )
        }
        repository.getPlaylistMetadata(url)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> _state.update { it.copy(isLoading = true) }
                    is Resource.Success -> _state.update { it.copy(isLoading = false, playlistMetadata = result.data) }
                    is Resource.Error   -> _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
            .launchIn(viewModelScope)
    }

    // ─── Single Video Download ─────────────────────────────────────────────────

    fun downloadMedia(formatId: String, isAudio: Boolean) {
        val currentState = _state.value
        val url          = currentState.urlInput
        val title        = currentState.videoMetadata?.title ?: "video"
        val thumbnailUrl = currentState.videoMetadata?.thumbnailUrl ?: ""

        val queueItemId = UUID.randomUUID().toString()
        val entity = DownloadQueueEntity(
            id             = queueItemId,
            videoUrl       = url,
            videoTitle     = title,
            thumbnailUrl   = thumbnailUrl,
            formatId       = formatId,
            isAudio        = isAudio,
            workManagerId  = null,
            status         = DownloadQueueStatus.QUEUED,
            progress       = 0f,
            statusText     = "Queued…",
            errorMessage   = null,
            outputFilePath = null,
            createdAt      = System.currentTimeMillis(),
            playlistTitle  = null
        )

        viewModelScope.launch {
            queueRepository.enqueue(entity)
            val workId = enqueueWorker(queueItemId, url, formatId, title, isAudio)
            queueRepository.updateStatusAndWorkId(
                queueItemId, DownloadQueueStatus.QUEUED, workId, "Queued…"
            )
            _state.update {
                it.copy(
                    isFormatDialogVisible = false,
                    queuedSnackbarMessage = "Added to download queue"
                )
            }
        }
    }

    // ─── Playlist Download ────────────────────────────────────────────────────

    /**
     * Enqueues every video in the current playlist for download.
     *
     * **Design decisions:**
     *
     * 1. All [DownloadQueueEntity] rows are inserted in a single `enqueueAll()` call so the
     *    queue UI updates atomically — the user sees all items appear at once rather than
     *    watching them trickle in one at a time.
     *
     * 2. WorkManager jobs are created on the IO dispatcher and their IDs written back to Room
     *    immediately.  The actual workers are gated by the fixed-thread-pool executor cap
     *    configured in [com.engfred.yvd.YVDApplication] (MAX_CONCURRENT_WORKERS = 3), so only
     *    3 workers run simultaneously regardless of playlist size.  All other jobs sit in
     *    WorkManager's internal queue — no ANR risk.
     *
     * 3. `createdAt` offsets ensure playlist order is preserved in the queue UI.
     */
    fun downloadEntirePlaylist(formatId: String, isAudio: Boolean) {
        val playlist = _state.value.playlistMetadata ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // Build all entities up front so we can insert them atomically.
            val entities = playlist.videos.mapIndexed { index, video ->
                DownloadQueueEntity(
                    id             = UUID.randomUUID().toString(),
                    videoUrl       = video.url,
                    videoTitle     = video.title,
                    thumbnailUrl   = video.thumbnailUrl,
                    formatId       = formatId,
                    isAudio        = isAudio,
                    workManagerId  = null,
                    status         = DownloadQueueStatus.QUEUED,
                    progress       = 0f,
                    statusText     = "Queued…",
                    errorMessage   = null,
                    outputFilePath = null,
                    createdAt      = now + index,  // unique offset preserves playlist order
                    playlistTitle  = playlist.title
                )
            }

            // Single atomic Room insert — queue UI reflects all items immediately.
            queueRepository.enqueueAll(entities)

            // Enqueue WorkManager jobs and persist their IDs back to Room.
            // Executed on IO dispatcher since WorkManager.enqueue() touches disk.
            // The WorkManager executor cap (3 threads) ensures only 3 workers actually
            // run simultaneously regardless of how many are enqueued here.
            withContext(Dispatchers.IO) {
                entities.forEach { entity ->
                    val workId = enqueueWorker(
                        queueItemId = entity.id,
                        url         = entity.videoUrl,
                        formatId    = entity.formatId,
                        title       = entity.videoTitle,
                        isAudio     = entity.isAudio
                    )
                    // Update each item's workManagerId so the pause/cancel actions
                    // can target the correct WorkManager job by UUID.
                    queueRepository.updateStatusAndWorkId(
                        id         = entity.id,
                        status     = DownloadQueueStatus.QUEUED,
                        workId     = workId,
                        statusText = "Queued…"
                    )
                }
            }

            _state.update {
                it.copy(
                    isPlaylistFormatDialogVisible = false,
                    queuedSnackbarMessage = "${playlist.videoCount} videos added to queue"
                )
            }
        }
    }

    // ─── WorkManager ──────────────────────────────────────────────────────────

    /**
     * Builds and submits a [DownloadWorker] request to WorkManager.
     *
     * @return The WorkManager request UUID as a string, stored in Room so that pause/cancel
     *         actions can target the exact job via [WorkManager.cancelWorkById].
     */
    private fun enqueueWorker(
        queueItemId: String,
        url: String,
        formatId: String,
        title: String,
        isAudio: Boolean
    ): String {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    "queueItemId" to queueItemId,
                    "url"         to url,
                    "formatId"    to formatId,
                    "title"       to title,
                    "isAudio"     to isAudio
                )
            )
            .addTag(TAG_DOWNLOAD_JOB)
            .build()

        workManager.enqueue(request)
        return request.id.toString()
    }

    // ─── Incoming URL Guard ───────────────────────────────────────────────────

    fun handleIncomingUrl(url: String) {
        if (_state.value.activeDownloadCount > 0) {
            _state.update { it.copy(pendingIncomingUrl = url, showActiveDownloadGuardDialog = true) }
            return
        }
        applyIncomingUrl(url)
    }

    fun confirmReplaceWithPendingUrl() {
        val url = _state.value.pendingIncomingUrl ?: return
        _state.update { it.copy(showActiveDownloadGuardDialog = false, pendingIncomingUrl = null) }
        applyIncomingUrl(url)
    }

    fun dismissGuardDialog() {
        _state.update { it.copy(showActiveDownloadGuardDialog = false, pendingIncomingUrl = null) }
    }

    private fun applyIncomingUrl(url: String) {
        _state.update {
            it.copy(
                urlInput      = url,
                urlError      = null,
                videoMetadata = null,
                playlistMetadata = null,
                isPlaylistUrl = false,
                error         = null
            )
        }
        loadVideoInfo(url)
    }

    // ─── Dialog Visibility ────────────────────────────────────────────────────

    fun showFormatDialog() {
        if (_state.value.videoMetadata != null) _state.update { it.copy(isFormatDialogVisible = true) }
    }

    fun hideFormatDialog() = _state.update { it.copy(isFormatDialogVisible = false) }

    fun showPlaylistFormatDialog() {
        if (_state.value.playlistMetadata != null) _state.update { it.copy(isPlaylistFormatDialogVisible = true) }
    }

    fun hidePlaylistFormatDialog() = _state.update { it.copy(isPlaylistFormatDialogVisible = false) }

    fun showThemeDialog() = _state.update { it.copy(isThemeDialogVisible = true) }

    fun hideThemeDialog() = _state.update { it.copy(isThemeDialogVisible = false) }

    fun updateTheme(newTheme: AppTheme) {
        viewModelScope.launch {
            themeRepository.setTheme(newTheme)
            hideThemeDialog()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearQueuedMessage() = _state.update { it.copy(queuedSnackbarMessage = null) }
}