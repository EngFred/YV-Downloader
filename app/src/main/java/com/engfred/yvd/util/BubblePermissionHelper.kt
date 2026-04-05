package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object BubblePermissionHelper {

    /** True if the overlay permission is already granted (or not required on old APIs). */
    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * On Android 13+, sideloaded apps face "Restricted Settings".
     * The overlay toggle is greyed out until the user manually unlocks it via
     * Settings → Apps → [App] → ⋮ → "Allow restricted settings".
     *
     * We can't detect this state directly via any API, but we know:
     * - If the permission is denied AND we're on API 33+, it's VERY likely restricted.
     * - The fix is always the same steps, so we guide users there.
     */
    fun isLikelyRestrictedByAndroid(context: Context): Boolean =
        !canDrawOverlays(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU // API 33

    /** Opens the App Info page directly — first step to reach "Allow restricted settings". */
    fun openAppInfoSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /** Opens the overlay permission settings page (use AFTER user has allowed restricted settings). */
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