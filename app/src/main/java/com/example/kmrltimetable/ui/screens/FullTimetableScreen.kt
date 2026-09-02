package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.components.*
import com.example.kmrltimetable.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullTimetableScreen(
    viewModel: TimetableViewModel,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Bar
        TopAppBar(
            title = { Text("Full Timetable", color = Color.White, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "App Information", tint = Color.White)
                }
            },
            actions = {
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
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = KmrlTeal)
        )
        
        if (uiState.fromStation == null || uiState.toStation == null) {
            EmptyState("Route Not Selected", "Please select a route from the Today tab.")
        } else if (uiState.allTrainsToday.isEmpty()) {
            EmptyState("No Trains Found", "No trains scheduled for this route.")
        } else {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(uiState.fromStation?.name?.uppercase() ?: "", style = MaterialTheme.typography.titleMedium, color = KmrlTeal, fontWeight = FontWeight.Bold)
                            Text("Source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            // Dotted line with swap icon in middle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("----------", color = KmrlTeal.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Box(
                                    modifier = Modifier.size(24.dp).background(KmrlTeal, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text("----------", color = KmrlTeal.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.weight(1f), maxLines = 1, textAlign = TextAlign.End)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(uiState.toStation?.name?.uppercase() ?: "", style = MaterialTheme.typography.titleMedium, color = KmrlTeal, fontWeight = FontWeight.Bold)
                            Text("Destination", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val dur = getDurationStr(uiState.allTrainsToday.firstOrNull()?.departureTime ?: "", uiState.allTrainsToday.firstOrNull()?.arrivalTime ?: "")
                            Text("Travel time: $dur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
                            Text(dateFormat.format(Date()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.allTrainsToday) { index, train ->
                    val colorAccent = if (index % 2 == 0) KmrlTeal else KmrlLime
                    FullTimetableRow(train, colorAccent, Date())
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Timetable shown is subject to change. Please check station announcements for the latest updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGrey
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
