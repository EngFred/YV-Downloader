package com.engfred.yvd.util

/**
 * Lightweight singleton flag read by [FloatingBubbleService] to avoid showing the bubble
 * while the user is actively inside the app.
 *
 * Set to true in [MainActivity.onResume] and false in [MainActivity.onPause].
 * Using @Volatile ensures the service's background thread always reads the latest value.
 */
object AppLifecycleTracker {
    @Volatile
    var isInForeground: Boolean = false
}