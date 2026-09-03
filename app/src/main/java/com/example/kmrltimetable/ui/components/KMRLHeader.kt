package com.example.kmrltimetable.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.R
import com.example.kmrltimetable.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HeaderBlock(
    currentTime: Date,
    isTomorrow: Boolean = false,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Bar Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "App Information",
                    tint = MaterialTheme.colorScheme.onBackground
                )
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
            
            // Dynamic Theme Toggle Icon
            IconButton(onClick = onThemeToggle) {
                if (isDarkMode) {
                    Icon(
                        Icons.Filled.LightMode,
                        contentDescription = "Switch to Light Mode",
                        tint = Color(0xFFFFD54F)
                    )
                } else {
                    Icon(
                        Icons.Filled.DarkMode,
                        contentDescription = "Switch to Dark Mode",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
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

@Composable
fun AboutAppDialog(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onThemeToggle: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kmrl_logo),
                    contentDescription = "KMRL Logo",
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    val titleText = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Bold)) { append("KMRL ") }
                        withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Bold)) { append("TRAIN FINDER") }
                    }
                    Text(text = titleText, fontSize = 17.sp)
                    Surface(
                        color = KmrlTeal.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "v5.0.4 (Release)",
                            color = KmrlTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Info Rows
                InfoItemRow(
                    icon = Icons.Outlined.DirectionsSubway,
                    title = "Route Coverage",
                    subtitle = "25 Stations • Aluva ↔ Tripunithura"
                )

                InfoItemRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Database Schedules",
                    subtitle = "64 Timetables (Weekday, Sunday, Festival, Special)"
                )

                InfoItemRow(
                    icon = Icons.Outlined.Info,
                    title = "App Features",
                    subtitle = "Real-time Timings • Offline First • Revenue Service Filter"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Theme Switch Row
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onThemeToggle() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isDarkMode) {
                                Icon(Icons.Filled.LightMode, contentDescription = null, tint = Color(0xFFFFD54F))
                                Text("Dark Mode Active", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            } else {
                                Icon(Icons.Filled.DarkMode, contentDescription = null, tint = KmrlTeal)
                                Text("Light Mode Active", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Text(
                            "Toggle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KmrlTeal
                        )
                    }
                }

                Text(
                    text = "Made for Kochi Metro Commuters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(KmrlTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
