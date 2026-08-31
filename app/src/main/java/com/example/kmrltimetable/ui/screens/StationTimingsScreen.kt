package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.data.local.entity.StationTrainResult
import com.example.kmrltimetable.ui.components.StationSearchDialog
import com.example.kmrltimetable.ui.components.getCountdownMillis
import com.example.kmrltimetable.ui.components.getCountdownFormattedMins
import com.example.kmrltimetable.ui.stations.DirectionFilter
import com.example.kmrltimetable.ui.stations.StationTimingsViewModel
import com.example.kmrltimetable.ui.stations.TrainFilter
import com.example.kmrltimetable.ui.theme.*
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StationTimingsScreen(viewModel: StationTimingsViewModel) {
    val uiState     by viewModel.uiState.collectAsState()
    val stations    by viewModel.stations.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    var showStationPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceLight)) {

        // ── Gradient Header ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(KmrlTeal, KmrlTeal.copy(alpha = 0.88f))))
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DirectionsSubway,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "STATION TIMINGS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Station Picker
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().clickable { showStationPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = uiState.selectedStation?.name ?: "Select Station",
                            fontWeight = if (uiState.selectedStation != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.selectedStation != null) TextDark else TextGrey,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextGrey)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Day toggle chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayChip("TODAY", selected = !uiState.isTomorrow) { viewModel.setTomorrow(false) }
                    DayChip("TOMORROW", selected = uiState.isTomorrow) { viewModel.setTomorrow(true) }
                }
            }
        }

        // ── Filter bar ───────────────────────────────────────────────────────
        Surface(color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyledFilterChip(
                        label = "UPCOMING",
                        selected = uiState.trainFilter == TrainFilter.UPCOMING,
                        onClick = { viewModel.setFilter(TrainFilter.UPCOMING) }
                    )
                    StyledFilterChip(
                        label = "ALL TRAINS",
                        selected = uiState.trainFilter == TrainFilter.ALL,
                        onClick = { viewModel.setFilter(TrainFilter.ALL) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                DirectionSegmentedControl(
                    selected = uiState.dirFilter,
                    onSelect = { viewModel.setDirectionFilter(it) }
                )

                if (uiState.timetableName.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = KmrlLime, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(uiState.timetableName, fontSize = 11.sp, color = TextGrey)
                    }
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        when {
            uiState.selectedStation == null -> NoStationSelected()
            uiState.isLoading               -> LoadingState()
            else -> {
                val aluvaList = filterByAvailability(uiState.toAluvaTrains, uiState.trainFilter, currentTime)
                val tphtList  = filterByAvailability(uiState.toTphtTrains,  uiState.trainFilter, currentTime)

                val showAluva = uiState.dirFilter != DirectionFilter.TO_TPHT
                val showTpht  = uiState.dirFilter != DirectionFilter.TO_ALUVA

                val aluvaEmpty = !showAluva || aluvaList.isEmpty()
                val tphtEmpty  = !showTpht  || tphtList.isEmpty()

                if (aluvaEmpty && tphtEmpty) {
                    NoTrainsFound()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (showAluva) {
                            item {
                                DirectionSectionHeader("TOWARDS ALUVA", aluvaList.size, KmrlTeal)
                            }
                            if (aluvaList.isEmpty()) {
                                item { NoTrainsInDirection("Aluva") }
                            } else {
                                item {
                                    NextTrainStationCard(
                                        train = aluvaList.first(),
                                        dirLabel = "Towards Aluva",
                                        currentTime = currentTime,
                                        isTomorrow = uiState.isTomorrow
                                    )
                                }
                                if (aluvaList.size > 1) {
                                    item { FollowingLabel() }
                                    itemsIndexed(aluvaList.drop(1)) { idx, train ->
                                        StationTrainRow(
                                            train = train,
                                            dirLabel = "Towards Aluva",
                                            currentTime = currentTime,
                                            isTomorrow = uiState.isTomorrow,
                                            accent = if (idx % 2 == 0) KmrlTeal else KmrlLime
                                        )
                                    }
                                }
                            }
                        }

                        if (showTpht) {
                            item {
                                DirectionSectionHeader("TOWARDS TPHT", tphtList.size, KmrlLime)
                            }
                            if (tphtList.isEmpty()) {
                                item { NoTrainsInDirection("TPHT") }
                            } else {
                                item {
                                    NextTrainStationCard(
                                        train = tphtList.first(),
                                        dirLabel = "Towards TPHT",
                                        currentTime = currentTime,
                                        isTomorrow = uiState.isTomorrow
                                    )
                                }
                                if (tphtList.size > 1) {
                                    item { FollowingLabel() }
                                    itemsIndexed(tphtList.drop(1)) { idx, train ->
                                        StationTrainRow(
                                            train = train,
                                            dirLabel = "Towards TPHT",
                                            currentTime = currentTime,
                                            isTomorrow = uiState.isTomorrow,
                                            accent = if (idx % 2 == 0) KmrlLime else KmrlTeal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Station picker dialog
    if (showStationPicker) {
        StationSearchDialog(
            stations = stations,
            onDismiss = { showStationPicker = false },
            onStationSelected = { station ->
                viewModel.selectStation(station)
                showStationPicker = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: filter by upcoming/all
// ─────────────────────────────────────────────────────────────────────────────

private fun filterByAvailability(
    trains: List<StationTrainResult>,
    mode: TrainFilter,
    now: Date
): List<StationTrainResult> = when (mode) {
    TrainFilter.ALL      -> trains
    TrainFilter.UPCOMING -> trains.filter { getCountdownMillis(it.departureTime, now) > 0 }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = if (selected) KmrlTeal else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StyledFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = KmrlTeal,
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = BorderGrey,
            selectedBorderColor = KmrlTeal
        )
    )
}

@Composable
private fun DirectionSectionHeader(title: String, count: Int, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.dp, 20.dp).background(accentColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.Bold, color = accentColor, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Surface(shape = CircleShape, color = accentColor.copy(alpha = 0.12f)) {
                Text(
                    "$count trains",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FollowingLabel() {
    Text(
        "FOLLOWING TRAINS",
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextGrey,
        letterSpacing = 0.8.sp
    )
}

// Highlighted NEXT TRAIN card
@Composable
private fun NextTrainStationCard(
    train: StationTrainResult,
    dirLabel: String,
    currentTime: Date,
    isTomorrow: Boolean
) {
    val millis   = if (isTomorrow) 1L else getCountdownMillis(train.departureTime, currentTime)
    val isDue    = !isTomorrow && millis <= 0
    val countdown = if (isTomorrow) "Tomorrow" else getCountdownFormattedMins(train.departureTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = KmrlTeal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        "NEXT TRAIN",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Train ${train.trainNo}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(train.departureTime, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(dirLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }

            if (isDue) {
                Surface(shape = RoundedCornerShape(8.dp), color = KmrlLime) {
                    Text("DUE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text("IN", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text(countdown, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}

// Following train row
@Composable
private fun StationTrainRow(
    train: StationTrainResult,
    dirLabel: String,
    currentTime: Date,
    isTomorrow: Boolean,
    accent: Color
) {
    val millis    = if (isTomorrow) 1L else getCountdownMillis(train.departureTime, currentTime)
    val isDue     = !isTomorrow && millis <= 0
    val countdown = if (isTomorrow) "Tomorrow" else getCountdownFormattedMins(train.departureTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accent))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Train ${train.trainNo}", fontSize = 11.sp, color = KmrlTeal, fontWeight = FontWeight.Bold)
                    Text(train.departureTime, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(dirLabel, fontSize = 11.sp, color = TextGrey)
                }

                if (isDue) {
                    Surface(shape = RoundedCornerShape(6.dp), color = KmrlLime.copy(alpha = 0.15f)) {
                        Text("DUE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = KmrlLime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("IN", fontSize = 9.sp, color = TextGrey)
                        Text(countdown, fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// Direction segmented button row
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionSegmentedControl(selected: DirectionFilter, onSelect: (DirectionFilter) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == DirectionFilter.BOTH,
            onClick = { onSelect(DirectionFilter.BOTH) },
            shape = SegmentedButtonDefaults.itemShape(0, 3),
            colors = SegmentedButtonDefaults.colors(activeContainerColor = KmrlTeal, activeContentColor = Color.White)
        ) { Text("BOTH", fontSize = 11.sp) }

        SegmentedButton(
            selected = selected == DirectionFilter.TO_ALUVA,
            onClick = { onSelect(DirectionFilter.TO_ALUVA) },
            shape = SegmentedButtonDefaults.itemShape(1, 3),
            colors = SegmentedButtonDefaults.colors(activeContainerColor = KmrlTeal, activeContentColor = Color.White)
        ) { Text("→ ALUVA", fontSize = 11.sp) }

        SegmentedButton(
            selected = selected == DirectionFilter.TO_TPHT,
            onClick = { onSelect(DirectionFilter.TO_TPHT) },
            shape = SegmentedButtonDefaults.itemShape(2, 3),
            colors = SegmentedButtonDefaults.colors(activeContainerColor = KmrlLime, activeContentColor = Color.White)
        ) { Text("→ TPHT", fontSize = 11.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / Loading states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoStationSelected() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = BorderGrey, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Select a station", style = MaterialTheme.typography.titleMedium, color = TextGrey)
            Spacer(Modifier.height(8.dp))
            Text("Tap the selector above to choose\na metro station", color = TextGrey, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = KmrlTeal)
    }
}

@Composable
private fun NoTrainsFound() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BorderGrey, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(12.dp))
            Text("No trains available", color = TextGrey, fontWeight = FontWeight.Bold)
            Text("All trains have departed for today", fontSize = 12.sp, color = TextGrey)
        }
    }
}

@Composable
private fun NoTrainsInDirection(direction: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SurfaceLight, RoundedCornerShape(8.dp))
            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp)).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No upcoming trains towards $direction", fontSize = 12.sp, color = TextGrey, textAlign = TextAlign.Center)
    }
}
