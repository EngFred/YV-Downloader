package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helpers for the SYSTEM_ALERT_WINDOW (overlay) permission required by [FloatingBubbleService].
 *
 * This permission cannot be granted at runtime via [requestPermissions] — the user must
 * navigate to a dedicated Settings page. [openOverlaySettings] takes them there directly.
 *
 * The permission is only enforced on API 23+. On older devices it is auto-granted.
 */
object BubblePermissionHelper {

    /** Returns true if the overlay permission is already granted (or not required). */
    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Opens the system overlay-permission Settings page for this app. */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
        }
    }
}