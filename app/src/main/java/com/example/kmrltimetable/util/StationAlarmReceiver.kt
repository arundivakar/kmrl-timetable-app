package com.example.kmrltimetable.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by Android AlarmManager when a scheduled station
 * assistance alarm expires (e.g. 5 minutes before train arrives at VAKK).
 * Wakes up the device, plays audible alarm ringtone, vibrates, and shows heads-up notification.
 */
class StationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STATION_ALARM = "com.example.kmrltimetable.ACTION_STATION_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.kmrltimetable.ACTION_DISMISS_ALARM"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TRAIN_NO = "extra_train_no"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_STATION_CODE = "extra_station_code"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
        const val EXTRA_NOTES = "extra_notes"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_DIRECTION = "extra_direction"

        fun dismissAlarm(context: Context, alarmId: String) {
            AlarmSoundPlayer.stopAlarmSound()
            NotificationHelper.dismissAlarmNotification(context, alarmId)
            StationAlarmManager.onAlarmDismissed(context, alarmId)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        if (action == ACTION_DISMISS_ALARM) {
            Log.d("StationAlarmReceiver", "Action dismiss alarm received: $alarmId")
            dismissAlarm(context, alarmId)
            return
        }

        if (action == ACTION_STATION_ALARM) {
            val trainNo = intent.getStringExtra(EXTRA_TRAIN_NO) ?: ""
            val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: ""
            val stationCode = intent.getStringExtra(EXTRA_STATION_CODE) ?: ""
            val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 5)
            val notes = intent.getStringExtra(EXTRA_NOTES) ?: ""
            val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: ""
            val direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "UP"

            Log.i("StationAlarmReceiver", "Station assistance alarm fired for Train $trainNo at $stationName ($stationCode)")

            // 1. Wake up the device display immediately if sleeping/locked
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                @Suppress("DEPRECATION")
                val screenWakeLock = powerManager?.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "KMRL:StationAlarmReceiverWakeLock"
                )
                screenWakeLock?.acquire(15_000L) // 15s wake display
            } catch (e: Exception) {
                Log.w("StationAlarmReceiver", "Could not acquire screen wake lock: ${e.message}")
            }

            // 2. Play high-priority alarm sound and urgent vibration pattern (with CPU wake lock)
            AlarmSoundPlayer.playAlarmSound(context)

            // 3. Dispatch full-screen heads-up notification that shows over lock screen
            NotificationHelper.showStationAlarmNotification(
                context = context,
                alarmId = alarmId,
                trainNo = trainNo,
                stationName = stationName,
                stationCode = stationCode,
                minutesBefore = minutesBefore,
                notes = notes,
                scheduledTime = scheduledTime,
                direction = direction
            )

            // 4. Also launch full-screen lock screen activity directly
            try {
                val activityIntent = Intent(context, com.example.kmrltimetable.ui.screens.StationAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_TRAIN_NO, trainNo)
                    putExtra(EXTRA_STATION_NAME, stationName)
                    putExtra(EXTRA_STATION_CODE, stationCode)
                    putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
                    putExtra(EXTRA_NOTES, notes)
                    putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                    putExtra(EXTRA_DIRECTION, direction)
                }
                context.startActivity(activityIntent)
            } catch (e: Exception) {
                Log.d("StationAlarmReceiver", "Direct activity start bypassed in favor of fullScreenIntent: ${e.message}")
            }

            // 5. Mark alarm as triggered in StationAlarmManager
            StationAlarmManager.onAlarmTriggered(context, alarmId)
        }
    }
}
