package com.engfred.yvd.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.engfred.yvd.domain.model.PlaylistMetadata

// Quality descriptors — worker resolves these to actual ITAGs per video
const val QUALITY_BEST  = "QUALITY_BEST"
const val QUALITY_720P  = "QUALITY_720P"
const val QUALITY_480P  = "QUALITY_480P"
const val QUALITY_AUDIO = "QUALITY_AUDIO"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistFormatSheet(
    playlistMetadata: PlaylistMetadata,
    onDismiss: () -> Unit,
    onFormatSelected: (formatId: String, isAudio: Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Download Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${playlistMetadata.title} • ${playlistMetadata.videoCount} videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Text("Select Quality for All Videos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            data class Option(val id: String, val label: String, val desc: String, val isAudio: Boolean)

            val options = listOf(
                Option(QUALITY_BEST,  "Best Available", "Highest MP4 quality for each video", false),
                Option(QUALITY_720P,  "Up to 720p",    "Balanced quality and file size",      false),
                Option(QUALITY_480P,  "Up to 480p",    "Smaller files, good for mobile data", false),
                Option(QUALITY_AUDIO, "Audio Only",    "M4A, best available bitrate per track",true),
            )

            options.forEach { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onFormatSelected(option.id, option.isAudio) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(option.label, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(option.desc) },
                        leadingContent = {
                            Icon(
                                imageVector = if (option.isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.Hd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}