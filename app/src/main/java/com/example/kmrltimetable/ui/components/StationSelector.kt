package com.example.kmrltimetable.ui.components

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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, BorderGrey, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                Column {
                    StationSearchBox("FROM", fromStation, stations, onFromSelected, isFrom = true)
                    HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(start = 50.dp, end = 16.dp))
                    StationSearchBox("TO", toStation, stations, onToSelected, isFrom = false)
                }
                
                // Swap Button layered exactly over the divider
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).offset(y = 0.dp).zIndex(1f)
                ) {
                    IconButton(
                        onClick = onSwap,
                        modifier = Modifier.size(36.dp).background(KmrlTeal, CircleShape).shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Swap", tint = Color.White)
                    }
                }
            }
            
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, color = TextGrey, fontWeight = FontWeight.Bold)
            Text(
                text = selectedStation?.name ?: "Select Station",
                fontSize = 16.sp,
                fontWeight = if (selectedStation != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedStation != null) TextDark else TextGrey
            )
        }
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextDark)
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
        Surface(modifier = Modifier.fillMaxSize(), color = BgLight) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text("Select Station", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
                }

                // Search Box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search stations...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KmrlTeal,
                        unfocusedBorderColor = BorderGrey,
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight
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
                            color = TextDark
                        )
                        HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
