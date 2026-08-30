package com.example.kmrltimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.TimetableViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- KMRL BRANDING COLORS ---
val KmrlTeal = Color(0xFF00BFA5)
val KmrlLime = Color(0xFFAEEA00)
val CharcoalBg = Color(0xFF121212)
val CharcoalSurface = Color(0xFF1E1E1E)
val CharcoalSurfaceVariant = Color(0xFF2C2C2C)

val KmrlColorScheme = darkColorScheme(
    primary = KmrlTeal,
    onPrimary = Color.Black,
    primaryContainer = KmrlTeal.copy(alpha = 0.2f),
    onPrimaryContainer = KmrlTeal,
    secondary = KmrlLime,
    onSecondary = Color.Black,
    background = CharcoalBg,
    surface = CharcoalSurface,
    surfaceVariant = CharcoalSurfaceVariant,
    onBackground = Color.White,
    onSurface = Color.White
)

enum class NavTab(val title: String, val icon: String) {
    TODAY("Today", "☀"),
    TOMORROW("Tomorrow", "🗓"),
    FULL("Full", "📋")
}

class MainActivity : ComponentActivity() {

    private val viewModel: TimetableViewModel by viewModels {
        val app = application as KMRLApplication
        TimetableViewModelFactory(app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fix Android Status Bar Overlap
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme(colorScheme = KmrlColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: TimetableViewModel) {
    var selectedTab by remember { mutableStateOf(NavTab.TODAY) }
    
    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.icon, fontSize = 24.sp) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (selectedTab) {
                NavTab.TODAY -> TodayScreen(viewModel)
                NavTab.TOMORROW -> TomorrowScreen(viewModel)
                NavTab.FULL -> FullTimetableScreen(viewModel)
            }
        }
    }
}

@Composable
fun TodayScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    val displayDateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
    val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- HEADER ---
        HeaderBlock("KMRL TIMETABLE", "${displayDateFormat.format(currentTime)} · ${displayTimeFormat.format(currentTime)}")

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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        } else if (uiState.fromStation != null && uiState.toStation != null) {
            
            val validTrains = uiState.allTrainsToday.filter { train ->
                getCountdownMillis(train.departureTime, currentTime) > 0
            }

            if (validTrains.isEmpty()) {
                EmptyState("NO MORE TRAINS", "No more scheduled trains for this route today.")
            } else {
                Text(
                    text = "NEXT TRAIN",
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                NextTrainCard(validTrains.first(), currentTime, isTomorrow = false)

                Spacer(modifier = Modifier.height(12.dp))
                
                if (validTrains.size > 1) {
                    Text(
                        text = "UPCOMING TRAINS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(validTrains.drop(1)) { train ->
                            FollowingTrainRow(train, currentTime, isTomorrow = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TomorrowScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()

    val tomorrowDate = Date(System.currentTimeMillis() + 86400000)
    val displayDateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HeaderBlock("TOMORROW'S TIMETABLE", displayDateFormat.format(tomorrowDate))

        if (uiState.tomorrowTimetableName.isNotEmpty()) {
            Text(
                text = "Timetable: ${uiState.tomorrowTimetableName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        } else if (uiState.fromStation != null && uiState.toStation != null) {
            
            if (uiState.allTrainsTomorrow.isEmpty()) {
                EmptyState("NO TRAINS SCHEDULED", "No trains are scheduled for this route tomorrow.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "UPCOMING TRAINS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(uiState.allTrainsTomorrow) { train ->
                        FollowingTrainRow(train, tomorrowDate, isTomorrow = true)
                    }
                }
            }
        }
    }
}

@Composable
fun FullTimetableScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HeaderBlock("FULL TIMETABLE", "Today's Complete Schedule")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.fromStation == null || uiState.toStation == null) {
            EmptyState("ROUTE NOT SELECTED", "Please select a route from the Train Finder tab.")
        } else if (uiState.allTrainsToday.isEmpty()) {
            EmptyState("NO TRAINS", "No trains found for this route.")
        } else {
            Text(
                text = "${uiState.fromStation?.name} → ${uiState.toStation?.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Train", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Dep", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Arr", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Dur", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                }
                items(uiState.allTrainsToday) { train ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(train.trainNo, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                        Text(train.departureTime, modifier = Modifier.weight(1f))
                        Text(train.arrivalTime, modifier = Modifier.weight(1f))
                        Text(getDurationStr(train.departureTime, train.arrivalTime), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, color = Color.Gray)
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                }
            }
        }
    }
}

// --- SHARED COMPONENTS ---

@Composable
fun HeaderBlock(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(message, textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }
}

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
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column {
                StationSearchBox("From:", fromStation, stations, onFromSelected)
                Spacer(modifier = Modifier.height(12.dp))
                StationSearchBox("To:", toStation, stations, onToSelected)
            }
            
            FloatingActionButton(
                onClick = onSwap,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(44.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("⇅", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun StationSearchBox(
    label: String,
    selectedStation: StationEntity?,
    stations: List<StationEntity>,
    onStationSelected: (StationEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showDialog = true }
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(
                text = selectedStation?.name ?: "Select Station",
                fontSize = 16.sp,
                fontWeight = if (selectedStation != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedStation != null) MaterialTheme.colorScheme.onSurface else Color.Gray
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
                        Text("✕", fontSize = 24.sp)
                    }
                    Text("Select Station", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
                }

                // Search Box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search stations...") },
                    leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 12.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                                .padding(16.dp),
                            fontSize = 18.sp
                        )
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun NextTrainCard(train: JourneyResult, currentTime: Date, isTomorrow: Boolean) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Train ${train.trainNo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Travel time · $duration", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(train.departureTime, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Departure", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("IN $depCountdown", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    Text("Arrival", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("IN $arrCountdown", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun FollowingTrainRow(train: JourneyResult, currentTime: Date, isTomorrow: Boolean) {
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Train ${train.trainNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(train.departureTime, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text(" Departure", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                }
                Text("Arrival · ${train.arrivalTime}", fontSize = 12.sp, color = Color.Gray)
            }
            if (!isTomorrow) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("IN $depCountdown", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                    Text("IN $arrCountdown", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// --- Utility Functions ---

fun getCountdownMillis(targetTimeStr: String, current: Date): Long {
    val targetFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    try {
        val target = targetFormat.parse(targetTimeStr) ?: return -1
        val cal = Calendar.getInstance().apply { time = current }
        val tCal = Calendar.getInstance().apply { time = target }
        cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
        cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
        cal.set(Calendar.SECOND, tCal.get(Calendar.SECOND))
        
        return cal.timeInMillis - current.time
    } catch(e: Exception) { return -1 }
}

fun getCountdownFormatted(targetTimeStr: String, current: Date): String {
    val diff = getCountdownMillis(targetTimeStr, current)
    if (diff <= 0) return "Due"
    val mins = diff / 60000
    val secs = (diff % 60000) / 1000
    return if (mins > 0) String.format(Locale.US, "%dm %02ds", mins, secs) else String.format(Locale.US, "%02ds", secs)
}

fun getDurationStr(dep: String, arr: String): String {
    val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    try {
        val d1 = format.parse(dep) ?: return ""
        val d2 = format.parse(arr) ?: return ""
        var diff = d2.time - d1.time
        if (diff < 0) diff += 24 * 60 * 60 * 1000 // Handle crossing midnight just in case
        val mins = diff / 60000
        return "${mins} min"
    } catch(e: Exception) { return "" }
}
