package com.example.kmrltimetable.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kmrltimetable.MainActivity
import com.example.kmrltimetable.R
import com.example.kmrltimetable.data.remote.AppUpdateInfo

/**
 * Utility helper for creating notification channels and dispatching
 * high-priority system push notifications for App Updates and Timetable Syncs.
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_ID_UPDATES = "kmrl_updates_channel"
    private const val CHANNEL_NAME = "KMRL Updates & Schedules"
    private const val CHANNEL_DESCRIPTION = "Alerts for new KMRL app versions, schedule updates, and service announcements."

    const val CHANNEL_ID_ALARMS = "kmrl_assistance_alarms_channel"
    private const val CHANNEL_NAME_ALARMS = "Station Assistance Alarms"
    private const val CHANNEL_DESCRIPTION_ALARMS = "Urgent audible alarms when trains approach stations for platform passenger assistance."

    private const val NOTIF_ID_APP_UPDATE = 1001
    private const val NOTIF_ID_TIMETABLE_UPDATE = 1002
    private const val NOTIF_ID_TEST = 1099

    private const val PREFS_NOTIF = "kmrl_notification_prefs"
    private const val KEY_LAST_NOTIFIED_APP_VERSION = "last_notified_app_version"
    private const val KEY_LAST_NOTIFIED_TIMETABLE_VER = "last_notified_timetable_ver"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the Android Notification Channels.
     * Must be called during Application onCreate.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val updateChannel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = 0xFF009B8F.toInt() // KmrlTeal
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(updateChannel)

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARMS,
                CHANNEL_NAME_ALARMS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION_ALARMS
                enableLights(true)
                lightColor = 0xFFE65100.toInt() // Urgent Amber
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500, 250, 800)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alarmChannel)

            Log.d(TAG, "Notification channels initialized.")
        }
    }

    /**
     * Shows a push notification informing the user that a new app update is available.
     * Prevents duplicate spamming if the user was already notified of this exact version.
     */
    fun showAppUpdateNotification(context: Context, updateInfo: AppUpdateInfo, force: Boolean = false) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted. Cannot show app update notification.")
            return
        }

        val prefs = getPrefs(context)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_APP_VERSION, null)
        if (!force && lastNotified == updateInfo.latestVersion) {
            Log.d(TAG, "App update notification for version ${updateInfo.latestVersion} already shown. Skipping.")
            return
        }

        // 1. Content Intent: Open MainActivity with update dialog trigger
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_UPDATE", true)
            putExtra("EXTRA_UPDATE_VERSION", updateInfo.latestVersion)
            putExtra("EXTRA_UPDATE_URL", updateInfo.downloadUrl)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            1,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action Intent: Direct browser download button
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            2,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.kmrl_logo)
        } catch (e: Exception) {
            null
        }

        val cleanNotes = updateInfo.releaseNotes
            .lines()
            .map { it.replace("**", "").replace("###", "").trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .take(5)
            .joinToString("\n")
            .ifBlank { "• Live tracking using timetable added\n• Direction-specific 25-station route timeline\n• Update push notifications & schedule disclaimer" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setColor(0xFF009B8F.toInt())
            .setContentTitle("KMRL App Update Available (v${updateInfo.latestVersion})")
            .setContentText("Live tracking using timetable added • Tap to update")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("KMRL App Update (v${updateInfo.latestVersion})")
                    .bigText("What's New in v${updateInfo.latestVersion}:\n\n$cleanNotes")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Download APK",
                downloadPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_APP_UPDATE, notification)
            prefs.edit().putString(KEY_LAST_NOTIFIED_APP_VERSION, updateInfo.latestVersion).apply()
            Log.d(TAG, "App update notification posted for version ${updateInfo.latestVersion}")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
        }
    }

    /**
     * Shows a push notification when an OTA Timetable database has been synchronized.
     */
    fun showTimetableUpdateNotification(context: Context, timetableVersion: String, message: String) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted. Skipping timetable notification.")
            return
        }

        val prefs = getPrefs(context)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_TIMETABLE_VER, null)
        if (lastNotified == timetableVersion) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.kmrl_logo)
        } catch (e: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setColor(0xFF009B8F.toInt())
            .setContentTitle("KMRL Timetable Updated")
            .setContentText("New metro schedule is now active ($timetableVersion).")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("The latest KMRL timetable ($timetableVersion) has been downloaded and synchronized on your device.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TIMETABLE_UPDATE, notification)
            prefs.edit().putString(KEY_LAST_NOTIFIED_TIMETABLE_VER, timetableVersion).apply()
            Log.d(TAG, "Timetable update notification posted: $timetableVersion")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
        }
    }

    /**
     * Shows an arbitrary push notification (e.g. from Firebase Cloud Messaging).
     */
    fun showCustomPushNotification(
        context: Context,
        title: String,
        message: String,
        targetUrl: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = if (!targetUrl.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF009B8F.toInt())
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notifId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting custom notification: ${e.message}")
        }
    }

    /**
     * Dispatches an immediate test push notification so the user can verify
     * push notification delivery on their device.
     */
    fun sendTestNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            99,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF009B8F.toInt())
            .setContentTitle("KMRL Notifications Active")
            .setContentText("Push notifications for updates and timetables are working properly!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You will receive automatic notifications whenever a new KMRL app release or updated timetable is available.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TEST, notification)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error posting test notification: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if notification permission is granted on the device.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Shows a high-priority station assistance alarm notification with alarm sound & action buttons.
     */
    fun showStationAlarmNotification(
        context: Context,
        alarmId: String,
        trainNo: String,
        stationName: String,
        stationCode: String,
        minutesBefore: Int,
        notes: String,
        scheduledTime: String,
        direction: String
    ) {
        if (!hasNotificationPermission(context)) return

        val platformText = if (direction == "UP") "Platform 1 (Towards Tripunithura)" else "Platform 2 (Towards Aluva)"
        val timingText = if (minutesBefore > 0) "Arriving in ~$minutesBefore min" else "Arriving Now"

        // 1. Content Intent: Open MainActivity directly into Live Tracking for this train
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TRACK_TRAIN", trainNo)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action Intent: Dismiss / Stop Alarm
        val dismissIntent = Intent(context, StationAlarmReceiver::class.java).apply {
            action = StationAlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(StationAlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Full-Screen Intent: Wake screen and launch StationAlarmActivity over Lock Screen
        val fullScreenIntent = Intent(context, com.example.kmrltimetable.ui.screens.StationAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(StationAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(StationAlarmReceiver.EXTRA_TRAIN_NO, trainNo)
            putExtra(StationAlarmReceiver.EXTRA_STATION_NAME, stationName)
            putExtra(StationAlarmReceiver.EXTRA_STATION_CODE, stationCode)
            putExtra(StationAlarmReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
            putExtra(StationAlarmReceiver.EXTRA_NOTES, notes)
            putExtra(StationAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(StationAlarmReceiver.EXTRA_DIRECTION, direction)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode() + 3,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.kmrl_logo)
        } catch (e: Exception) {
            null
        }

        val bigText = buildString {
            append("🚨 PASSENGER ASSISTANCE REQUIRED\n\n")
            append("• Train: Train $trainNo ($direction Line)\n")
            append("• Station: $stationName ($stationCode)\n")
            append("• Platform: $platformText\n")
            append("• Scheduled Departure: $scheduledTime\n")
            append("• Estimated Arrival: $timingText\n")
            if (notes.isNotBlank()) {
                append("• Notes: $notes\n\n")
            } else {
                append("\n")
            }
            append("👉 Action: Alert platform security & staff to position on $platformText for passenger assistance.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setColor(0xFFE65100.toInt()) // Urgent Amber
            .setContentTitle("🚨 Assistance Alert: Train $trainNo ➔ $stationName ($stationCode)")
            .setContentText("$timingText ($scheduledTime) • ${notes.ifBlank { "Platform assistance required" }}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "DISMISS & STOP SOUND",
                dismissPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "VIEW TRACKER",
                contentPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(alarmId.hashCode(), notification)
            Log.d(TAG, "Posted Station Alarm notification for $alarmId (Train $trainNo at $stationCode)")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting station alarm notification: ${e.message}")
        }
    }

    /**
     * Cancels an active station alarm notification.
     */
    fun dismissAlarmNotification(context: Context, alarmId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(alarmId.hashCode())
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing alarm notification: ${e.message}")
        }
    }
}
