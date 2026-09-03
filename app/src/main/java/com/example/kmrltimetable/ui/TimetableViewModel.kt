package com.example.kmrltimetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.repository.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

data class TimetableUiState(
    val fromStation: StationEntity? = null,
    val toStation: StationEntity? = null,
    val allTrainsToday: List<JourneyResult> = emptyList(),
    val activeTimetableName: String = "",
    val allTrainsTomorrow: List<JourneyResult> = emptyList(),
    val tomorrowTimetableName: String = "",
    val customSelectedDate: Date? = null,
    val customDateTrains: List<JourneyResult> = emptyList(),
    val customDateTimetableName: String = "",
    val isLoading: Boolean = false
)

class TimetableViewModel(
    private val repository: TimetableRepository
) : ViewModel() {

    val stations: StateFlow<List<StationEntity>> = repository.getStations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentTime: StateFlow<Date> = flow {
        while (true) {
            emit(Date())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Date())

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState

    fun setFromStation(station: StationEntity) {
        _uiState.update { it.copy(fromStation = station) }
        fetchUpcomingTrains()
    }

    fun setToStation(station: StationEntity) {
        _uiState.update { it.copy(toStation = station) }
        fetchUpcomingTrains()
    }

    fun swapStations() {
        val currentFrom = _uiState.value.fromStation
        val currentTo = _uiState.value.toStation
        _uiState.update { 
            it.copy(fromStation = currentTo, toStation = currentFrom)
        }
        fetchUpcomingTrains()
    }

    fun fetchTrainsForCustomDate(date: Date) {
        val from = _uiState.value.fromStation ?: return
        val to = _uiState.value.toStation ?: return
        if (from.id == to.id) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, customSelectedDate = date) }
            val result = repository.getUpcomingTrains(
                fromStationId = from.id,
                toStationId = to.id,
                currentDate = date,
                timeStrOverride = "00:00:00",
                limit = 1000
            )
            _uiState.update {
                it.copy(
                    customDateTrains = result.second,
                    customDateTimetableName = result.first,
                    isLoading = false
                )
            }
        }
    }

    fun clearCustomDate() {
        _uiState.update {
            it.copy(
                customSelectedDate = null,
                customDateTrains = emptyList(),
                customDateTimetableName = ""
            )
        }
    }

    private fun fetchUpcomingTrains() {
        val from = _uiState.value.fromStation ?: return
        val to = _uiState.value.toStation ?: return
        
        if (from.id == to.id) {
            _uiState.update { it.copy(allTrainsToday = emptyList(), allTrainsTomorrow = emptyList(), customDateTrains = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Fetch Today
            val todayResult = repository.getUpcomingTrains(
                fromStationId = from.id,
                toStationId = to.id,
                currentDate = Date(),
                timeStrOverride = "00:00:00",
                limit = 1000
            )

            // Fetch Tomorrow
            val tomorrowDate = Date(System.currentTimeMillis() + 86400000)
            val tomorrowResult = repository.getUpcomingTrains(
                fromStationId = from.id,
                toStationId = to.id,
                currentDate = tomorrowDate,
                timeStrOverride = "00:00:00",
                limit = 1000
            )

            val customDate = _uiState.value.customSelectedDate
            val customResult = if (customDate != null) {
                repository.getUpcomingTrains(
                    fromStationId = from.id,
                    toStationId = to.id,
                    currentDate = customDate,
                    timeStrOverride = "00:00:00",
                    limit = 1000
                )
            } else null

            _uiState.update { 
                it.copy(
                    allTrainsToday = todayResult.second, 
                    activeTimetableName = todayResult.first,
                    allTrainsTomorrow = tomorrowResult.second,
                    tomorrowTimetableName = tomorrowResult.first,
                    customDateTrains = customResult?.second ?: it.customDateTrains,
                    customDateTimetableName = customResult?.first ?: it.customDateTimetableName,
                    isLoading = false
                ) 
            }
        }
    }
}
