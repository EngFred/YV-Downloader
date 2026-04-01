package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.engfred.yvd.service.FloatingBubbleService

/**
 * Opens the YouTube app (or YouTube.com as a fallback) and simultaneously shows the
 * floating bubble so the user can return to the app with one tap after copying a link.
 *
 * If the overlay permission has not been granted, the bubble is simply skipped — YouTube
 * still opens normally. This keeps the feature opt-in without breaking the core flow.
 *
 * @param context The [Context] used to check overlay permissions, start the floating
 * bubble foreground service, and launch the target Activity.
 */
fun openYoutube(context: Context) {
    // Show bubble before switching away so it's visible the moment YouTube opens.
    try {
        if (BubblePermissionHelper.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingBubbleService::class.java).apply {
                    action = FloatingBubbleService.ACTION_SHOW
                }
            )
        }
    } catch (e: Exception) {
        // Fail silently if the service cannot be started so the core flow isn't interrupted
        e.printStackTrace()
    }

    try {
        val youtubeApp = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")

        // Launch the app if installed, otherwise fallback to the browser intent
        context.startActivity(
            youtubeApp ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        )
    } catch (e: Exception) {
        // Ultimate fallback just in case no browser or intent receiver is found
        e.printStackTrace()
    }
}