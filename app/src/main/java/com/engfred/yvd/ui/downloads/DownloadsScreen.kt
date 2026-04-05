package com.engfred.yvd.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.domain.model.DownloadQueueStatus
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.FileThumbnail
import com.engfred.yvd.ui.components.QueueItemCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val activeQueue by viewModel.activeQueue.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Refresh library when screen becomes active
    LaunchedEffect(Unit) { viewModel.loadLibraryFiles() }

    // Back press exits selection mode in library
    BackHandler(enabled = libraryState.isSelectionMode) {
        viewModel.clearLibrarySelection()
    }

    // Library deletion dialog
    if (libraryState.deleteMode != DeleteMode.NONE) {
        val (title, text) = when (libraryState.deleteMode) {
            DeleteMode.SINGLE -> {
                val name = libraryState.singleItemToDelete?.fileName ?: "file"
                "Delete File?" to "Delete '$name'? This cannot be undone."
            }
            DeleteMode.SELECTED -> {
                val n = libraryState.selectedItems.size
                "Delete $n Item${if (n != 1) "s" else ""}?" to
                        "Delete $n file${if (n != 1) "s" else ""}? This cannot be undone."
            }
            DeleteMode.ALL   -> "Delete All Files?" to "Delete ALL downloaded files? This is permanent."
            DeleteMode.NONE  -> "" to ""
        }
        ConfirmationDialog(
            title = title, text = text, confirmText = "Delete",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // ─── Top App Bar ──────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Text(
                    text = when {
                        libraryState.isSelectionMode -> "${libraryState.selectedItems.size} Selected"
                        selectedTabIndex == 0        -> "Download Queue"
                        else                         -> "My Downloads"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (libraryState.isSelectionMode)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            ),
            navigationIcon = {
                if (libraryState.isSelectionMode) {
                    IconButton(onClick = { viewModel.clearLibrarySelection() }) {
                        Icon(Icons.Rounded.Close, "Exit selection")
                    }
                }
            },
            actions = {
                when {
                    libraryState.isSelectionMode -> {
                        IconButton(onClick = { viewModel.showDeleteSelectedDialog() }) {
                            Icon(Icons.Rounded.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    selectedTabIndex == 0 && activeQueue.any {
                        it.status == DownloadQueueStatus.FAILED ||
                                it.status == DownloadQueueStatus.CANCELLED
                    } -> {
                        IconButton(onClick = { viewModel.clearFinishedJobs() }) {
                            Icon(Icons.Rounded.DeleteSweep, "Clear failed")
                        }
                    }
                    selectedTabIndex == 1 && libraryState.files.isNotEmpty() -> {
                        IconButton(onClick = { viewModel.showDeleteAllDialog() }) {
                            Icon(Icons.Rounded.DeleteForever, "Delete all")
                        }
                    }
                }
            }
        )

        // ─── Tab Row ──────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Queue", fontWeight = FontWeight.SemiBold) },
                icon = {
                    if (activeQueue.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(activeQueue.size.toString()) } }) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(22.dp))
                    }
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1; viewModel.loadLibraryFiles() },
                text = { Text("Library", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Rounded.VideoLibrary, null, modifier = Modifier.size(22.dp)) }
            )
        }

        // ─── Tab Content ──────────────────────────────────────────────────────
        when (selectedTabIndex) {
            0 -> QueueTab(queue = activeQueue, viewModel = viewModel)
            1 -> LibraryTab(state = libraryState, viewModel = viewModel)
        }
    }
}

// ─── Queue Tab ────────────────────────────────────────────────────────────────

@Composable
private fun QueueTab(
    queue: List<com.engfred.yvd.data.local.DownloadQueueEntity>,
    viewModel: DownloadsViewModel
) {
    if (queue.isEmpty()) {
        EmptyState(
            modifier = Modifier.padding(bottom = 100.dp),
            icon = { Icon(Icons.Rounded.DownloadDone, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) },
            title = "Queue is Empty",
            subtitle = "Downloads you start will appear here. Tap the YouTube button to begin."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp)
        ) {
            items(queue, key = { it.id }) { item ->
                QueueItemCard(
                    item = item,
                    onPause  = { viewModel.pauseDownload(item) },
                    onResume = { viewModel.resumeDownload(item) },
                    onCancel = { viewModel.cancelDownload(item) },
                    onRetry  = { viewModel.retryDownload(item) }
                )
            }
        }
    }
}

// ─── Library Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTab(state: LibraryState, viewModel: DownloadsViewModel) {
    if (state.files.isEmpty()) {
        EmptyState(
            modifier = Modifier.padding(bottom = 100.dp),
            icon = { Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) },
            title = "No Downloads Yet",
            subtitle = "Your downloaded videos and music will appear here, safe and sound."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.files, key = { it.file.absolutePath }) { item ->
                val isSelected = state.selectedItems.contains(item)
                LibraryItemCard(
                    item = item,
                    isSelected = isSelected,
                    isSelectionMode = state.isSelectionMode,
                    onTap = {
                        if (state.isSelectionMode) viewModel.toggleSelection(item)
                        else viewModel.playFile(item)
                    },
                    onLongPress = {
                        if (!state.isSelectionMode) viewModel.selectSingleItemForLongPress(item)
                        else viewModel.toggleSelection(item)
                    },
                    onPlay   = { viewModel.playFile(item) },
                    onShare  = { viewModel.shareFile(item) },
                    onDelete = { viewModel.showDeleteSingleDialog(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryItemCard(
    item: DownloadItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Text("${item.file.extension.uppercase()} • ${item.sizeLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingContent = {
                Box(contentAlignment = Alignment.Center) {
                    FileThumbnail(
                        file = item.file,
                        isAudio = item.isAudio,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            },
            trailingContent = {
                if (!isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Rounded.PlayCircleFilled, "Play", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Rounded.Share, "Share", Modifier.size(22.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        )
    }
}

// ─── Shared Empty State ───────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
    }
}