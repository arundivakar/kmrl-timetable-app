package com.example.kmrltimetable.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.AppDatabase
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
    val weekdayDefault: String? get() = dayDefaults[0] ?: dayDefaults[1] ?: dayDefaults[2] ?: dayDefaults[3] ?: dayDefaults[4] ?: dayDefaults[5]
    val sundayDefault: String? get() = dayDefaults[6]
    val weekendDefault: String? get() = sundayDefault
    val saturdayDefault: String? get() = dayDefaults[5]
}

class AdminViewModel(private var dao: TimetableDao) : ViewModel() {

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

    fun loadAdminData(context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (context != null) {
                    dao = AppDatabase.getDatabase(context).timetableDao()
                }
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
    // Default Timetables Management (Weekday Mon-Sat & Sunday)
    // -------------------------------------------------------------------------

    fun setDefaultWeekdayTimetable(timetableName: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        val days = listOf(0, 1, 2, 3, 4, 5) // Monday to Saturday (Mon–Sat)
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
                    successMessage = "✅ Default Weekday (Mon–Sat) set to $timetableName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to update weekday default: ${e.message}"
                )
            }
        }
    }

    fun setDefaultSundayTimetable(timetableName: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        val days = listOf(6) // 6 = Sunday only
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
                    successMessage = "✅ Default Sunday set to $timetableName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to update Sunday default: ${e.message}"
                )
            }
        }
    }

    // Kept for backward compatibility
    fun setDefaultWeekendTimetable(timetableName: String, includeSaturday: Boolean = false) {
        setDefaultSundayTimetable(timetableName)
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

    fun deleteTimetable(timetable: TimetableEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val assignedDays = _uiState.value.dayDefaults.filter { it.value == timetable.name }.keys
                if (assignedDays.isNotEmpty()) {
                    val daysStr = assignedDays.joinToString(", ") { dayIdx ->
                        when(dayIdx) {
                            0 -> "Monday"
                            1 -> "Tuesday"
                            2 -> "Wednesday"
                            3 -> "Thursday"
                            4 -> "Friday"
                            5 -> "Saturday"
                            6 -> "Sunday"
                            else -> "Day $dayIdx"
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cannot delete '${timetable.name}': it is default for $daysStr. Change default first."
                    )
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    dao.deleteStopTimesForTimetable(timetable.id)
                    dao.deleteTripsForTimetable(timetable.id)
                    dao.deleteTimetable(timetable.id)
                    dao.deleteOverridesForTimetable(timetable.name)
                }

                val updatedTimetables = withContext(Dispatchers.IO) { dao.getAllTimetables() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    timetables = updatedTimetables,
                    successMessage = "✅ Timetable '${timetable.name}' deleted successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to delete: ${e.message}"
                )
            }
        }
    }

    fun reloadBundledDatabase(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    com.example.kmrltimetable.data.local.AppDatabase.forceReloadFromAssets(context)
                }
                loadAdminData()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "✅ Database successfully reloaded from bundled assets!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to reload: ${e.message}"
                )
            }
        }
    }

    fun syncFromCloud(context: Context, force: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    com.example.kmrltimetable.data.remote.OtaSyncManager.checkAndSync(context, force = force)
                }
                when (result) {
                    is com.example.kmrltimetable.data.remote.OtaSyncResult.Success -> {
                        dao = AppDatabase.getDatabase(context).timetableDao()
                        loadAdminData(context)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "✅ ${result.message}"
                        )
                    }
                    is com.example.kmrltimetable.data.remote.OtaSyncResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "OTA Sync Failed: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to sync: ${e.message}"
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
