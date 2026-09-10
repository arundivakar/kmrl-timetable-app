package com.example.kmrltimetable.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.TimetableDao
import com.example.kmrltimetable.data.local.entity.DayDefaultEntity
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.TimetableEntity
import com.example.kmrltimetable.data.remote.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AdminUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Data
    val timetables: List<TimetableEntity> = emptyList(),
    val dateAssignments: Map<String, String> = emptyMap(),   // date -> timetableName
    val dayDefaults: Map<Int, String> = emptyMap(),           // dayOfWeek -> timetableName
    val lastSyncTime: String = "",
    val configVersion: Long = 0
) {
    val weekdayDefault: String? get() = dayDefaults[0] ?: dayDefaults[1] ?: dayDefaults[2] ?: dayDefaults[3] ?: dayDefaults[4]
    val weekendDefault: String? get() = dayDefaults[6] ?: dayDefaults[5]
    val saturdayDefault: String? get() = dayDefaults[5]
    val sundayDefault: String? get() = dayDefaults[6]
}

class AdminViewModel(private val dao: TimetableDao) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        // Check if already signed in from a previous session
        if (FirebaseManager.isSignedIn()) {
            _uiState.value = _uiState.value.copy(isSignedIn = true)
            loadAdminData()
        }
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.signIn(email, password)
                _uiState.value = _uiState.value.copy(isSignedIn = true, isLoading = false)
                loadAdminData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message}"
                )
            }
        }
    }

    fun signOut() {
        FirebaseManager.signOut()
        _uiState.value = AdminUiState(isSignedIn = false)
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    fun loadAdminData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // All DAO calls must run on IO dispatcher
                val timetables = withContext(Dispatchers.IO) { dao.getAllTimetables() }

                // Load remote assignments
                val assignments = FirebaseManager.fetchDateAssignments()
                val remoteDefaults = FirebaseManager.fetchDayDefaults()
                val dayDefaults = if (remoteDefaults.isNotEmpty()) {
                    remoteDefaults
                } else {
                    withContext(Dispatchers.IO) {
                        dao.getDayDefaults().associate { it.dayOfWeek to it.timetableName }
                    }
                }
                val config      = FirebaseManager.fetchConfig()

                // Load last sync time from local metadata
                val lastSync = withContext(Dispatchers.IO) {
                    dao.getSyncMetadata("last_sync_time")?.value ?: "Never"
                }

                _uiState.value = _uiState.value.copy(
                    isLoading       = false,
                    timetables      = timetables,
                    dateAssignments = assignments,
                    dayDefaults     = dayDefaults,
                    lastSyncTime    = lastSync,
                    configVersion   = config.version
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Default Timetables Management (Weekday & Weekend)
    // -------------------------------------------------------------------------

    fun setDefaultWeekdayTimetable(timetableName: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        val days = listOf(0, 1, 2, 3, 4) // Monday to Friday
        val updates = days.associateWith { timetableName }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.setDayDefaults(updates, adminEmail)
                withContext(Dispatchers.IO) {
                    dao.insertDayDefaults(days.map { d ->
                        DayDefaultEntity(dayOfWeek = d, timetableName = timetableName)
                    })
                }
                val newDefaults = _uiState.value.dayDefaults.toMutableMap().apply {
                    days.forEach { put(it, timetableName) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dayDefaults = newDefaults,
                    successMessage = "✅ Default Weekday (Mon–Fri) set to $timetableName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to update weekday default: ${e.message}"
                )
            }
        }
    }

    fun setDefaultWeekendTimetable(timetableName: String, includeSaturday: Boolean = true) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        val days = if (includeSaturday) listOf(5, 6) else listOf(6) // 5=Saturday, 6=Sunday
        val updates = days.associateWith { timetableName }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.setDayDefaults(updates, adminEmail)
                withContext(Dispatchers.IO) {
                    dao.insertDayDefaults(days.map { d ->
                        DayDefaultEntity(dayOfWeek = d, timetableName = timetableName)
                    })
                }
                val newDefaults = _uiState.value.dayDefaults.toMutableMap().apply {
                    days.forEach { put(it, timetableName) }
                }
                val label = if (includeSaturday) "Weekend (Sat & Sun)" else "Sunday"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dayDefaults = newDefaults,
                    successMessage = "✅ Default $label set to $timetableName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to update weekend default: ${e.message}"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Date Assignment Management
    // -------------------------------------------------------------------------

    fun assignTimetableToDate(date: String, timetableName: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.setDateAssignment(date, timetableName, adminEmail)

                // Immediately reflect in local Room on IO thread
                withContext(Dispatchers.IO) {
                    dao.clearAllOverrides()
                    val updatedAssignments = FirebaseManager.fetchDateAssignments()
                    dao.insertOverrides(updatedAssignments.map { (d, t) ->
                        ScheduleOverrideEntity(overrideDate = d, timetableName = t)
                    })
                }

                val updatedAssignments = FirebaseManager.fetchDateAssignments()
                _uiState.value = _uiState.value.copy(
                    isLoading       = false,
                    dateAssignments = updatedAssignments,
                    successMessage  = "✅ $date → $timetableName saved successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to save: ${e.message}"
                )
            }
        }
    }

    fun removeDateAssignment(date: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.removeDateAssignment(date, adminEmail)
                val updatedAssignments = FirebaseManager.fetchDateAssignments()
                withContext(Dispatchers.IO) {
                    dao.clearAllOverrides()
                    dao.insertOverrides(updatedAssignments.map { (d, t) ->
                        ScheduleOverrideEntity(overrideDate = d, timetableName = t)
                    })
                }
                _uiState.value = _uiState.value.copy(
                    isLoading       = false,
                    dateAssignments = updatedAssignments,
                    successMessage  = "✅ Override for $date removed (reverted to default)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to remove: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    // -------------------------------------------------------------------------
    // Helper: next 14 days for the calendar view
    // -------------------------------------------------------------------------

    fun getNext14Days(): List<Pair<String, String>> {
        val fmt        = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt   = SimpleDateFormat("EEE, d MMM", Locale.US)
        val cal        = Calendar.getInstance()
        return (0..13).map {
            val date  = fmt.format(cal.time)
            val label = labelFmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            Pair(date, label)
        }
    }
}
