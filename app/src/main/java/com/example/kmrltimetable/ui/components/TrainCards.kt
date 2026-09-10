package com.example.kmrltimetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.ui.theme.*
import java.util.Date
import java.util.Calendar

@Composable
fun NextTrainCard(train: JourneyResult, currentTime: Date, isTomorrow: Boolean) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    val depMillis = if (isTomorrow) 1L else getCountdownMillis(train.departureTime, currentTime)
    val arrMillis = if (isTomorrow) 1L else getCountdownMillis(train.arrivalTime, currentTime)

    val isDepDeparted = !isTomorrow && depMillis <= 0
    val isArrDeparted = !isTomorrow && arrMillis <= 0

    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)

    val cal = Calendar.getInstance().apply { time = currentTime }
    val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val isRev = isRevenueService(train.terminalDepartureTime, train.departureTime, isSunday)

    val containerBg = if (isRev) KmrlTeal else Color(0xFF455A64)

    Card(
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(if (isRev) 1f else 0.85f),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRev) 6.dp else 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            
            // Train Icon in Circle
            Box(
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Departure Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        if (isDepDeparted) {
                            Text("DEPARTED", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("IN $depCountdown", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (!isRev) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFA000).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFD54F), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("NON-REVENUE SERVICE", fontSize = 9.sp, color = Color(0xFFFFE082), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                
                // Arrival Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        if (isArrDeparted) {
                            Text("DEPARTED", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        } else {
                            Text("IN $arrCountdown", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Pill
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Travel time: $duration", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FollowingTrainRow(train: JourneyResult, currentTime: Date, isTomorrow: Boolean, index: Int) {
    val depMillis = if (isTomorrow) 1L else getCountdownMillis(train.departureTime, currentTime)
    val arrMillis = if (isTomorrow) 1L else getCountdownMillis(train.arrivalTime, currentTime)

    val isDepDeparted = !isTomorrow && depMillis <= 0
    val isArrDeparted = !isTomorrow && arrMillis <= 0

    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormattedMins(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormattedMins(train.arrivalTime, currentTime)
    val colorAccent = if (index % 2 == 0) KmrlTeal else KmrlLime

    val cal = Calendar.getInstance().apply { time = currentTime }
    val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val isRev = isRevenueService(train.terminalDepartureTime, train.departureTime, isSunday)

    val cardBg = if (isRev) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val actualAccent = if (isRev) colorAccent else Color(0xFF78909C)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isRev) 1f else 0.8f)
            .border(
                1.dp,
                if (isRev) MaterialTheme.colorScheme.outlineVariant else Color(0xFF78909C).copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Colored Border
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(actualAccent))
            
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = actualAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Train ${train.trainNo}", fontSize = 12.sp, color = if (isRev) KmrlTeal else Color(0xFF546E7A), fontWeight = FontWeight.Bold)
                        if (!isRev) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFA000).copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, Color(0xFFFFA000).copy(alpha = 0.5f))
                            ) {
                                Text("NON-REVENUE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(train.departureTime, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(" Departure", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                    }
                    Text("Arrival: ${train.arrivalTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (isDepDeparted) {
                            Text("DEPARTED", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        } else {
                            Text("IN $depCountdown", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                        if (isArrDeparted) {
                            Text("DEPARTED", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        } else {
                            Text("IN $arrCountdown", fontSize = 12.sp, color = if (isRev) KmrlTeal else Color(0xFF546E7A), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FullTimetableRow(train: JourneyResult, colorAccent: Color, timetableDate: Date) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    
    val cal = Calendar.getInstance().apply { time = timetableDate }
    val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val isRev = isRevenueService(train.terminalDepartureTime, train.departureTime, isSunday)

    val cardBg = if (isRev) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val actualAccent = if (isRev) colorAccent else Color(0xFF78909C)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isRev) 1f else 0.8f)
            .border(
                1.dp,
                if (isRev) MaterialTheme.colorScheme.outlineVariant else Color(0xFF78909C).copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(actualAccent))
            
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = actualAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Train ${train.trainNo}", fontSize = 12.sp, color = if (isRev) KmrlTeal else Color(0xFF546E7A), fontWeight = FontWeight.Bold)
                    if (!isRev) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFA000).copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color(0xFFFFA000).copy(alpha = 0.5f))
                        ) {
                            Text("NON-REVENUE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 8.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("------", color = MaterialTheme.colorScheme.outlineVariant, maxLines = 1, letterSpacing = 2.sp)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
