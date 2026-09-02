package com.example.kmrltimetable.ui.components

import androidx.compose.foundation.Image

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.painterResource
import com.example.kmrltimetable.R

@Composable
fun HeaderBlock(
    currentTime: Date,
    isTomorrow: Boolean = false,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Bar Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onBackground)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.kmrl_logo),
                    contentDescription = "KMRL Logo",
                    modifier = Modifier.height(32.dp).padding(end = 8.dp)
                )
                
                // Headline Text
                val logoText = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Bold)) { append("KMRL ") }
                    withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Bold)) { append("TRAIN FINDER") }
                }
                Text(text = logoText, fontSize = 18.sp, letterSpacing = 0.5.sp)
            }
            
            Row {
                IconButton(onClick = onThemeToggle) {
                    Icon(Icons.Default.Settings, contentDescription = "Toggle Theme", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
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
                    Text("Tomorrow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                Text(timeFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun TimetableStatusCard(timetableName: String) {
    if (timetableName.isEmpty()) return
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Timetable: $timetableName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                val updateDateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
                Text("Updated: $updateDateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
