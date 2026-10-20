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

            val channel = NotificationChannel(
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

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel $CHANNEL_ID_UPDATES created.")
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

        val releaseSnippet = updateInfo.releaseNotes
            .lines()
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString("\n")
            .ifBlank { "Tap to download and install the latest KMRL features." }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setColor(0xFF009B8F.toInt())
            .setContentTitle("KMRL App Update Available (v${updateInfo.latestVersion})")
            .setContentText("A new version is available. Tap to update.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("KMRL App Update (v${updateInfo.latestVersion})")
                    .bigText("${updateInfo.releaseTitle}\n\n$releaseSnippet")
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
}
