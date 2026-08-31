package com.example.kmrltimetable.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmrltimetable.ui.theme.*

// --------------------------------------------------------------------------
// Admin Panel Root
// --------------------------------------------------------------------------

@Composable
fun AdminScreen(viewModel: AdminViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isSignedIn) {
        AdminLoginScreen(viewModel, uiState)
    } else {
        AdminDashboardScreen(viewModel, uiState)
    }
}

// --------------------------------------------------------------------------
// Login Screen
// --------------------------------------------------------------------------

@Composable
fun AdminLoginScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(BgLight),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Box(
                    modifier = Modifier.size(64.dp).background(KmrlTeal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                Text("Admin Panel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
                Text("KMRL Timetable Management", style = MaterialTheme.typography.bodyMedium, color = TextGrey)

                HorizontalDivider(color = BorderGrey)

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Admin Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = KmrlTeal) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KmrlTeal, focusedLabelColor = KmrlTeal)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = KmrlTeal) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KmrlTeal, focusedLabelColor = KmrlTeal)
                )

                // Error message
                AnimatedVisibility(visible = uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(uiState.error ?: "", modifier = Modifier.padding(12.dp), color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(
                    onClick = { viewModel.signIn(email, password) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Admin Dashboard
// --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Calendar", "Timetables", "Sync")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KmrlTeal),
                actions = {
                    IconButton(onClick = { viewModel.loadAdminData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = Color.White)
                    }
                }
            )
        },
        containerColor = SurfaceLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Success / Error banners
            uiState.successMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF2E7D32))
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFC62828))
                        }
                    }
                }
            }

            // Tab bar
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = KmrlTeal
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(title, modifier = Modifier.padding(vertical = 12.dp), fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KmrlTeal)
                }
            } else {
                when (selectedTab) {
                    0 -> CalendarTab(viewModel, uiState)
                    1 -> TimetablesTab(uiState)
                    2 -> SyncTab(uiState, viewModel)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Tab 1: Calendar (Date Assignments)
// --------------------------------------------------------------------------

@Composable
fun CalendarTab(viewModel: AdminViewModel, uiState: AdminUiState) {
    val next14Days = remember { viewModel.getNext14Days() }
    var showAssignDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf("") }
    var selectedDateLabel by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("NEXT 14 DAYS", style = MaterialTheme.typography.labelLarge, color = KmrlTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        }

        items(next14Days) { (date, label) ->
            val assignedTimetable = uiState.dateAssignments[date]

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (assignedTimetable != null) KmrlTeal.copy(alpha = 0.05f) else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                if (assignedTimetable != null) KmrlLime else BorderGrey,
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                            Text(
                                if (assignedTimetable != null) "Override: $assignedTimetable"
                                else "Default timetable",
                                color = if (assignedTimetable != null) KmrlTeal else TextGrey,
                                fontSize = 12.sp,
                                fontWeight = if (assignedTimetable != null) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = {
                                selectedDate = date
                                selectedDateLabel = label
                                showAssignDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = KmrlTeal, modifier = Modifier.size(20.dp))
                        }
                        if (assignedTimetable != null) {
                            IconButton(
                                onClick = { viewModel.removeDateAssignment(date) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove override", tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAssignDialog) {
        AssignTimetableDialog(
            dateLabel       = selectedDateLabel,
            timetables      = uiState.timetables.map { it.name },
            currentAssigned = uiState.dateAssignments[selectedDate],
            onDismiss       = { showAssignDialog = false },
            onConfirm       = { timetableName ->
                viewModel.assignTimetableToDate(selectedDate, timetableName)
                showAssignDialog = false
            }
        )
    }
}

// --------------------------------------------------------------------------
// Assignment Dialog
// --------------------------------------------------------------------------

@Composable
fun AssignTimetableDialog(
    dateLabel: String,
    timetables: List<String>,
    currentAssigned: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selected by remember { mutableStateOf(currentAssigned ?: timetables.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column {
                Text("Assign Timetable", fontWeight = FontWeight.Bold, color = TextDark)
                Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = TextGrey)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timetables.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = name }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == name, onClick = { selected = name }, colors = RadioButtonDefaults.colors(selectedColor = KmrlTeal))
                        Spacer(Modifier.width(8.dp))
                        Text(name, color = TextDark, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                enabled = selected.isNotBlank()
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextGrey) }
        }
    )
}

// --------------------------------------------------------------------------
// Tab 2: Timetables list
// --------------------------------------------------------------------------

@Composable
fun TimetablesTab(uiState: AdminUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("AVAILABLE TIMETABLES", style = MaterialTheme.typography.labelLarge, color = KmrlTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        }

        items(uiState.timetables) { timetable ->
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(KmrlTeal))
                    Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                        Text(timetable.name, fontWeight = FontWeight.Bold, color = TextDark)
                        Text(timetable.type, style = MaterialTheme.typography.bodySmall, color = KmrlTeal)
                        timetable.notes?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = TextGrey)
                        }
                    }
                    timetable.trainCount?.let { count ->
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
                            Text("$count", fontWeight = FontWeight.Bold, color = KmrlTeal, fontSize = 18.sp)
                            Text("trains", style = MaterialTheme.typography.labelSmall, color = TextGrey)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = KmrlTeal.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = KmrlTeal)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "To add new timetables, use the Python import script on your PC. New timetables will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Tab 3: Sync Status
// --------------------------------------------------------------------------

@Composable
fun SyncTab(uiState: AdminUiState, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("SYNC STATUS", style = MaterialTheme.typography.labelLarge, color = KmrlTeal, fontWeight = FontWeight.Bold)
        }
        item {
            InfoCard(label = "Firebase Config Version", value = "v${uiState.configVersion}", icon = Icons.Outlined.CloudSync)
        }
        item {
            InfoCard(label = "Last Local Sync", value = uiState.lastSyncTime.ifBlank { "Never" }, icon = Icons.Outlined.Schedule)
        }
        item {
            InfoCard(label = "Active Date Overrides", value = "${uiState.dateAssignments.size} dates", icon = Icons.Outlined.CalendarMonth)
        }
        item {
            Button(
                onClick = { viewModel.loadAdminData() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KmrlTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh from Firebase", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How sync works", fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(Modifier.height(4.dp))
                    Text("• Users sync automatically when they open the app", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    Text("• Version check is instant (~100ms)", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    Text("• Full sync only downloads when version changes", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                    Text("• Users without internet use their last cached config", style = MaterialTheme.typography.bodySmall, color = TextGrey)
                }
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderGrey, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(KmrlTeal.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = KmrlTeal, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = TextGrey)
                Text(value, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
            }
        }
    }
}
