package com.example.kmrltimetable.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.kmrltimetable.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Information about the latest available app release.
 */
data class AppUpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean,
    val publishedAt: String = ""
)

/**
 * Manager to check for app updates via GitHub Releases API.
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val GITHUB_REPO_LATEST_RELEASE_URL =
        "https://api.github.com/repos/arundivakar/kmrl-timetable-app/releases/latest"

    private const val PREFS_NAME = "kmrl_app_update_prefs"
    private const val KEY_DISMISSED_VERSION = "key_dismissed_version"
    private const val KEY_LAST_CHECK_TIMESTAMP = "key_last_check_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves current installed version name.
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            if (BuildConfig.VERSION_NAME.isNotBlank()) {
                BuildConfig.VERSION_NAME
            } else {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                packageInfo.versionName ?: "5.0.7"
            }
        } catch (e: Exception) {
            "5.0.7"
        }
    }

    /**
     * Checks GitHub Releases API for the latest version.
     * Runs safely on [Dispatchers.IO].
     */
    suspend fun checkForUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_REPO_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "KMRL-Timetable-App")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP $responseCode")
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(responseBody)

            val rawTagName = json.optString("tag_name", "")
            val latestVersion = rawTagName.trimStart('v', 'V').trim()
            val releaseTitle = json.optString("name", "Version $latestVersion")
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
            val htmlUrl = json.optString("html_url", "https://github.com/arundivakar/kmrl-timetable-app/releases")
            val publishedAt = json.optString("published_at", "")

            // Find APK asset url
            var apkDownloadUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", htmlUrl)
                        break
                    }
                }
            }

            val currentVersion = getCurrentVersionName(context)
            val isNewer = isVersionNewer(latestVersion, currentVersion)

            // Save last check time
            getPrefs(context).edit().putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis()).apply()

            AppUpdateInfo(
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                isUpdateAvailable = isNewer,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares two semantic version strings (e.g., "5.0.6" vs "5.0.5").
     * Returns true if candidateVersion is strictly higher than currentVersion.
     */
    fun isVersionNewer(candidateVersion: String, currentVersion: String): Boolean {
        try {
            val candidateParts = candidateVersion.split(".", "-").mapNotNull { it.toIntOrNull() }
            val currentParts = currentVersion.split(".", "-").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(candidateParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val candidatePart = candidateParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                if (candidatePart > currentPart) return true
                if (candidatePart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return candidateVersion.compareTo(currentVersion) > 0
        }
    }

    /**
     * Determines whether the user should be prompted for this version.
     * False if the user already clicked "Later" for this specific version.
     */
    fun shouldPromptForUpdate(context: Context, latestVersion: String): Boolean {
        val dismissedVersion = getPrefs(context).getString(KEY_DISMISSED_VERSION, null)
        return dismissedVersion != latestVersion
    }

    /**
     * Records that the user dismissed prompt for this version.
     */
    fun dismissUpdate(context: Context, latestVersion: String) {
        getPrefs(context).edit().putString(KEY_DISMISSED_VERSION, latestVersion).apply()
    }
}
