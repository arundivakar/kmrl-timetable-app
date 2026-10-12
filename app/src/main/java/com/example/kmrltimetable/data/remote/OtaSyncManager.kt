package com.example.kmrltimetable.data.remote

import android.content.Context
import android.util.Log
import com.example.kmrltimetable.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

sealed class OtaSyncResult {
    data class Success(val version: String, val updated: Boolean, val message: String) : OtaSyncResult()
    data class Error(val message: String) : OtaSyncResult()
}

object OtaSyncManager {
    private const val TAG = "OtaSyncManager"
    private const val BASE_URL = "https://arundivakar.github.io/kmrl-timetable-app/data"
    private const val VERSION_URL = "$BASE_URL/db_version.txt"
    private const val DB_GZ_URL = "$BASE_URL/kmrl_timetable.db.gz"

    private const val PREFS_NAME = "kmrl_database_prefs"
    private const val KEY_CURRENT_OTA_VERSION = "ota_db_version"
    private const val KEY_LAST_OTA_SYNC = "ota_last_sync_time"

    fun getCurrentVersion(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CURRENT_OTA_VERSION, null)
        if (!saved.isNullOrBlank()) return saved

        return try {
            context.assets.open("db_version.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            "Initial"
        }
    }

    fun getLastSyncTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_OTA_SYNC, null)
    }

    suspend fun checkAndSync(context: Context, force: Boolean = false): OtaSyncResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking remote timetable version from $VERSION_URL...")

            // 1. Fetch remote version
            val versionUrlWithBust = URL("$VERSION_URL?nocache=${System.currentTimeMillis()}")
            val conn = (versionUrlWithBust.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                useCaches = false
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext OtaSyncResult.Error("Cloud server returned HTTP ${conn.responseCode}")
            }

            val remoteVersion = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            conn.disconnect()

            if (remoteVersion.isBlank()) {
                return@withContext OtaSyncResult.Error("Remote version string is empty")
            }

            val currentVersion = getCurrentVersion(context)
            Log.d(TAG, "Current version: $currentVersion, Remote version: $remoteVersion, force: $force")

            if (!force && currentVersion == remoteVersion) {
                return@withContext OtaSyncResult.Success(
                    version = currentVersion,
                    updated = false,
                    message = "Timetable is already up to date ($currentVersion)"
                )
            }

            // 2. Download gzipped database
            Log.d(TAG, "Downloading updated timetable database: $DB_GZ_URL...")
            val gzTempFile = File(context.cacheDir, "kmrl_update_${System.currentTimeMillis()}.db.gz")
            val dbTempFile = File(context.cacheDir, "kmrl_update_${System.currentTimeMillis()}.db")

            val dbConn = (URL("$DB_GZ_URL?nocache=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                useCaches = false
            }

            if (dbConn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext OtaSyncResult.Error("Failed to download database: HTTP ${dbConn.responseCode}")
            }

            // Stream download directly to file
            dbConn.inputStream.use { inStream ->
                FileOutputStream(gzTempFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            dbConn.disconnect()

            // 3. Decompress gzip
            Log.d(TAG, "Decompressing gzipped database (${gzTempFile.length()} bytes)...")
            gzTempFile.inputStream().use { fileIn ->
                GZIPInputStream(BufferedInputStream(fileIn)).use { gzIn ->
                    FileOutputStream(dbTempFile).use { dbOut ->
                        gzIn.copyTo(dbOut)
                    }
                }
            }
            gzTempFile.delete()

            // 4. Validate SQLite header
            if (dbTempFile.length() < 100) {
                dbTempFile.delete()
                return@withContext OtaSyncResult.Error("Downloaded database file is invalid or too small")
            }

            val header = ByteArray(16)
            dbTempFile.inputStream().use { it.read(header) }
            val headerStr = String(header)
            if (!headerStr.startsWith("SQLite format 3")) {
                dbTempFile.delete()
                return@withContext OtaSyncResult.Error("Downloaded file is not a valid SQLite database")
            }

            // 5. Replace database
            Log.d(TAG, "Replacing active database with new version...")
            AppDatabase.replaceDatabaseWithFile(context, dbTempFile)
            dbTempFile.delete()

            // 6. Update preferences
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CURRENT_OTA_VERSION, remoteVersion)
                .putString(KEY_LAST_OTA_SYNC, now)
                .apply()

            Log.d(TAG, "OTA Timetable sync successful! New version: $remoteVersion")
            OtaSyncResult.Success(
                version = remoteVersion,
                updated = true,
                message = "Successfully updated timetable to version $remoteVersion"
            )

        } catch (e: Exception) {
            Log.e(TAG, "OTA Sync error: ${e.message}", e)
            OtaSyncResult.Error(e.message ?: "Network error during OTA sync")
        }
    }
}
