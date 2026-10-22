package com.example.kmrltimetable.util

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Audio and haptic alert player for KMRL Station Assistance Alarms.
 * Emits high-priority alarm sound and distinct vibration pattern so that platform security
 * and assistance staff hear the alert clearly in loud station environments.
 * Holds a partial wake lock to guarantee audio and vibration continue even when screen is locked.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"

    private var currentRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun playAlarmSound(context: Context, vibrate: Boolean = true) {
        try {
            stopAlarmSound()

            // Acquire CPU wake lock so playback continues even if screen is locked
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KMRL:AlarmSoundWakeLock")
            wakeLock?.acquire(60_000L) // 60s max safety timeout

            // 1. Resolve loudest alarm uri (fallback to notification/ringtone if alarm not set)
            var alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alertUri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                ringtone.play()
                currentRingtone = ringtone
                Log.d(TAG, "Assistance alarm sound started playing.")
            }

            // 2. Urgent Attention Vibration Pattern: 500ms on, 250ms off, 500ms on...
            if (vibrate) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 500, 250, 500, 250, 500, 250, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound/vibration: ${e.message}", e)
        }
    }

    @Synchronized
    fun stopAlarmSound() {
        try {
            if (currentRingtone?.isPlaying == true) {
                currentRingtone?.stop()
            }
            currentRingtone = null

            vibrator?.cancel()
            vibrator = null

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            Log.d(TAG, "Assistance alarm sound stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm sound: ${e.message}", e)
        }
    }
}
