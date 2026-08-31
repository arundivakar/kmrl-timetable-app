package com.example.kmrltimetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.ui.theme.*
import java.util.Date

@Composable
fun NextTrainCard(train: JourneyResult, currentTime: Date, isTomorrow: Boolean) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = KmrlTeal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            
            // Train Icon in Circle
            Box(
                modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Departure Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        Text("IN $depCountdown", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Arrival Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        Text("IN $arrCountdown", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pill
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Travel time: $duration", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FollowingTrainRow(train: JourneyResult, currentTime: Date, isTomorrow: Boolean, index: Int) {
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)
    val colorAccent = if (index % 2 == 0) KmrlTeal else KmrlLime

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Colored Border
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(colorAccent))
            
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = colorAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Train ${train.trainNo}", fontSize = 12.sp, color = KmrlTeal, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(train.departureTime, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                        Text(" Departure", fontSize = 11.sp, color = TextGrey, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                    }
                    Text("Arrival: ${train.arrivalTime}", fontSize = 12.sp, color = TextGrey)
                }
                
                if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("IN $depCountdown", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                        Text("IN $arrCountdown", fontSize = 12.sp, color = KmrlLime, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
            }
        }
    }
}

@Composable
fun FullTimetableRow(train: JourneyResult, colorAccent: Color) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(colorAccent))
            
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = colorAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Train ${train.trainNo}", fontSize = 12.sp, color = KmrlTeal, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(duration, style = MaterialTheme.typography.labelSmall, color = TextGrey)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("------", color = BorderGrey, maxLines = 1, letterSpacing = 2.sp)
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = BorderGrey, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    }
                    
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
                }
            }
        }
    }
}
