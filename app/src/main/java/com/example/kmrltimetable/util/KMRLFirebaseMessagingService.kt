package com.example.kmrltimetable.util

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.kmrltimetable.data.remote.SyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service to handle incoming remote push notifications
 * for app releases, schedule updates, and system broadcasts.
 */
class KMRLFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KMRL_FCM"

        fun subscribeToTopics() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("updates")
                FirebaseMessaging.getInstance().subscribeToTopic("timetables")
                FirebaseMessaging.getInstance().subscribeToTopic("all")
                Log.d(TAG, "Subscribed to FCM topics: updates, timetables, all")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to subscribe to FCM topics: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Registration Token: $token")
        subscribeToTopics()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // 1. Extract notification payload or data payload
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "KMRL Metro Update"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "A new update is available for KMRL Timetable."

        val targetUrl = remoteMessage.data["url"]
        val messageType = remoteMessage.data["type"]

        // 2. If it's a timetable update signal, trigger immediate background sync
        if (messageType == "timetable_update" || remoteMessage.data.containsKey("sync_now")) {
            Log.d(TAG, "Received timetable sync push signal. Enqueueing SyncWorker...")
            try {
                val syncWork = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(applicationContext).enqueue(syncWork)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enqueue SyncWorker: ${e.message}")
            }
        }

        // 3. Post tray notification to user
        NotificationHelper.showCustomPushNotification(
            context = applicationContext,
            title = title,
            message = body,
            targetUrl = targetUrl
        )
    }
}
