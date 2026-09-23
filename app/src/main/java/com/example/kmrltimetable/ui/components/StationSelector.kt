package com.example.kmrltimetable.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.ui.theme.*

@Composable
fun StationSelectorCard(
    stations: List<StationEntity>,
    fromStation: StationEntity?,
    toStation: StationEntity?,
    onFromSelected: (StationEntity) -> Unit,
    onToSelected: (StationEntity) -> Unit,
    onSwap: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                Column {
                    StationSearchBox("FROM", fromStation, stations, onFromSelected, isFrom = true)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 50.dp, end = 20.dp)
                    )
                    StationSearchBox("TO", toStation, stations, onToSelected, isFrom = false)
                }
                
                // Modern Interchange Button cleanly centered over the divider
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .zIndex(1f)
                ) {
                    StationSwapButton(onSwap = onSwap)
                }
            }
        }
    }
}

@Composable
fun StationSwapButton(
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swap_rotation"
    )

    Surface(
        onClick = {
            rotationAngle += 180f
            onSwap()
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.5.dp, KmrlTeal.copy(alpha = 0.35f)),
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                rotationZ = animatedRotation
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Swap stations",
                tint = KmrlTeal,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun StationSearchBox(
    label: String,
    selectedStation: StationEntity?,
    stations: List<StationEntity>,
    onStationSelected: (StationEntity) -> Unit,
    isFrom: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    val iconColor = if (isFrom) KmrlTeal else KmrlLime

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 40.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                text = selectedStation?.name ?: "Select Station",
                fontSize = 16.sp,
                fontWeight = if (selectedStation != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedStation != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        StationSearchDialog(
            stations = stations,
            onDismiss = { showDialog = false },
            onStationSelected = { 
                onStationSelected(it)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSearchDialog(
    stations: List<StationEntity>,
    onDismiss: () -> Unit,
    onStationSelected: (StationEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredStations = stations.filter { it.name.contains(query, ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "Select Station",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                // Search Box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search stations...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KmrlTeal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // List
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredStations) { station ->
                        Text(
                            text = station.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStationSelected(station) }
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StationTimingCompactCard(
    stations: List<StationEntity>,
    onSearch: (StationEntity) -> Unit
) {
    var selectedStation by remember { mutableStateOf<StationEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "STATION TIMING",
            style = MaterialTheme.typography.labelLarge,
            color = KmrlTeal,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showDialog = true }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedStation?.name ?: "Select Station",
                        fontWeight = if (selectedStation != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedStation != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            IconButton(
                onClick = { selectedStation?.let { onSearch(it) } },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (selectedStation != null) KmrlTeal else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                enabled = selectedStation != null
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (selectedStation != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        StationSearchDialog(
            stations = stations,
            onDismiss = { showDialog = false },
            onStationSelected = { 
                selectedStation = it
                showDialog = false
            }
        )
    }
}

