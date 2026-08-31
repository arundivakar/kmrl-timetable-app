package com.example.kmrltimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.kmrltimetable.ui.TimetableViewModel
import com.example.kmrltimetable.ui.TimetableViewModelFactory
import com.example.kmrltimetable.ui.screens.FullTimetableScreen
import com.example.kmrltimetable.ui.screens.TodayScreen
import com.example.kmrltimetable.ui.screens.TomorrowScreen
import com.example.kmrltimetable.ui.theme.KmrlTeal
import com.example.kmrltimetable.ui.theme.KmrlTheme
import com.example.kmrltimetable.ui.theme.TextGrey

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
