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
import java.util.Date

@Composable
fun TomorrowScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()

    val tomorrowDate = Date(System.currentTimeMillis() + 86400000)

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBlock(tomorrowDate, isTomorrow = true)

        Spacer(modifier = Modifier.height(12.dp))
        TimetableStatusCard(uiState.tomorrowTimetableName)
        Spacer(modifier = Modifier.height(16.dp))

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
            
            if (uiState.allTrainsTomorrow.isEmpty()) {
                EmptyState("No trains scheduled", "Please select a valid route.")
            } else {
                Text(
                    text = "UPCOMING TRAINS",
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
                    itemsIndexed(uiState.allTrainsTomorrow) { index, train ->
                        FollowingTrainRow(train, tomorrowDate, isTomorrow = true, index = index)
                    }
                }
            }
        }
    }
}
