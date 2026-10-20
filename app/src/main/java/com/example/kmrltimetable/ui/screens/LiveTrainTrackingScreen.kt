package com.example.kmrltimetable.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.data.local.entity.TripStopDetail
import com.example.kmrltimetable.data.repository.LiveStatusType
import com.example.kmrltimetable.data.repository.TripWithStops
import com.example.kmrltimetable.ui.theme.KmrlOrange
import com.example.kmrltimetable.ui.theme.KmrlTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrainTrackingScreen(
    viewModel: LiveTrainTrackingViewModel,
    trainNo: String,
    onBack: () -> Unit,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(trainNo) {
        viewModel.startTracking(trainNo)
    }

    // Auto-scroll to current position when data loads
    LaunchedEffect(uiState.selectedTripIndex, uiState.liveLocation?.lastPassedIndex) {
        val targetIdx = uiState.liveLocation?.lastPassedIndex ?: 0
        if (targetIdx >= 0 && uiState.currentTrip?.stops?.isNotEmpty() == true) {
            // Add offset for header items (+2 items before the station list)
            val scrollIdx = (targetIdx + 2).coerceIn(0, uiState.currentTrip!!.stops.size + 1)
            listState.animateScrollToItem(scrollIdx)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Train ${uiState.trainNo.ifBlank { trainNo }} • Live Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        val trip = uiState.currentTrip?.trip
                        val dirText = if (trip != null) {
                            if (trip.direction == "UP") "Aluva ➔ Tripunithura (UP Line)" else "Tripunithura ➔ Aluva (DOWN Line)"
                        } else "KMRL Kochi Metro"
                        Text(
                            text = dirText,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFFFFD54F) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KmrlTeal)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KmrlTeal)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading schedule for Train $trainNo...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = KmrlOrange,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Train Not Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        } else {
            val tripWithStops = uiState.currentTrip

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Professional Schedule-Based Disclaimer Card
                item {
                    DisclaimerNoticeCard()
                }

                // 2. Live Status Hero Card
                item {
                    LiveHeroStatusCard(uiState = uiState)
                }

                // 3. Trips Selector Header
                if (uiState.trips.size > 1) {
                    item {
                        TripSelectorSection(
                            trips = uiState.trips,
                            selectedIndex = uiState.selectedTripIndex,
                            onTripSelected = { viewModel.selectTrip(it) }
                        )
                    }
                }

                // 4. Timeline Header (Labels)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DEPARTURE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "ROUTE STATIONS (25)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = KmrlTeal,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // 5. 25-Station Route Nodes
                if (tripWithStops != null) {
                    val stops = tripWithStops.stops
                    val liveLoc = uiState.liveLocation
                    val lastPassedIdx = liveLoc?.lastPassedIndex ?: -1
                    val nextUpcomingIdx = liveLoc?.nextUpcomingIndex ?: -1
                    val isTrainAtStation = liveLoc?.statusType == LiveStatusType.AT_STATION

                    itemsIndexed(stops) { index, stop ->
                        val isFirst = index == 0
                        val isLast = index == stops.size - 1

                        val isPassed = when {
                            liveLoc?.statusType == LiveStatusType.COMPLETED -> true
                            liveLoc?.statusType == LiveStatusType.NOT_STARTED || liveLoc?.statusType == LiveStatusType.WAITING_TERMINAL -> false
                            else -> index <= lastPassedIdx
                        }

                        val isCurrentStation = isTrainAtStation && (index == lastPassedIdx)
                        val isBetweenThisAndNext = !isTrainAtStation && (index == lastPassedIdx && nextUpcomingIdx == index + 1)

                        StationTimelineNodeItem(
                            stop = stop,
                            isFirst = isFirst,
                            isLast = isLast,
                            isPassed = isPassed,
                            isCurrentStation = isCurrentStation,
                            isBetweenThisAndNext = isBetweenThisAndNext,
                            trainNo = uiState.trainNo,
                            direction = tripWithStops.trip.direction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerNoticeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = KmrlTeal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Schedule-Based Estimates: Train positions and timings are calculated strictly from official KMRL timetables. Live GPS tracking is not available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun LiveHeroStatusCard(uiState: LiveTrainUiState) {
    val liveLoc = uiState.liveLocation
    val trip = uiState.currentTrip?.trip

    val (badgeColor, badgeText) = when (liveLoc?.statusType) {
        LiveStatusType.IN_TRANSIT -> Pair(Color(0xFF2E7D32), "SCHEDULED • IN TRANSIT")
        LiveStatusType.AT_STATION -> Pair(KmrlTeal, "AT STATION • BOARDING")
        LiveStatusType.WAITING_TERMINAL -> Pair(Color(0xFFE65100), "WAITING AT TERMINAL")
        LiveStatusType.NOT_STARTED -> Pair(Color(0xFF1565C0), "NOT STARTED")
        LiveStatusType.COMPLETED -> Pair(Color(0xFF616161), "TRIP COMPLETED")
        null -> Pair(Color.Gray, "UNKNOWN")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(color = badgeColor)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Text(
                    text = uiState.timetableName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = liveLoc?.statusText ?: "Analyzing schedule...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = liveLoc?.detailText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Origin Dep",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        format12Hour(trip?.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = KmrlTeal
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Line Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (trip?.direction == "UP") "UP (ALVA ➔ TPHT)" else "DOWN (TPHT ➔ ALVA)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Terminal Arr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        format12Hour(trip?.endTime),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = KmrlOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun TripSelectorSection(
    trips: List<TripWithStops>,
    selectedIndex: Int,
    onTripSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = "SELECT TRIP TODAY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(trips) { index, item ->
                val isSelected = index == selectedIndex
                val trip = item.trip
                val startFormatted = format12Hour(trip.startTime)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) KmrlTeal else MaterialTheme.colorScheme.surface,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { onTripSelected(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$startFormatted (${trip.direction})",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationTimelineNodeItem(
    stop: TripStopDetail,
    isFirst: Boolean,
    isLast: Boolean,
    isPassed: Boolean,
    isCurrentStation: Boolean,
    isBetweenThisAndNext: Boolean,
    trainNo: String,
    direction: String
) {
    val platformText = if (direction == "UP") "Platform 1" else "Platform 2"
    val timeFormatted = format12Hour(stop.departureTime)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Scheduled Time (Left Column)
            Box(
                modifier = Modifier.width(76.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (stop.departureTime != null) {
                    Column {
                        Text(
                            text = timeFormatted,
                            fontSize = 12.5.sp,
                            fontWeight = if (isCurrentStation) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "Passes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 2. Track & Node Indicator (Center Column)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // Continuous Vertical Track
                val trackColor = if (isPassed) KmrlTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(trackColor)
                )

                // Station Node Circle
                if (isCurrentStation) {
                    // Pulsing / Highlighted Active Station Node
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(KmrlTeal.copy(alpha = 0.25f), CircleShape)
                            .border(2.dp, KmrlTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(KmrlTeal, CircleShape)
                        )
                    }
                } else if (isPassed) {
                    // Passed Node: Solid Teal
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(KmrlTeal, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                } else {
                    // Upcoming Node: Hollow Circle
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }

            // 3. Station Details (Right Column)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.stationName,
                        fontWeight = if (isCurrentStation) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = if (isCurrentStation) KmrlTeal else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${stop.stationCode} • $platformText",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Station Status Label
                when {
                    isCurrentStation -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KmrlTeal,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                "AT STATION",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isPassed -> {
                        Text(
                            "Passed",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    else -> {
                        Text(
                            "Upcoming",
                            fontSize = 11.5.sp,
                            color = KmrlTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Floating Train Badge Between Stations (When in transit)
        if (isBetweenThisAndNext && !isLast) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(76.dp))

                Box(
                    modifier = Modifier.width(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .background(KmrlTeal)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KmrlTeal,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DirectionsTransit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Train $trainNo (In Transit)",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color, CircleShape)
    )
}

fun format12Hour(time24: String?): String {
    if (time24.isNullOrBlank()) return "--:--"
    return try {
        val parts = time24.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        String.format(java.util.Locale.US, "%02d:%02d %s", h12, m, amPm)
    } catch (e: Exception) {
        time24
    }
}
