package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    val todayDate = remember { Date() }
    val tomorrowDate = remember { Date(System.currentTimeMillis() + 86_400_000L) }
    val initialDate = if (isTomorrow) tomorrowDate else todayDate

    var selectedDate by remember(isTomorrow) { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.US)

    val isToday = isSameDay(selectedDate, currentTime)
    val isTomorrowDay = isSameDay(selectedDate, tomorrowDate)

    // Always fetch fresh trains and timetable for the selectedDate (respects date overrides)
    LaunchedEffect(selectedDate, uiState.fromStation?.id, uiState.toStation?.id) {
        viewModel.fetchTrainsForCustomDate(selectedDate)
    }

    val (trains, timetableName) = if (uiState.customSelectedDate != null && isSameDay(uiState.customSelectedDate!!, selectedDate)) {
        Pair(uiState.customDateTrains, uiState.customDateTimetableName)
    } else if (isToday) {
        Pair(uiState.allTrainsToday, uiState.activeTimetableName)
    } else if (isTomorrowDay) {
        Pair(uiState.allTrainsTomorrow, uiState.tomorrowTimetableName)
    } else {
        Pair(uiState.customDateTrains, uiState.customDateTimetableName)
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.time
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val pickedDate = localCal.time
                        selectedDate = pickedDate
                        viewModel.fetchTrainsForCustomDate(pickedDate)
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = KmrlTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = KmrlTeal,
                    todayDateBorderColor = KmrlTeal,
                    todayContentColor = KmrlTeal
                )
            )
        }
    }

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
                        
                        // Clickable Date Picker Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KmrlTeal.copy(alpha = 0.12f),
                            modifier = Modifier.clickable { showDatePicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormat.format(selectedDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KmrlTeal,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    contentDescription = "Change Date",
                                    tint = KmrlTeal,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    if (timetableName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Schedule: $timetableName",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val validTrains = if (isToday) {
                trains.filter { isTrainValidUpcoming(it.departureTime, currentTime) }
            } else {
                trains
            }

            if (validTrains.isEmpty()) {
                if (isToday) {
                    EmptyState("No trains available", "All trains have departed for today.")
                } else {
                    EmptyState("No trains found", "No trains scheduled for ${dateFormat.format(selectedDate)}.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isToday) "NEXT TRAIN" else "FIRST TRAIN",
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
                        NextTrainCard(validTrains.first(), currentTime, isTomorrow = !isToday)
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
                                FollowingTrainRow(train, currentTime, isTomorrow = !isToday, index = index)
                            }
                        }
                    }
                }
            }
        }
    }
}
