package com.example.kmrltimetable.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmrltimetable.data.local.entity.TripStopDetail
import com.example.kmrltimetable.data.model.StationAlarm
import com.example.kmrltimetable.data.repository.LiveStatusType
import com.example.kmrltimetable.data.repository.TimetableRepository
import com.example.kmrltimetable.data.repository.TrainLiveLocation
import com.example.kmrltimetable.data.repository.TrainTrackingData
import com.example.kmrltimetable.data.repository.TripWithStops
import com.example.kmrltimetable.util.StationAlarmManager
import com.example.kmrltimetable.util.StationAlarmReceiver
import kotlinx.coroutines.Dispatchers
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

    // Station Assistance Alarms state
    val activeAlarms: StateFlow<List<StationAlarm>> = StationAlarmManager.activeAlarms
    val triggeredAlarm: StateFlow<StationAlarm?> = StationAlarmManager.triggeredAlarm

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

                // Background lazy pre-caching of remaining trips so switching tabs is instant
                prefetchOtherTrips()
            }
        }
    }

    private fun prefetchOtherTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTrips = _uiState.value.trips
            for (i in currentTrips.indices) {
                if (!isActive) break
                if (currentTrips[i].stops.isEmpty()) {
                    val s = repository.getStopsForTrip(currentTrips[i].trip.id, currentTrips[i].trip.direction)
                    val updated = _uiState.value.trips.toMutableList()
                    if (i in updated.indices && updated[i].stops.isEmpty()) {
                        updated[i] = TripWithStops(updated[i].trip, s)
                        _uiState.value = _uiState.value.copy(trips = updated)
                    }
                }
            }
        }
    }

    fun selectTrip(index: Int) {
        val state = _uiState.value
        if (index !in state.trips.indices) return
        val currentTripItem = state.trips[index]

        viewModelScope.launch {
            val tripWithStops = if (currentTripItem.stops.isEmpty()) {
                val stops = repository.getStopsForTrip(currentTripItem.trip.id, currentTripItem.trip.direction)
                val updated = TripWithStops(currentTripItem.trip, stops)
                val updatedList = state.trips.toMutableList()
                updatedList[index] = updated
                _uiState.value = _uiState.value.copy(trips = updatedList)
                updated
            } else {
                currentTripItem
            }

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
            val curTimeStr = timeFormat.format(Date())
            val loc = repository.calculateLiveLocation(tripWithStops, curTimeStr)

            _uiState.value = _uiState.value.copy(
                selectedTripIndex = index,
                liveLocation = loc
            )
        }
    }

    private fun refreshPosition() {
        val state = _uiState.value
        if (state.trips.isEmpty() || state.selectedTripIndex !in state.trips.indices) return
        val selectedTrip = state.trips[state.selectedTripIndex]
        if (selectedTrip.stops.isEmpty()) return

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val curTimeStr = timeFormat.format(Date())
        val updatedLoc = repository.calculateLiveLocation(selectedTrip, curTimeStr)
        _uiState.value = _uiState.value.copy(liveLocation = updatedLoc)
    }

    // ────────────────────── Station Assistance Alarm Operations ──────────────────────

    fun setStationAlarm(
        context: Context,
        stop: TripStopDetail,
        minutesBefore: Int,
        notes: String
    ): Boolean {
        val trip = _uiState.value.currentTrip?.trip ?: return false
        val scheduledTime = stop.departureTime ?: return false
        val targetEpoch = StationAlarmManager.calculateTargetEpochMs(scheduledTime, minutesBefore)

        val cleanTrainNo = _uiState.value.trainNo.ifBlank { trip.trainNo }.padStart(2, '0')
        val alarmId = "alarm_${cleanTrainNo}_${stop.stationCode}_${trip.id}"

        val alarm = StationAlarm(
            id = alarmId,
            trainNo = cleanTrainNo,
            direction = trip.direction,
            stationCode = stop.stationCode,
            stationName = stop.stationName,
            tripId = trip.id,
            scheduledTime = scheduledTime,
            minutesBefore = minutesBefore,
            targetEpochMs = targetEpoch,
            notes = notes.ifBlank { "Passenger assistance requested" }
        )

        return StationAlarmManager.scheduleAlarm(context, alarm)
    }

    fun cancelStationAlarm(context: Context, alarmId: String) {
        StationAlarmManager.cancelAlarm(context, alarmId)
    }

    fun dismissTriggeredAlarm(context: Context) {
        val triggered = StationAlarmManager.triggeredAlarm.value
        if (triggered != null) {
            StationAlarmReceiver.dismissAlarm(context, triggered.id)
        }
    }

    fun getAlarmForStation(stationCode: String): StationAlarm? {
        val trainNo = _uiState.value.trainNo
        return StationAlarmManager.getAlarmForStation(trainNo, stationCode)
    }
}
