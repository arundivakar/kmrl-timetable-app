package com.example.kmrltimetable.data.remote

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kmrltimetable.data.local.AppDatabase
import com.example.kmrltimetable.data.local.entity.DayDefaultEntity
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.SyncMetadataEntity

/**
 * WorkManager background task that syncs the timetable configuration from Firebase.
 *
 * Steps:
 * 1. Fetch config version from Firebase.
 * 2. Compare with locally stored version.
 * 3. If changed: fetch date_assignments + day_defaults and update Room.
 * 4. Store new version locally so the next sync won't re-download unnecessarily.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val KEY_REMOTE_VERSION  = "remote_config_version"
        private const val KEY_LAST_SYNC_TIME  = "last_sync_time"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting timetable config and database sync...")
        val dao = AppDatabase.getDatabase(applicationContext).timetableDao()

        // 0a. OTA Timetable Database Sync from GitHub Pages
        try {
            val otaResult = OtaSyncManager.checkAndSync(applicationContext)
            Log.d(TAG, "OTA Timetable sync result: $otaResult")
            if (otaResult is OtaSyncResult.Success && otaResult.updated) {
                com.example.kmrltimetable.util.NotificationHelper.showTimetableUpdateNotification(
                    context = applicationContext,
                    timetableVersion = otaResult.version,
                    message = otaResult.message
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "OTA Timetable sync error: ${e.message}")
        }

        // 0b. Background check for new app release on GitHub
        try {
            val appUpdateInfo = AppUpdateManager.checkForUpdate(applicationContext)
            if (appUpdateInfo != null && appUpdateInfo.isUpdateAvailable) {
                com.example.kmrltimetable.util.NotificationHelper.showAppUpdateNotification(
                    context = applicationContext,
                    updateInfo = appUpdateInfo
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background app update check error: ${e.message}")
        }

        return try {
            // 1. Fetch remote config version
            val remoteConfig = FirebaseManager.fetchConfig()

            // 2. Compare with local version
            val localVersionStr = dao.getSyncMetadata(KEY_REMOTE_VERSION)?.value ?: "0"
            val localVersion    = localVersionStr.toLongOrNull() ?: 0L

            if (remoteConfig.version <= localVersion) {
                Log.d(TAG, "Already up to date (version=$localVersion). Skipping sync.")
                return Result.success()
            }

            Log.d(TAG, "Remote version=${remoteConfig.version}, local=$localVersion. Syncing...")

            // 3a. Fetch and apply date assignments (overrides)
            val dateAssignments = FirebaseManager.fetchDateAssignments()
            dao.clearAllOverrides()
            val overrideEntities = dateAssignments.map { (date, timetableName) ->
                ScheduleOverrideEntity(overrideDate = date, timetableName = timetableName)
            }
            if (overrideEntities.isNotEmpty()) {
                dao.insertOverrides(overrideEntities)
            }

            // 3b. Fetch and apply day defaults
            val dayDefaults = FirebaseManager.fetchDayDefaults()
            dayDefaults.forEach { (dayOfWeek, timetableName) ->
                dao.updateDayDefault(dayOfWeek, timetableName)
            }
            // In KMRL, Weekdays are Mon-Sat (0..5). Auto-heal Saturday (5) to match Weekday default (0) if holding a legacy Sunday schedule
            val weekdayName = dayDefaults[0] ?: dao.getDefaultTimetableForDay(0)?.timetableName
            if (weekdayName != null) {
                val satName = dayDefaults[5]
                if (satName == null || satName.contains("S", ignoreCase = false)) {
                    dao.updateDayDefault(5, weekdayName)
                }
            }

            // 4. Save new version + sync timestamp
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                          .format(java.util.Date())
            dao.upsertSyncMetadata(SyncMetadataEntity(KEY_REMOTE_VERSION, remoteConfig.version.toString()))
            dao.upsertSyncMetadata(SyncMetadataEntity(KEY_LAST_SYNC_TIME, now))

            Log.d(TAG, "Sync complete. Applied ${overrideEntities.size} overrides, ${dayDefaults.size} day defaults.")
            Result.success()

        } catch (e: Exception) {
            Log.w(TAG, "Sync failed (will use cached data): ${e.message}")
            Result.retry() // WorkManager will retry with backoff
        }
    }
}
