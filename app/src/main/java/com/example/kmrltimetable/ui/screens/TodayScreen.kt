package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.components.*
import com.example.kmrltimetable.ui.theme.KmrlTeal

@Composable
fun TodayScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBlock(currentTime, isTomorrow = false)

        Spacer(modifier = Modifier.height(12.dp))
        TimetableStatusCard(uiState.activeTimetableName)
        Spacer(modifier = Modifier.height(16.dp))

        // --- STATION SELECTOR ---
        StationSelectorCard(
            stations = stations,
            fromStation = uiState.fromStation,
            toStation = uiState.toStation,
            onFromSelected = { viewModel.setFromStation(it) },
            onToSelected = { viewModel.setToStation(it) },
            onSwap = { viewModel.swapStations() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KmrlTeal)
            }
        } else if (uiState.fromStation != null && uiState.toStation != null) {
            
            val validTrains = uiState.allTrainsToday.filter { train ->
                getCountdownMillis(train.departureTime, currentTime) > 0
            }

            if (validTrains.isEmpty()) {
                EmptyState("No more trains today", "Please check tomorrow's schedule.")
            } else {
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
                NextTrainCard(validTrains.first(), currentTime, isTomorrow = false)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (validTrains.size > 1) {
                    Text(
                        text = "FOLLOWING TRAINS",
                        style = MaterialTheme.typography.labelLarge,
                        color = KmrlTeal,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(validTrains.drop(1)) { index, train ->
                            FollowingTrainRow(train, currentTime, isTomorrow = false, index = index)
                        }
                    }
                }
            }
        }
    }
}
