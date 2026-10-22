package com.example.kmrltimetable

import android.app.Application
import androidx.work.*
import com.example.kmrltimetable.data.local.AppDatabase
import com.example.kmrltimetable.data.remote.SyncWorker
import com.example.kmrltimetable.data.repository.TimetableRepository
import com.example.kmrltimetable.util.KMRLFirebaseMessagingService
import com.example.kmrltimetable.util.NotificationHelper
import java.util.concurrent.TimeUnit

class KMRLApplication : Application() {

    val database: AppDatabase
        get() = AppDatabase.getDatabase(this)
    val repository: TimetableRepository
        get() = TimetableRepository(database.timetableDao())

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Notification Channels for updates, schedules & alarms
        NotificationHelper.createNotificationChannels(this)

        // 2. Initialize Station Assistance Alarm Manager
        com.example.kmrltimetable.util.StationAlarmManager.init(this)

        // 3. Subscribe device to FCM topics for remote push alerts
        KMRLFirebaseMessagingService.subscribeToTopics()

        // 4. Schedule background sync & update worker
        scheduleSyncWorker()
    }

    /**
     * Schedules a one-time sync immediately on startup plus a periodic sync every 15 minutes.
     * WorkManager handles network availability and retries automatically.
     */
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // One-time immediate sync on app open
        val immediateSync = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("kmrl_immediate_sync")
            .build()

        // Periodic sync every 15 minutes (minimum allowed by WorkManager)
        val periodicSync = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("kmrl_periodic_sync")
            .build()

        val workManager = WorkManager.getInstance(this)

        // Enqueue one-time sync (replace any existing)
        workManager.enqueueUniqueWork(
            "kmrl_immediate_sync",
            ExistingWorkPolicy.REPLACE,
            immediateSync
        )

        // Enqueue periodic sync (keep existing if already scheduled)
        workManager.enqueueUniquePeriodicWork(
            "kmrl_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync
        )
    }
}
