package com.example.kmrltimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.TimetableViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- KMRL BRANDING COLORS (LIGHT THEME) ---
val KmrlTeal = Color(0xFF009B8F)
val KmrlLime = Color(0xFF8CC63F)
val BgLight = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF9F9F9)
val BorderGrey = Color(0xFFE0E0E0)
val TextDark = Color(0xFF1E1E1E)
val TextGrey = Color(0xFF757575)

val KmrlLightColorScheme = lightColorScheme(
    primary = KmrlTeal,
    onPrimary = Color.White,
    primaryContainer = KmrlTeal.copy(alpha = 0.1f),
    onPrimaryContainer = KmrlTeal,
    secondary = KmrlLime,
    onSecondary = Color.White,
    background = BgLight,
    surface = BgLight,
    surfaceVariant = SurfaceLight,
    onBackground = TextDark,
    onSurface = TextDark
)

enum class NavTab(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Home),
    TOMORROW("Tomorrow", Icons.Default.DateRange),
    FULL("Full Timetable", Icons.Default.List)
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
            MaterialTheme(colorScheme = KmrlLightColorScheme) {
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
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KmrlTeal,
                            selectedTextColor = KmrlTeal,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextGrey,
                            unselectedTextColor = TextGrey
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
fun HeaderBlock(currentTime: Date, isTomorrow: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Bar Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextDark)
            }
            
            // KMRL Logo Text
            val logoText = buildAnnotatedString {
                withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Black)) { append("K") }
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Black)) { append("M") }
                withStyle(style = SpanStyle(color = KmrlLime, fontWeight = FontWeight.Black)) { append("R") }
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Black)) { append("L") }
                append(" ")
                withStyle(style = SpanStyle(color = KmrlTeal, fontWeight = FontWeight.Bold)) { append("TIMETABLE") }
            }
            Text(text = logoText, fontSize = 20.sp, letterSpacing = 0.5.sp)
            
            Row {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = TextDark)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextDark)
                }
            }
        }
        
        // Date / Time Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            
            if (isTomorrow) {
                Column {
                    Text("Tomorrow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, color = TextDark)
                }
            } else {
                Text(dateFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextDark)
                Text(timeFormat.format(currentTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextDark)
            }
        }
    }
}

@Composable
fun TimetableStatusCard(timetableName: String) {
    if (timetableName.isEmpty()) return
    
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Timetable: $timetableName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextDark)
                val updateDateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
                Text("Updated: $updateDateStr", style = MaterialTheme.typography.bodySmall, color = TextGrey)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullTimetableScreen(viewModel: TimetableViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().background(SurfaceLight)) {
        // App Bar for Search Results
        TopAppBar(
            title = { Text("Train Search Results", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White) }
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = KmrlTeal)
        )
        
        if (uiState.fromStation == null || uiState.toStation == null) {
            EmptyState("Route Not Selected", "Please select a route from the Today tab.")
        } else if (uiState.allTrainsToday.isEmpty()) {
            EmptyState("No Trains Found", "No trains scheduled for this route.")
        } else {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(uiState.fromStation?.name?.uppercase() ?: "", style = MaterialTheme.typography.titleMedium, color = KmrlTeal, fontWeight = FontWeight.Bold)
                            Text("Source", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                        }
                        
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            // Dotted line with swap icon in middle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("----------", color = KmrlTeal.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Box(
                                    modifier = Modifier.size(24.dp).background(KmrlTeal, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text("----------", color = KmrlTeal.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.weight(1f), maxLines = 1, textAlign = TextAlign.End)
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(uiState.toStation?.name?.uppercase() ?: "", style = MaterialTheme.typography.titleMedium, color = KmrlTeal, fontWeight = FontWeight.Bold)
                            Text("Destination", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderGrey)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val dur = getDurationStr(uiState.allTrainsToday.firstOrNull()?.departureTime ?: "", uiState.allTrainsToday.firstOrNull()?.arrivalTime ?: "")
                            Text("Travel time: $dur", style = MaterialTheme.typography.bodySmall, color = TextDark, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
                            Text(dateFormat.format(Date()), style = MaterialTheme.typography.bodySmall, color = TextDark, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.allTrainsToday) { index, train ->
                    val colorAccent = if (index % 2 == 0) KmrlTeal else KmrlLime
                    FullTimetableRow(train, colorAccent)
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Timetable shown is subject to change. Please check station announcements for the latest updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGrey
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// --- SHARED COMPONENTS ---

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Train, contentDescription = null, tint = BorderGrey, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
        Text(message, textAlign = TextAlign.Center, color = TextGrey, style = MaterialTheme.typography.bodyMedium)
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
            
            HorizontalDivider(color = BorderGrey)
            
            // Travel Time Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TextGrey, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Travel time", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                }
                val durStr = if (fromStation != null && toStation != null) "10 min" else "--" // Hardcoded 10 min for demo unless calculated
                Text(durStr, style = MaterialTheme.typography.bodyMedium, color = KmrlLime, fontWeight = FontWeight.Bold)
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

@Composable
fun NextTrainCard(train: JourneyResult, currentTime: Date, isTomorrow: Boolean) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = KmrlTeal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            
            // Train Icon in Circle
            Box(
                modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Departure Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        Text("IN $depCountdown", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Arrival Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    if (!isTomorrow) {
                        Text("IN $arrCountdown", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pill
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Travel time: $duration", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FollowingTrainRow(train: JourneyResult, currentTime: Date, isTomorrow: Boolean, index: Int) {
    val depCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.departureTime, currentTime)
    val arrCountdown = if (isTomorrow) "Tomorrow" else getCountdownFormatted(train.arrivalTime, currentTime)
    val colorAccent = if (index % 2 == 0) KmrlTeal else KmrlLime

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Colored Border
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(colorAccent))
            
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = colorAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Train ${train.trainNo}", fontSize = 12.sp, color = KmrlTeal, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(train.departureTime, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                        Text(" Departure", fontSize = 11.sp, color = TextGrey, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                    }
                    Text("Arrival: ${train.arrivalTime}", fontSize = 12.sp, color = TextGrey)
                }
                
                if (!isTomorrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("IN $depCountdown", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                        Text("IN $arrCountdown", fontSize = 12.sp, color = KmrlLime, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
            }
        }
    }
}

@Composable
fun FullTimetableRow(train: JourneyResult, colorAccent: Color) {
    val duration = getDurationStr(train.departureTime, train.arrivalTime)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(colorAccent))
            
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, tint = colorAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Train ${train.trainNo}", fontSize = 12.sp, color = KmrlTeal, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(train.departureTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Departure", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(duration, style = MaterialTheme.typography.labelSmall, color = TextGrey)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("------", color = BorderGrey, maxLines = 1, letterSpacing = 2.sp)
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = BorderGrey, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(train.arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Arrival", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    }
                    
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrey)
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
