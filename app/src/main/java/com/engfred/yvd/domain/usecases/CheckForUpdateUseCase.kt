package com.engfred.yvd.domain.usecases

import android.os.Build
import android.util.Log
import com.engfred.yvd.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

private const val TAG = "CheckForUpdateUseCase"
private const val OWNER = "EngFred"
private const val REPO  = "YV-Downloader"

class CheckForUpdateUseCase @Inject constructor() {

    suspend operator fun invoke(currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned ${connection.responseCode}")
                    connection.disconnect()
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json         = JSONObject(body)
                val tagName      = json.getString("tag_name").removePrefix("v")
                val htmlUrl      = json.getString("html_url")
                val releaseNotes = json.optString("body", "").trim()
                val assets       = json.optJSONArray("assets")

                val downloadUrl = resolveDownloadUrl(assets, htmlUrl)

                if (isNewerVersion(latest = tagName, current = currentVersion)) {
                    Log.d(TAG, "Update available: $currentVersion → $tagName")
                    UpdateInfo(
                        latestVersion = tagName,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        htmlUrl = htmlUrl
                    )
                } else {
                    Log.d(TAG, "App is up-to-date ($currentVersion)")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                null
            }
        }

    private fun resolveDownloadUrl(
        assets: JSONArray?,
        fallbackHtmlUrl: String
    ): String {
        if (assets == null || assets.length() == 0) return fallbackHtmlUrl

        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        var exactMatch: String? = null
        var armeabiMatch: String? = null
        var anyApk: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name  = asset.getString("name")
            val url   = asset.getString("browser_download_url")

            if (!name.endsWith(".apk")) continue

            when {
                name.contains(deviceAbi) && exactMatch == null -> exactMatch = url
                name.contains("armeabi-v7a") && armeabiMatch == null -> armeabiMatch = url
            }
            if (anyApk == null) anyApk = url
        }

        return exactMatch ?: armeabiMatch ?: anyApk ?: fallbackHtmlUrl
    }
    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map  { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(l.size, c.size)
        for (i in 0 until len) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}