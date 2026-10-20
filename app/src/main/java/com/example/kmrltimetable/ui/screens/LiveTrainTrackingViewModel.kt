package com.example.kmrltimetable.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.repository.LiveStatusType
import com.example.kmrltimetable.data.repository.TimetableRepository
import com.example.kmrltimetable.data.repository.TrainLiveLocation
import com.example.kmrltimetable.data.repository.TrainTrackingData
import com.example.kmrltimetable.data.repository.TripWithStops
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveTrainUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val trainNo: String = "",
    val timetableName: String = "",
    val trips: List<TripWithStops> = emptyList(),
    val selectedTripIndex: Int = 0,
    val liveLocation: TrainLiveLocation? = null,
    val isViewingLiveTrip: Boolean = true,
    val availableTrainNumbers: List<String> = emptyList()
) {
    val currentTrip: TripWithStops?
        get() = trips.getOrNull(selectedTripIndex)
}

class LiveTrainTrackingViewModel(
    private val repository: TimetableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTrainUiState())
    val uiState: StateFlow<LiveTrainUiState> = _uiState.asStateFlow()

    private var currentTrainNo: String = ""

    init {
        // Load available train numbers
        viewModelScope.launch {
            val trains = repository.getAvailableTrainNumbers()
            _uiState.value = _uiState.value.copy(availableTrainNumbers = trains)
        }

        // Ticking loop to refresh real-time position every 5 seconds
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (currentTrainNo.isNotEmpty() && !_uiState.value.isLoading) {
                    refreshPosition()
                }
            }
        }
    }

    fun startTracking(trainNo: String) {
        currentTrainNo = trainNo
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, trainNo = trainNo)
            val trackingData = repository.getTrainTrackingData(trainNo)
            if (trackingData == null || trackingData.trips.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Train $trainNo was not found in today's active schedule."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null,
                    trainNo = trackingData.trainNo,
                    timetableName = trackingData.timetableName,
                    trips = trackingData.trips,
                    selectedTripIndex = trackingData.activeTripIndex,
                    liveLocation = trackingData.liveLocation,
                    isViewingLiveTrip = true
                )
            }
        }
    }

    fun selectTrip(index: Int) {
        if (index !in _uiState.value.trips.indices) return
        val trip = _uiState.value.trips[index]
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val curTimeStr = timeFormat.format(Date())
        val loc = repository.calculateLiveLocation(trip, curTimeStr)

        _uiState.value = _uiState.value.copy(
            selectedTripIndex = index,
            liveLocation = loc
        )
    }

    private fun refreshPosition() {
        val state = _uiState.value
        if (state.trips.isEmpty() || state.selectedTripIndex !in state.trips.indices) return
        val selectedTrip = state.trips[state.selectedTripIndex]
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val curTimeStr = timeFormat.format(Date())
        val updatedLoc = repository.calculateLiveLocation(selectedTrip, curTimeStr)
        _uiState.value = _uiState.value.copy(liveLocation = updatedLoc)
    }
}
