package com.example.kmrltimetable.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.data.local.entity.StationTrainResult
import com.example.kmrltimetable.data.repository.TimetableRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class TrainFilter { ALL, UPCOMING }
enum class DirectionFilter { BOTH, TO_ALUVA, TO_TPHT }

// ── UI State ──────────────────────────────────────────────────────────────────

data class StationTimingsUiState(
    val selectedStation: StationEntity? = null,
    val isTomorrow: Boolean = false,
    val trainFilter: TrainFilter = TrainFilter.UPCOMING,
    val dirFilter: DirectionFilter = DirectionFilter.BOTH,
    val timetableName: String = "",
    val toAluvaTrains: List<StationTrainResult> = emptyList(),  // direction DOWN = towards Aluva
    val toTphtTrains: List<StationTrainResult> = emptyList(),   // direction UP = towards TPHT
    val isLoading: Boolean = false,
    val error: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StationTimingsViewModel(
    private val repository: TimetableRepository
) : ViewModel() {

    /** All metro stations — reused from repository */
    val stations: StateFlow<List<StationEntity>> = repository.getStations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 1-minute clock for live countdown updates */
    val currentTime: StateFlow<Date> = flow {
        while (true) {
            emit(Date())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Date())

    private val _uiState = MutableStateFlow(StationTimingsUiState())
    val uiState: StateFlow<StationTimingsUiState> = _uiState

    fun selectStation(station: StationEntity) {
        _uiState.update { it.copy(selectedStation = station) }
        fetchTimings()
    }

    fun setTomorrow(isTomorrow: Boolean) {
        _uiState.update { it.copy(isTomorrow = isTomorrow) }
        fetchTimings()
    }

    fun setFilter(filter: TrainFilter) {
        _uiState.update { it.copy(trainFilter = filter) }
    }

    fun setDirectionFilter(dir: DirectionFilter) {
        _uiState.update { it.copy(dirFilter = dir) }
    }

    private fun fetchTimings() {
        val station = _uiState.value.selectedStation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val date = if (_uiState.value.isTomorrow) {
                    Date(System.currentTimeMillis() + 86_400_000L)
                } else {
                    Date()
                }
                val (timetableName, upTrains, downTrains) = repository.getStationTimings(
                    stationId = station.id,
                    currentDate = date
                )
                _uiState.update {
                    it.copy(
                        timetableName = timetableName,
                        toTphtTrains  = upTrains,     // UP = towards TPHT
                        toAluvaTrains = downTrains,   // DOWN = towards Aluva
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
