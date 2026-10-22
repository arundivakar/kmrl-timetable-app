package com.example.kmrltimetable.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import com.example.kmrltimetable.data.model.StationAlarm
import com.example.kmrltimetable.ui.screens.StationAlarmActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Calendar

/**
 * Manages scheduling, canceling, persistence, and state of station assistance alarms.
 * Uses Android AlarmManager to guarantee exact delivery even in Doze / sleep states.
 */
object StationAlarmManager {

    private const val TAG = "StationAlarmManager"
    private const val PREFS_NAME = "kmrl_station_alarms_prefs"
    private const val KEY_ACTIVE_ALARMS = "active_station_alarms"

    private val _activeAlarms = MutableStateFlow<List<StationAlarm>>(emptyList())
    val activeAlarms: StateFlow<List<StationAlarm>> = _activeAlarms.asStateFlow()

    private val _triggeredAlarm = MutableStateFlow<StationAlarm?>(null)
    val triggeredAlarm: StateFlow<StationAlarm?> = _triggeredAlarm.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        loadAlarms(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadAlarms(context: Context) {
        try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_ACTIVE_ALARMS, null) ?: return
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<StationAlarm>()
            val now = System.currentTimeMillis()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val alarm = StationAlarm.fromJson(obj)
                // Filter out alarms that ended more than 4 hours ago
                if (now - alarm.targetEpochMs < 4 * 3600 * 1000L) {
                    list.add(alarm)
                }
            }
            _activeAlarms.value = list
            Log.d(TAG, "Loaded ${list.size} active station alarms from storage.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading station alarms: ${e.message}", e)
        }
    }

    private fun saveAlarms(context: Context, list: List<StationAlarm>) {
        try {
            val jsonArray = JSONArray()
            for (alarm in list) {
                jsonArray.put(alarm.toJson())
            }
            getPrefs(context).edit().putString(KEY_ACTIVE_ALARMS, jsonArray.toString()).apply()
            _activeAlarms.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error saving station alarms: ${e.message}", e)
        }
    }

    /**
     * Calculates the trigger time (in epoch ms) given a 24-hr scheduled time (e.g., "09:42:00")
     * and the lead time in minutes before arrival (e.g., 5 min).
     */
    fun calculateTargetEpochMs(scheduledTimeStr: String, minutesBefore: Int): Long {
        val parts = scheduledTimeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val s = parts.getOrNull(2)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, s)
        cal.set(Calendar.MILLISECOND, 0)

        val scheduledMs = cal.timeInMillis
        val targetMs = scheduledMs - (minutesBefore * 60 * 1000L)
        return targetMs
    }

    /**
     * Schedules an exact alarm with the system AlarmManager.
     */
    fun scheduleAlarm(context: Context, alarm: StationAlarm): Boolean {
        init(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service unavailable")
            return false
        }

        val intent = Intent(context, StationAlarmReceiver::class.java).apply {
            action = StationAlarmReceiver.ACTION_STATION_ALARM
            putExtra(StationAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(StationAlarmReceiver.EXTRA_TRAIN_NO, alarm.trainNo)
            putExtra(StationAlarmReceiver.EXTRA_STATION_NAME, alarm.stationName)
            putExtra(StationAlarmReceiver.EXTRA_STATION_CODE, alarm.stationCode)
            putExtra(StationAlarmReceiver.EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
            putExtra(StationAlarmReceiver.EXTRA_NOTES, alarm.notes)
            putExtra(StationAlarmReceiver.EXTRA_SCHEDULED_TIME, alarm.scheduledTime)
            putExtra(StationAlarmReceiver.EXTRA_DIRECTION, alarm.direction)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        // If target epoch is in the past, but train has not departed yet, ring immediately (+1.5 sec)
        val triggerEpoch = if (alarm.targetEpochMs <= now) {
            now + 1500L
        } else {
            alarm.targetEpochMs
        }

        val showIntent = Intent(context, StationAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(StationAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(StationAlarmReceiver.EXTRA_TRAIN_NO, alarm.trainNo)
            putExtra(StationAlarmReceiver.EXTRA_STATION_NAME, alarm.stationName)
            putExtra(StationAlarmReceiver.EXTRA_STATION_CODE, alarm.stationCode)
            putExtra(StationAlarmReceiver.EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
            putExtra(StationAlarmReceiver.EXTRA_NOTES, alarm.notes)
            putExtra(StationAlarmReceiver.EXTRA_SCHEDULED_TIME, alarm.scheduledTime)
            putExtra(StationAlarmReceiver.EXTRA_DIRECTION, alarm.direction)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 2,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Priority 1: setAlarmClock wakes up the device from deep sleep & Doze mode when screen is locked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerEpoch, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpoch,
                    pendingIntent
                )
            }
            Log.i(TAG, "Scheduled hardware AlarmClock for Train ${alarm.trainNo} at ${alarm.stationCode} in ${(triggerEpoch - now) / 1000}s")

            // Add to active list
            val current = _activeAlarms.value.filter { it.id != alarm.id }.toMutableList()
            current.add(alarm)
            saveAlarms(context, current)
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock restricted, falling back to setExactAndAllowWhileIdle: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AlarmManagerCompat.setExactAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerEpoch,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpoch,
                        pendingIntent
                    )
                }
                val current = _activeAlarms.value.filter { it.id != alarm.id }.toMutableList()
                current.add(alarm)
                saveAlarms(context, current)
                return true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm: ${ex.message}", ex)
                return false
            }
        }
    }

    /**
     * Cancels a scheduled station assistance alarm.
     */
    fun cancelAlarm(context: Context, alarmId: String) {
        init(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        val intent = Intent(context, StationAlarmReceiver::class.java).apply {
            action = StationAlarmReceiver.ACTION_STATION_ALARM
            putExtra(StationAlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.cancel(pendingIntent)
        NotificationHelper.dismissAlarmNotification(context, alarmId)
        AlarmSoundPlayer.stopAlarmSound()

        val updated = _activeAlarms.value.filter { it.id != alarmId }
        saveAlarms(context, updated)

        if (_triggeredAlarm.value?.id == alarmId) {
            _triggeredAlarm.value = null
        }
        Log.i(TAG, "Alarm $alarmId cancelled.")
    }

    fun onAlarmTriggered(context: Context, alarmId: String) {
        init(context)
        val list = _activeAlarms.value.map {
            if (it.id == alarmId) it.copy(isTriggered = true) else it
        }
        saveAlarms(context, list)
        val alarm = list.firstOrNull { it.id == alarmId }
        _triggeredAlarm.value = alarm
    }

    fun onAlarmDismissed(context: Context, alarmId: String) {
        init(context)
        if (_triggeredAlarm.value?.id == alarmId) {
            _triggeredAlarm.value = null
        }
        // Remove or mark handled
        val updated = _activeAlarms.value.filter { it.id != alarmId }
        saveAlarms(context, updated)
    }

    fun getAlarmForStation(trainNo: String, stationCode: String): StationAlarm? {
        val cleanNo = trainNo.trim().trimStart('0')
        return _activeAlarms.value.firstOrNull {
            (it.trainNo.trimStart('0') == cleanNo) && it.stationCode.equals(stationCode, ignoreCase = true)
        }
    }

    /**
     * Checks if the app is excluded from Android battery saver optimizations.
     * When unrestricted, Android will never throttle alarms even during deep sleep.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Opens the system Battery Optimization settings or prompt so the user can allow KMRL unrestricted execution.
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Cannot open battery optimization settings: ${ex.message}")
                }
            }
        }
    }
}
