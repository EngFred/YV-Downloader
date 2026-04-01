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
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.FileThumbnail

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    // Handle Back Press to exit selection mode
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    // --- Dialog Logic ---
    if (uiState.deleteMode != DeleteMode.NONE) {
        val (title, text) = when (uiState.deleteMode) {
            DeleteMode.SINGLE -> {
                val fileName = uiState.singleItemToDelete?.fileName ?: "file"
                Pair("Delete File?", "Are you sure you want to delete '$fileName'?")
            }
            DeleteMode.SELECTED -> {
                val count = uiState.selectedItems.size
                val itemString = if (count == 1) "Item" else "Items"
                val fileString = if (count == 1) "file" else "files"
                val theseString = if (count == 1) "this" else "these"
                Pair("Delete $count $itemString?", "Are you sure you want to delete $theseString $count $fileString? This cannot be undone.")
            }
            DeleteMode.ALL -> Pair("Delete All Files?", "Are you sure you want to delete ALL downloaded files? This is permanent.")
            else -> Pair("", "")
        }

        ConfirmationDialog(
            title = title,
            text = text,
            confirmText = "Delete",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                if (uiState.isSelectionMode) {
                    Text("${uiState.selectedItems.size} Selected", fontWeight = FontWeight.Bold)
                } else {
                    Text("My Downloads", fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = if (uiState.isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                titleContentColor = if (uiState.isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = if (uiState.isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Selection")
                    }
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.showDeleteSelectedDialog() }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    if (uiState.files.isNotEmpty()) {
                        IconButton(onClick = { viewModel.showDeleteAllDialog() }) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete All")
                        }
                    }
                }
            }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (uiState.files.isEmpty()) {
                // Premium Empty State
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No Downloads Yet",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your downloaded videos and music will appear here, safe and sound.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp), // FIX: Extra bottom padding for the floating nav bar
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.files) { item ->
                        val isSelected = uiState.selectedItems.contains(item)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (uiState.isSelectionMode) viewModel.toggleSelection(item)
                                        else viewModel.playFile(item)
                                    },
                                    onLongClick = {
                                        if (!uiState.isSelectionMode) viewModel.selectSingleItemForLongPress(item)
                                        else viewModel.toggleSelection(item)
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${item.file.extension.uppercase()} • ${item.sizeLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = {
                                    Box(contentAlignment = Alignment.Center) {
                                        FileThumbnail(
                                            file = item.file,
                                            isAudio = item.isAudio,
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                        )
                                        if(isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (!uiState.isSelectionMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { viewModel.playFile(item) }) {
                                                Icon(Icons.Rounded.PlayCircleFilled, contentDescription = "Play", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.shareFile(item) }) {
                                                Icon(Icons.Rounded.Share, contentDescription = "Share", Modifier.size(22.dp))
                                            }
                                            IconButton(onClick = { viewModel.showDeleteSingleDialog(item) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    } else {
                                        RadioButton(selected = isSelected, onClick = null)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}