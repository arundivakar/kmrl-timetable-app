package com.example.kmrltimetable.ui.screens

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.MainActivity
import com.example.kmrltimetable.ui.theme.KmrlOrange
import com.example.kmrltimetable.ui.theme.KmrlTeal
import com.example.kmrltimetable.ui.theme.KmrlTheme
import com.example.kmrltimetable.util.AlarmSoundPlayer
import com.example.kmrltimetable.util.NotificationHelper
import com.example.kmrltimetable.util.StationAlarmReceiver

/**
 * Full-Screen Activity that turns ON the device screen and displays over the Lock Screen
 * when a Station Assistance Alarm triggers.
 * Ensures metro staff and station security see and hear the alarm immediately,
 * even if the device was locked, asleep, or in deep Doze mode.
 */
class StationAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowWhenLocked()

        val alarmId = intent.getStringExtra(StationAlarmReceiver.EXTRA_ALARM_ID) ?: ""
        val trainNo = intent.getStringExtra(StationAlarmReceiver.EXTRA_TRAIN_NO) ?: ""
        val stationName = intent.getStringExtra(StationAlarmReceiver.EXTRA_STATION_NAME) ?: "Station"
        val stationCode = intent.getStringExtra(StationAlarmReceiver.EXTRA_STATION_CODE) ?: ""
        val minutesBefore = intent.getIntExtra(StationAlarmReceiver.EXTRA_MINUTES_BEFORE, 5)
        val notes = intent.getStringExtra(StationAlarmReceiver.EXTRA_NOTES) ?: "Passenger Assistance"
        val scheduledTime = intent.getStringExtra(StationAlarmReceiver.EXTRA_SCHEDULED_TIME) ?: ""
        val direction = intent.getStringExtra(StationAlarmReceiver.EXTRA_DIRECTION) ?: "UP"

        setContent {
            KmrlTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    StationAlarmLockScreenContent(
                        trainNo = trainNo,
                        stationName = stationName,
                        stationCode = stationCode,
                        minutesBefore = minutesBefore,
                        notes = notes,
                        scheduledTime = scheduledTime,
                        direction = direction,
                        onDismissAlarm = {
                            StationAlarmReceiver.dismissAlarm(this, alarmId)
                            finish()
                        },
                        onViewTracker = {
                            StationAlarmReceiver.dismissAlarm(this, alarmId)
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("EXTRA_TRACK_TRAIN", trainNo)
                            }
                            startActivity(mainIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun turnScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop audio if user leaves the activity
        AlarmSoundPlayer.stopAlarmSound()
    }
}

@Composable
fun StationAlarmLockScreenContent(
    trainNo: String,
    stationName: String,
    stationCode: String,
    minutesBefore: Int,
    notes: String,
    scheduledTime: String,
    direction: String,
    onDismissAlarm: () -> Unit,
    onViewTracker: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bell_scale"
    )

    val platformText = if (direction == "UP") "Platform 1 (Towards Tripunithura)" else "Platform 2 (Towards Aluva)"
    val timingText = if (minutesBefore > 0) "Arriving in ~$minutesBefore min" else "Train Arriving Now"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP: Pulsing Alert Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF5722), Color(0xFFE65100).copy(alpha = 0.4f), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFE65100), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alarm Active",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Surface(
                color = Color(0xFFE65100).copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE65100))
            ) {
                Text(
                    text = "🚨 PASSENGER ASSISTANCE REQUIRED",
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stationName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            if (stationCode.isNotBlank()) {
                Text(
                    text = "Station Code: $stationCode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = KmrlTeal
                )
            }
        }

        // CENTER: Operational Briefing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Assistance Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = notes.ifBlank { "Passenger Assistance" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF2C2C2C))

                // Detail Rows
                AlarmDetailRow(
                    icon = Icons.Default.DirectionsSubway,
                    label = "Incoming Train",
                    value = "Train $trainNo ($direction Line)"
                )

                AlarmDetailRow(
                    icon = Icons.Default.Place,
                    label = "Target Platform",
                    value = platformText
                )

                AlarmDetailRow(
                    icon = Icons.Default.Schedule,
                    label = "Scheduled Timing",
                    value = "$scheduledTime ($timingText)"
                )
            }
        }

        // BOTTOM: Big Response Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dismiss Button (Big and Urgent)
            Button(
                onClick = onDismissAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Icon(
                    imageVector = Icons.Default.AlarmOff,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "DISMISS & STOP SOUND",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // View Tracker Button
            OutlinedButton(
                onClick = onViewTracker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmrlTeal),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, KmrlTeal)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "VIEW LIVE TRAIN TRACKER",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AlarmDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KmrlTeal,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 13.5.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
