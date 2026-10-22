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
            val rawName = if (json.isNull("name")) "" else json.optString("name", "")
            val releaseTitle = if (rawName.isBlank() || rawName.equals("null", ignoreCase = true)) {
                "KMRL Timetable v$latestVersion"
            } else {
                rawName
            }

            val rawBody = if (json.isNull("body")) "" else json.optString("body", "")
            val releaseNotes = if (rawBody.isBlank() || rawBody.equals("null", ignoreCase = true)) {
                getChangelogForVersion(latestVersion)
            } else {
                rawBody
            }
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

    private const val KEY_SEEN_WHATS_NEW_VERSION = "key_seen_whats_new_version"

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

    /**
     * Checks if the user has already acknowledged or seen the "What's New" prompt for this installed version.
     */
    fun hasSeenWhatsNew(context: Context, version: String): Boolean {
        val seen = getPrefs(context).getString(KEY_SEEN_WHATS_NEW_VERSION, null)
        return seen == version
    }

    /**
     * Records that the user has seen the "What's New" notification for this version.
     */
    fun markWhatsNewSeen(context: Context, version: String) {
        getPrefs(context).edit().putString(KEY_SEEN_WHATS_NEW_VERSION, version).apply()
    }

    /**
     * Returns structured changelog notes for any version release.
     */
    fun getChangelogForVersion(version: String): String {
        val clean = version.trim().trimStart('v', 'V')
        return when {
            clean.startsWith("5.4") -> """
                • Station Assistance Alarms: Set alarms before reaching stations (e.g. at KLMT for VAKK) to dispatch platform security & assistance
                • Audible Alarm & Haptic Alerts: Loud USAGE_ALARM sound and urgent vibration pattern
                • 100x Faster Live Tracking: Optimized query loads active train schedule in <50ms
                • Active Assistance Alarms Banner & Heads-up notification
                • Full in-app changelog & automatic release distribution
            """.trimIndent()
            clean.startsWith("5.3") -> """
                • Timetable-Based Live Train Tracker with 25-station route nodes
                • Direction-specific UP & DOWN line routes (Aluva ➔ Tripunithura)
                • Live status hero badge with real-time transit calculation
                • Push notifications & schedule estimates disclaimer
            """.trimIndent()
            clean.startsWith("5.2") -> """
                • Instant in-app database reload after OTA sync without app restart
                • Updated October 2026 timetable schedules
            """.trimIndent()
            else -> """
                • Performance improvements & bug fixes
                • Updated KMRL metro timetables & station timings
            """.trimIndent()
        }
    }

    /**
     * Full version changelog history with release download URLs and metadata for the in-app releases section.
     */
    fun getChangelogHistory(): List<VersionChangelogEntry> = listOf(
        VersionChangelogEntry(
            version = "v5.4.0",
            date = "22 October 2026",
            title = "Station Assistance Alarms & Speed",
            downloadUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/download/v5.4.0/KMRL_Timetable_v5.4.0_Release.apk",
            releasePageUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/tag/v5.4.0",
            changes = listOf(
                "Station Assistance Alarms: Set alarms 5m before arrival (e.g. at KLMT for VAKK) to arrange platform security & wheelchair assist",
                "Audible alarm ringtone and urgent haptic vibration patterns",
                "100x faster live tracking load (< 50ms) with optimized SQLite composite indices",
                "Active Assistance Alarms Banner with 1-tap view & cancel",
                "Full in-app changelog & automated release distribution"
            )
        ),
        VersionChangelogEntry(
            version = "v5.3.0",
            date = "20 October 2026",
            title = "Live Train Tracking & Push Alerts",
            downloadUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/download/v5.3.0/KMRL_Timetable_v5.3.0_Release.apk",
            releasePageUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/tag/v5.3.0",
            changes = listOf(
                "Timetable-based Live Train Tracker with 25-station route nodes",
                "Direction-specific UP & DOWN line route views (Aluva ↔ Tripunithura)",
                "Live status hero card with real-time transit indicators",
                "Firebase Cloud Messaging (FCM) update alerts & background sync"
            )
        ),
        VersionChangelogEntry(
            version = "v5.2.0",
            date = "12 October 2026",
            title = "Sunday Timetable & Cloud OTA Sync",
            downloadUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/download/v5.2.0/KMRL_Timetable_v5.2.0_Release.apk",
            releasePageUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/tag/v5.2.0",
            changes = listOf(
                "Instant in-app database reload after OTA sync without app restart",
                "Updated October 2026 timetable schedules (Weekday, Sunday, Festival)"
            )
        ),
        VersionChangelogEntry(
            version = "v5.1.0",
            date = "23 September 2026",
            title = "Material 3 Station Interchange",
            downloadUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/download/v5.1.0/KMRL_Timetable_v5.1.0_Release.apk",
            releasePageUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/tag/v5.1.0",
            changes = listOf(
                "Animated Material 3 station interchange button with 180° rotation",
                "Clean Material Design 3 surface colors and visual tokens"
            )
        ),
        VersionChangelogEntry(
            version = "v5.0.0",
            date = "02 September 2026",
            title = "Universal Dark Theme & Small Footprint",
            downloadUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/download/v5.0.0/KMRL_Timetable_v5.0.0_Release.apk",
            releasePageUrl = "https://github.com/arundivakar/kmrl-timetable-app/releases/tag/v5.0.0",
            changes = listOf(
                "Universal Light & Dark theme toggle with persistent storage",
                "Modular architecture and 75% smaller APK footprint"
            )
        )
    )

    fun getLatestVersionEntry(): VersionChangelogEntry {
        return getChangelogHistory().first()
    }
}

data class VersionChangelogEntry(
    val version: String,
    val date: String,
    val title: String = "",
    val changes: List<String>,
    val downloadUrl: String = "",
    val releasePageUrl: String = ""
)
