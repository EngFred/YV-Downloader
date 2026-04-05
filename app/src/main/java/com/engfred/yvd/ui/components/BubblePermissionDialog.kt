package com.engfred.yvd.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.engfred.yvd.util.BubblePermissionHelper

/**
 * A step-by-step guide dialog that walks users through Android 13+'s
 * "Allow restricted settings" flow required for sideloaded apps to get
 * the SYSTEM_ALERT_WINDOW (overlay) permission.
 *
 * Shows two phases:
 *  - Phase 1: "Go to App Info and tap Allow restricted settings"
 *  - Phase 2: "Now go to Display over other apps and enable it"
 */
@Composable
fun BubblePermissionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BubbleChart,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Enable Floating Bubble",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Android requires two quick steps for sideloaded apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // Step indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepChip(number = 1, label = "Unlock App", isActive = currentStep == 1, isDone = currentStep > 1)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    StepChip(number = 2, label = "Enable", isActive = currentStep == 2, isDone = false)
                }

                Spacer(Modifier.height(20.dp))

                // Step content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    },
                    label = "step_content"
                ) { step ->
                    when (step) {
                        1 -> StepContent(
                            steps = listOf(
                                Pair(Icons.Rounded.Settings, "Open Settings → Apps → YV Downloader"),
                                Pair(Icons.Rounded.MoreVert, "Tap the ⋮ menu in the top-right corner"),
                                Pair(Icons.Rounded.LockOpen, "Tap \"Allow restricted settings\""),
                                Pair(Icons.Rounded.Fingerprint, "Confirm with PIN or fingerprint")
                            )
                        )
                        2 -> StepContent(
                            steps = listOf(
                                Pair(Icons.Rounded.OpenInNew, "The overlay settings page will open"),
                                Pair(Icons.Rounded.ToggleOn, "Find YV Downloader and enable the toggle"),
                                Pair(Icons.Rounded.CheckCircle, "Come back to the app — bubble is ready!")
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action buttons
                if (currentStep == 1) {
                    Button(
                        onClick = {
                            BubblePermissionHelper.openAppInfoSettings(context)
                            currentStep = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open App Settings", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Not Now")
                    }
                } else {
                    Button(
                        onClick = {
                            BubblePermissionHelper.openOverlaySettings(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Overlay Settings", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back to Step 1")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepChip(number: Int, label: String, isActive: Boolean, isDone: Boolean) {
    val bgColor = when {
        isDone   -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else     -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isDone   -> MaterialTheme.colorScheme.onPrimary
        isActive -> MaterialTheme.colorScheme.primary
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            } else {
                Text("$number", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun StepContent(steps: List<Pair<ImageVector, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            steps.forEachIndexed { index, (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}