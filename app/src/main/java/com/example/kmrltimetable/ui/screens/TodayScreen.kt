package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.components.*
import com.example.kmrltimetable.ui.theme.KmrlTeal

@Composable
fun TodayScreen(
    viewModel: TimetableViewModel,
    onFindTrainsClick: () -> Unit,
    onStationTimingSearch: (com.example.kmrltimetable.data.local.entity.StationEntity) -> Unit,
    onTrackTrainClick: (String) -> Unit = {},
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.refreshUpcomingTrains()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        HeaderBlock(
            currentTime = currentTime,
            isTomorrow = false,
            isDarkMode = isDarkMode,
            onThemeToggle = onThemeToggle,
            onMenuClick = onMenuClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- PROFESSIONAL SCHEDULE-BASED DISCLAIMER ---
        ScheduleNoticeDisclaimerCard()

        Spacer(modifier = Modifier.height(10.dp))
        TimetableStatusCard(uiState.activeTimetableName)
        Spacer(modifier = Modifier.height(14.dp))

        // --- STATION SELECTOR ---
        StationSelectorCard(
            stations = stations,
            fromStation = uiState.fromStation,
            toStation = uiState.toStation,
            onFromSelected = { viewModel.setFromStation(it) },
            onToSelected = { viewModel.setToStation(it) },
            onSwap = { viewModel.swapStations() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- FIND TRAINS BUTTON ---
        if (uiState.fromStation != null && uiState.toStation != null) {
            Button(
                onClick = onFindTrainsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal)
            ) {
                Text(
                    "FIND TRAINS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // --- LIVE TRAIN TRACKER CARD ---
        LiveTrainSearchCard(
            onTrackTrainClick = onTrackTrainClick
        )

        Spacer(modifier = Modifier.height(18.dp))

        // --- COMPACT STATION TIMING ---
        StationTimingCompactCard(
            stations = stations,
            onSearch = onStationTimingSearch
        )

        if (uiState.isLoading) {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KmrlTeal)
            }
        }
    }
}

@Composable
fun ScheduleNoticeDisclaimerCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = KmrlTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Schedule Notice: All timings and train locations are computed strictly from the active KMRL timetable. Live GPS tracking is not available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
fun LiveTrainSearchCard(
    onTrackTrainClick: (String) -> Unit
) {
    var trainInput by remember { mutableStateOf("") }
    val quickPills = listOf("01", "02", "03", "04", "05", "06", "07", "08")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(KmrlTeal.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsTransit,
                        contentDescription = null,
                        tint = KmrlTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Live Train Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Track train location as per timetable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = trainInput,
                onValueChange = { trainInput = it.filter { ch -> ch.isDigit() }.take(2) },
                placeholder = { Text("Enter Train ID (e.g., 1, 2, 07...)") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = KmrlTeal)
                },
                trailingIcon = {
                    if (trainInput.isNotEmpty()) {
                        IconButton(onClick = { trainInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KmrlTeal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Quick Selection Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Quick Pick: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickPills.size) { i ->
                        val pill = quickPills[i]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (trainInput == pill || trainInput == pill.trimStart('0')) KmrlTeal.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (trainInput == pill) KmrlTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { trainInput = pill }
                        ) {
                            Text(
                                text = pill,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (trainInput == pill) KmrlTeal else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (trainInput.isNotBlank()) {
                        onTrackTrainClick(trainInput)
                    }
                },
                enabled = trainInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmrlTeal,
                    disabledContainerColor = KmrlTeal.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "TRACK TRAIN ${if (trainInput.isNotBlank()) trainInput.padStart(2, '0') else ""}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
