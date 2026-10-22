package com.example.kmrltimetable.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kmrltimetable.data.local.entity.TripStopDetail
import com.example.kmrltimetable.data.model.StationAlarm
import com.example.kmrltimetable.ui.theme.KmrlOrange
import com.example.kmrltimetable.ui.theme.KmrlTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog to set a passenger assistance alarm before a train reaches a destination station.
 * E.g., when train is at KLMT (Kalamassery), setting an alarm 5 minutes before VAKK (Vadakkekotta)
 * to arrange platform security and wheelchair assistance.
 */
@Composable
fun SetStationAlarmDialog(
    stop: TripStopDetail,
    trainNo: String,
    direction: String,
    onDismiss: () -> Unit,
    onConfirm: (minutesBefore: Int, note: String) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var assistanceNote by remember { mutableStateOf("♿ Wheelchair Assistance") }
    var customNoteInput by remember { mutableStateOf("") }
    var isCustomNote by remember { mutableStateOf(false) }

    val platformText = if (direction == "UP") "Platform 1 (Towards Tripunithura)" else "Platform 2 (Towards Aluva)"
    val scheduledTimeFormatted = format12Hour(stop.departureTime)

    // Calculate ring time preview
    val ringTimePreview = remember(stop.departureTime, selectedMinutes) {
        calculateRingTimePreview(stop.departureTime, selectedMinutes)
    }

    val quickNotePresets = listOf(
        "♿ Wheelchair Assistance",
        "👮 Security Escort",
        "🧓 Senior Citizen Assist",
        "🧳 Luggage / Boarding Assist",
        "✏️ Custom..."
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFFFF3E0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = KmrlOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Station Assistance Alarm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Train $trainNo • $platformText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Station Information Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DESTINATION STATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = KmrlTeal,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${stop.stationName} (${stop.stationCode})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SCHEDULED DEP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = scheduledTimeFormatted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = KmrlOrange
                            )
                        }
                    }
                }

                // 3. Alarm Timing Selection (e.g. 5 mins before)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ALERT TIMING BEFORE ARRIVAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Pair(5, "5 min before"),
                            Pair(2, "2 min before"),
                            Pair(10, "10 min before"),
                            Pair(0, "At arrival")
                        ).forEach { (mins, label) ->
                            val isSelected = selectedMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) KmrlTeal else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) KmrlTeal else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMinutes = mins }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    if (mins == 5) {
                                        Text(
                                            text = "Best",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFFFFE082) else KmrlOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Live Trigger Time Preview Banner
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = ringTimePreview,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF5D4037)
                        )
                    }
                }

                // 5. Passenger Assistance Purpose / Note
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ASSISTANCE PURPOSE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    // Quick Preset Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickNotePresets.take(2).forEach { preset ->
                                val isSelected = !isCustomNote && assistanceNote == preset
                                PresetChip(
                                    text = preset,
                                    isSelected = isSelected,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        isCustomNote = false
                                        assistanceNote = preset
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickNotePresets.drop(2).take(2).forEach { preset ->
                                val isSelected = !isCustomNote && assistanceNote == preset
                                PresetChip(
                                    text = preset,
                                    isSelected = isSelected,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        isCustomNote = false
                                        assistanceNote = preset
                                    }
                                )
                            }
                            PresetChip(
                                text = "Custom",
                                isSelected = isCustomNote,
                                modifier = Modifier.weight(0.7f),
                                onClick = { isCustomNote = true }
                            )
                        }
                    }

                    if (isCustomNote) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customNoteInput,
                            onValueChange = { customNoteInput = it },
                            placeholder = { Text("e.g., Coach 2 wheelchair, door 3...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 6. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val finalNote = if (isCustomNote) {
                                customNoteInput.ifBlank { "Passenger Assistance" }
                            } else {
                                assistanceNote
                            }
                            onConfirm(selectedMinutes, finalNote)
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Set Alarm")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) KmrlOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            if (isSelected) KmrlOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFFD84315) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Dialog to inspect or cancel an existing active assistance alarm.
 */
@Composable
fun ManageActiveAlarmDialog(
    alarm: StationAlarm,
    onDismiss: () -> Unit,
    onCancelAlarm: (alarmId: String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFF3E0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = KmrlOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Active Assistance Alarm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${alarm.stationName} (${alarm.stationCode})",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow(label = "Train", value = "Train ${alarm.trainNo} (${alarm.direction} Line)")
                    DetailRow(label = "Platform", value = if (alarm.direction == "UP") "Platform 1" else "Platform 2")
                    DetailRow(label = "Scheduled Dep", value = format12Hour(alarm.scheduledTime))
                    DetailRow(label = "Alarm Timing", value = "${alarm.minutesBefore} minutes before arrival")
                    DetailRow(label = "Assistance Note", value = alarm.notes.ifBlank { "Standard Assistance" })
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCancelAlarm(alarm.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel Alarm")
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Urgent full in-app alert dialog shown when an alarm goes off while the user is inside the app.
 */
@Composable
fun InAppAlarmTriggeredDialog(
    alarm: StationAlarm,
    onAcknowledge: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alertPulsing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertScale"
    )

    Dialog(onDismissRequest = onAcknowledge) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
            elevation = CardDefaults.cardElevation(12.dp),
            border = BorderStroke(2.dp, Color(0xFFFF5722))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(scale)
                        .background(Color(0xFFFF5722).copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "🚨 PASSENGER ASSISTANCE ALERT!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFCCBC),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Train ${alarm.trainNo} is arriving at ${alarm.stationName} (${alarm.stationCode}) in ~${alarm.minutesBefore} minutes!",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = Color(0xFF37474F),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Platform: ${if (alarm.direction == "UP") "Platform 1" else "Platform 2"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF80CBC4)
                        )
                        Text(
                            text = "Scheduled Dep: ${format12Hour(alarm.scheduledTime)}",
                            fontSize = 13.sp,
                            color = Color(0xFFB0BEC5)
                        )
                        if (alarm.notes.isNotBlank()) {
                            Text(
                                text = "Assistance: ${alarm.notes}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }
                }

                Text(
                    text = "👉 Please arrange security & platform assistance staff immediately.",
                    fontSize = 12.sp,
                    color = Color(0xFFFFAB91),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ACKNOWLEDGE & STOP ALARM",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun calculateRingTimePreview(departureTime24: String?, minutesBefore: Int): String {
    if (departureTime24.isNullOrBlank()) return "Alarm will ring $minutesBefore min before arrival"
    return try {
        val parts = departureTime24.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        var ringH = h
        var ringM = m - minutesBefore
        if (ringM < 0) {
            ringM += 60
            ringH = (ringH - 1 + 24) % 24
        }
        val amPm = if (ringH < 12) "AM" else "PM"
        val h12 = when {
            ringH == 0 -> 12
            ringH > 12 -> ringH - 12
            else -> ringH
        }
        val ringStr = String.format(Locale.US, "%02d:%02d %s", h12, ringM, amPm)

        // Estimated minutes from now
        val now = java.util.Calendar.getInstance()
        val curMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val targetMins = ringH * 60 + ringM
        val diff = targetMins - curMins

        if (diff > 0) {
            "⚡ Alarm will ring at $ringStr (~$diff min from now)"
        } else if (diff in -5..0) {
            "⚡ Alarm will ring immediately (train is due soon!)"
        } else {
            "⚡ Scheduled for $ringStr (Today)"
        }
    } catch (e: Exception) {
        "Alarm will ring $minutesBefore min before arrival"
    }
}
