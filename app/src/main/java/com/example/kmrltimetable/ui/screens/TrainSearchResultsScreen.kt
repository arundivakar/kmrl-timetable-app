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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarToday
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
fun TrainSearchResultsScreen(
    viewModel: TimetableViewModel,
    isTomorrow: Boolean,
    onBack: () -> Unit,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    
    val trains = if (isTomorrow) uiState.allTrainsTomorrow else uiState.allTrainsToday
    
    // Target date for display
    val targetDate = if (isTomorrow) Date(System.currentTimeMillis() + 86_400_000L) else Date()
    val dateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.US)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Bar
        TopAppBar(
            title = { Text("Train Search Results", color = Color.White, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
            EmptyState("Route Not Selected", "Please select a route to see trains.")
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
                            val dur = getDurationStr(trains.firstOrNull()?.departureTime ?: "", trains.firstOrNull()?.arrivalTime ?: "")
                            Text("Travel time: $dur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dateFormat.format(targetDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val validTrains = trains.filter { train ->
                if (isTomorrow) true else getCountdownMillis(train.departureTime, currentTime) > 0
            }

            if (validTrains.isEmpty()) {
                EmptyState("No trains available", "All trains have departed for today.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NEXT TRAIN",
                                style = MaterialTheme.typography.labelLarge,
                                color = KmrlTeal,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Train ${validTrains.first().trainNo}",
                                style = MaterialTheme.typography.labelMedium,
                                color = KmrlTeal
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NextTrainCard(validTrains.first(), currentTime, isTomorrow = isTomorrow)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (validTrains.size > 1) {
                        item {
                            Text(
                                text = "FOLLOWING TRAINS",
                                style = MaterialTheme.typography.labelLarge,
                                color = KmrlTeal,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(validTrains.drop(1)) { index, train ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                FollowingTrainRow(train, currentTime, isTomorrow = isTomorrow, index = index)
                            }
                        }
                    }
                }
            }
        }
    }
}
