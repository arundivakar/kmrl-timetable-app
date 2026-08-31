package com.example.kmrltimetable.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.TimetableDao
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.TimetableEntity
import com.example.kmrltimetable.data.remote.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
)

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
                // Load local timetables
                val timetables = dao.getAllTimetables()

                // Load remote assignments
                val assignments = FirebaseManager.fetchDateAssignments()
                val dayDefaults = FirebaseManager.fetchDayDefaults()
                val config      = FirebaseManager.fetchConfig()

                // Load last sync time from local metadata
                val lastSync = dao.getSyncMetadata("last_sync_time")?.value ?: "Never"

                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    timetables     = timetables,
                    dateAssignments = assignments,
                    dayDefaults    = dayDefaults,
                    lastSyncTime   = lastSync,
                    configVersion  = config.version
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
    // Date Assignment Management
    // -------------------------------------------------------------------------

    fun assignTimetableToDate(date: String, timetableName: String) {
        val adminEmail = FirebaseManager.currentUid() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                FirebaseManager.setDateAssignment(date, timetableName, adminEmail)

                // Immediately reflect in local Room so the timetable updates for user screens
                dao.clearAllOverrides()
                val updatedAssignments = FirebaseManager.fetchDateAssignments()
                dao.insertOverrides(updatedAssignments.map { (d, t) ->
                    ScheduleOverrideEntity(overrideDate = d, timetableName = t)
                })

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
                dao.clearAllOverrides()
                dao.insertOverrides(updatedAssignments.map { (d, t) ->
                    ScheduleOverrideEntity(overrideDate = d, timetableName = t)
                })
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
