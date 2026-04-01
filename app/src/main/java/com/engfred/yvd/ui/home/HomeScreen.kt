package com.engfred.yvd.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.ui.components.*
import com.engfred.yvd.util.openYoutube

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Enable notifications to track downloads", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    if (state.isCancelDialogVisible) {
        ConfirmationDialog(
            title = "Cancel Download?",
            text = "The partial download will be saved. You can resume it later.",
            confirmText = "Yes, Cancel",
            onConfirm = { viewModel.cancelDownload() },
            onDismiss = { viewModel.hideCancelDialog() }
        )
    }

    if (state.isThemeDialogVisible) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { viewModel.updateTheme(it) },
            onDismiss = { viewModel.hideThemeDialog() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            BadgedBox(
                modifier = Modifier.padding(bottom = 88.dp), // FIX 3: Pushes the FAB up so it doesn't hide behind the custom Nav Pill
                badge = {
                    if (state.activeDownloadCount > 1) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(state.activeDownloadCount.toString()) }
                    }
                }
            ) {
                FloatingActionButton(
                    onClick = { openYoutube(context) },
                    containerColor = Color(0xFFFF0000), // YouTube Red
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Rounded.SmartDisplay, contentDescription = "Open YouTube", modifier = Modifier.size(28.dp))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("YV Downloader", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    // FIX 1: Replaced the clunky icon with a premium soft circular Palette button
                    IconButton(
                        onClick = { viewModel.showThemeDialog() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Change theme", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Premium Custom Input Field
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                TextField(
                    value = state.urlInput,
                    onValueChange = { viewModel.onUrlInputChanged(it) },
                    placeholder = { Text("Paste YouTube link here...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        Row {
                            if (state.urlInput.isNotBlank()) {
                                IconButton(onClick = { viewModel.onUrlInputChanged("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    keyboardController?.hide()
                                    viewModel.loadVideoInfo(clip)
                                }
                            }) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(visible = state.urlError != null) {
                Text(
                    text = state.urlError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp).fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = state.videoMetadata == null && !state.isDownloading && !state.downloadComplete && !state.downloadFailed
            ) {
                // FIX 2: Removed custom .shadow() modifier and used proper Button elevation to fix UI glitch
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.loadVideoInfo(state.urlInput)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
                    enabled = !state.isLoading && state.urlInput.isNotBlank()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Fetching Magic...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Get Video Info", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Beautiful Empty State
            AnimatedVisibility(
                visible = state.videoMetadata == null && !state.isDownloading && !state.downloadComplete && !state.downloadFailed && !state.isLoading,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Ready to Download?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tap the red button to open YouTube, grab a link, and paste it above to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            state.videoMetadata?.let { metadata ->
                Spacer(modifier = Modifier.height(16.dp))
                VideoCard(
                    metadata = metadata,
                    isDownloading = state.isDownloading,
                    onDownloadClick = { viewModel.showFormatDialog() }
                )
            }

            AnimatedVisibility(visible = state.isDownloading || state.downloadComplete || state.downloadFailed) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    DownloadProgressCard(
                        statusText = state.downloadStatusText,
                        progress = state.downloadProgress,
                        isDownloading = state.isDownloading,
                        isComplete = state.downloadComplete,
                        isFailed = state.downloadFailed,
                        isAudio = state.isAudio,
                        onCancel = { viewModel.showCancelDialog() },
                        onPlay = { viewModel.openMediaFile() },
                        onShare = { viewModel.shareMediaFile() },
                        onRetry = { viewModel.retryDownload() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(120.dp)) // Extra space for the new floating nav bar
        }
    }

    if (state.isFormatDialogVisible && state.videoMetadata != null) {
        FormatSelectionSheet(
            metadata = state.videoMetadata!!,
            onDismiss = { viewModel.hideFormatDialog() },
            onFormatSelected = { formatId, isAudio ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.downloadMedia(formatId, isAudio)
            }
        )
    }
}