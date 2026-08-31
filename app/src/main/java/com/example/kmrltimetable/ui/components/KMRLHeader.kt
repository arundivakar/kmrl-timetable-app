package com.example.kmrltimetable.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HeaderBlock(currentTime: Date, isTomorrow: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Bar Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextDark)
            }
            
            // KMRL Logo Text
            val logoText = buildAnnotatedString {
                withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Black)) { append("K") }
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Black)) { append("M") }
                withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Black)) { append("R") }
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Black)) { append("L") }
                append(" ")
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Bold)) { append("TIMETABLE") }
            }
            Text(text = logoText, fontSize = 20.sp, letterSpacing = 0.5.sp)
            
            Row {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = TextDark)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextDark)
                }
            }
        }
        
        // Date / Time Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            
            if (isTomorrow) {
                Column {
                    Text("Tomorrow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, color = TextDark)
                }
            } else {
                Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextDark)
                Text(timeFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextDark)
            }
        }
    }
}

@Composable
fun TimetableStatusCard(timetableName: String) {
    if (timetableName.isEmpty()) return
    
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Timetable: $timetableName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextDark)
                val updateDateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
                Text("Updated: $updateDateStr", style = MaterialTheme.typography.bodySmall, color = TextGrey)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
        }
    }
}
