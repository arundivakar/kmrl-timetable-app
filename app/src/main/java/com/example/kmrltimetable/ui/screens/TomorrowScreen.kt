package com.example.kmrltimetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.components.*
import com.example.kmrltimetable.ui.theme.KmrlTeal
import java.util.Date

@Composable
fun TomorrowScreen(
    viewModel: TimetableViewModel,
    onFindTrainsClick: () -> Unit,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()

    val tomorrowDate = Date(System.currentTimeMillis() + 86400000)

    LaunchedEffect(Unit) {
        viewModel.refreshUpcomingTrains()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBlock(
            currentTime = tomorrowDate,
            isTomorrow = true,
            isDarkMode = isDarkMode,
            onThemeToggle = onThemeToggle,
            onMenuClick = onMenuClick
        )

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

        // --- FIND TRAINS BUTTON ---
        if (uiState.fromStation != null && uiState.toStation != null) {
            Button(
                onClick = onFindTrainsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal)
            ) {
                Text(
                    "FIND TRAINS",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KmrlTeal)
            }
        }
    }
}
