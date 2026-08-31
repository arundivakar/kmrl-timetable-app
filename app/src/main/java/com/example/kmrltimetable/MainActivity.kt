package com.example.kmrltimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.TimetableViewModelFactory
import com.example.kmrltimetable.ui.admin.AdminScreen
import com.example.kmrltimetable.ui.admin.AdminViewModel
import com.example.kmrltimetable.ui.screens.FullTimetableScreen
import com.example.kmrltimetable.ui.screens.StationTimingsScreen
import com.example.kmrltimetable.ui.screens.TodayScreen
import com.example.kmrltimetable.ui.screens.TomorrowScreen
import com.example.kmrltimetable.ui.stations.StationTimingsViewModel
import com.example.kmrltimetable.ui.theme.KmrlTeal
import com.example.kmrltimetable.ui.theme.KmrlTheme
import com.example.kmrltimetable.ui.theme.TextGrey

enum class NavTab(val title: String, val icon: ImageVector, val showInBar: Boolean = true) {
    TODAY("Today", Icons.Default.Home),
    TOMORROW("Tomorrow", Icons.Default.DateRange),
    FULL("Full", Icons.Default.List),
    STATION("Station", Icons.Outlined.DirectionsSubway),
    ADMIN("Admin", Icons.Default.AdminPanelSettings, showInBar = false)
}

class MainActivity : ComponentActivity() {

    private val viewModel: TimetableViewModel by viewModels {
        val app = application as KMRLApplication
        TimetableViewModelFactory(app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            KmrlTheme {
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
    var logoTapCount by remember { mutableIntStateOf(0) }

    // ViewModels — created lazily with access to the shared repository/DAO
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as KMRLApplication
    val adminViewModel: AdminViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AdminViewModel(app.database.timetableDao()) as T
            }
        }
    )
    val stationViewModel: StationTimingsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StationTimingsViewModel(app.repository) as T
            }
        }
    )

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavTab.values().filter { it.showInBar }.forEach { tab ->
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
                // Hidden Admin tab — visible only after 5 rapid taps on it
                if (logoTapCount >= 5 || selectedTab == NavTab.ADMIN) {
                    NavigationBarItem(
                        selected = selectedTab == NavTab.ADMIN,
                        onClick = { selectedTab = NavTab.ADMIN },
                        icon = { Icon(NavTab.ADMIN.icon, contentDescription = "Admin") },
                        label = { Text("Admin") },
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
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                // Tapping the screen area 5 times reveals Admin tab
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            logoTapCount++
                        }
                    )
                }
        ) {
            when (selectedTab) {
                NavTab.TODAY    -> TodayScreen(viewModel)
                NavTab.TOMORROW -> TomorrowScreen(viewModel)
                NavTab.FULL     -> FullTimetableScreen(viewModel)
                NavTab.STATION  -> StationTimingsScreen(stationViewModel)
                NavTab.ADMIN    -> AdminScreen(adminViewModel)
            }
        }
    }
}

