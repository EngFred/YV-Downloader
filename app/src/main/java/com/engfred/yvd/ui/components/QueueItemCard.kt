package com.engfred.yvd.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.engfred.yvd.data.local.DownloadQueueEntity
import com.engfred.yvd.domain.model.DownloadQueueStatus

@Composable
fun QueueItemCard(
    item: DownloadQueueEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (item.progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "QueueProgress"
    )

    // Softer background highlights for flat list design
    val backgroundColor = when (item.status) {
        DownloadQueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        DownloadQueueStatus.PAUSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Audio/Video badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (item.isAudio) "M4A" else "MP4",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info + Progress
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = item.videoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Playlist name (if applicable)
                item.playlistTitle?.let {
                    Text(
                        text = "Playlist: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Status text + progress
                val statusColor = when (item.status) {
                    DownloadQueueStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadQueueStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    DownloadQueueStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.status == DownloadQueueStatus.RUNNING || item.status == DownloadQueueStatus.PAUSED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (item.status == DownloadQueueStatus.RUNNING && item.progress <= 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceDim,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceDim,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // Action buttons row
                Spacer(modifier = Modifier.height(6.dp))
                AnimatedContent(targetState = item.status, label = "QueueActions") { status ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (status) {
                            DownloadQueueStatus.RUNNING -> {
                                SmallActionButton("Pause", Icons.Rounded.Pause, MaterialTheme.colorScheme.secondary, onPause)
                                SmallActionButton("Cancel", Icons.Rounded.Close, MaterialTheme.colorScheme.error, onCancel)
                            }
                            DownloadQueueStatus.QUEUED -> {
                                SmallActionButton("Cancel", Icons.Rounded.Close, MaterialTheme.colorScheme.error, onCancel)
                            }
                            DownloadQueueStatus.PAUSED -> {
                                SmallActionButton("Resume", Icons.Rounded.PlayArrow, MaterialTheme.colorScheme.primary, onResume)
                                SmallActionButton("Cancel", Icons.Rounded.Close, MaterialTheme.colorScheme.error, onCancel)
                            }
                            DownloadQueueStatus.FAILED -> {
                                SmallActionButton("Retry", Icons.Rounded.Refresh, MaterialTheme.colorScheme.primary, onRetry)
                                SmallActionButton("Remove", Icons.Rounded.DeleteOutline, MaterialTheme.colorScheme.error, onCancel)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Clean separator between list items
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(30.dp),
        border = BorderStroke(width = 1.dp, color = tintColor.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tintColor)
    ) {
        Icon(icon, null, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}