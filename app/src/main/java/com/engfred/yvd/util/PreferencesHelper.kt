package com.engfred.yvd.util

import android.content.Context
import androidx.core.content.edit
import com.engfred.yvd.domain.model.UpdateInfo

object PreferencesHelper {
    private const val PREFS_NAME = "yvd_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    // Update Keys
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_LATEST_VERSION = "latest_version"
    private const val KEY_RELEASE_NOTES = "release_notes"
    private const val KEY_DOWNLOAD_URL = "download_url"
    private const val KEY_HTML_URL = "html_url"

    fun isOnboardingDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    fun getLastUpdateCheck(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_CHECK, 0L)
    }

    fun setLastUpdateCheck(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putLong(KEY_LAST_UPDATE_CHECK, timestamp) }
    }

    fun saveCachedUpdateInfo(context: Context, info: UpdateInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LATEST_VERSION, info.latestVersion)
            putString(KEY_RELEASE_NOTES, info.releaseNotes)
            putString(KEY_DOWNLOAD_URL, info.downloadUrl)
            putString(KEY_HTML_URL, info.htmlUrl)
        }
    }

    fun getCachedUpdateInfo(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getString(KEY_LATEST_VERSION, null) ?: return null
        val notes = prefs.getString(KEY_RELEASE_NOTES, "") ?: ""
        val dlUrl = prefs.getString(KEY_DOWNLOAD_URL, "") ?: ""
        val htmlUrl = prefs.getString(KEY_HTML_URL, "") ?: ""

        return UpdateInfo(version, notes, dlUrl, htmlUrl)
    }
}