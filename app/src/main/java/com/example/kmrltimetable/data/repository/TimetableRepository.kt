package com.example.kmrltimetable.data.repository

import com.example.kmrltimetable.data.local.TimetableDao
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.local.entity.StationTrainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimetableRepository(
    private val dao: TimetableDao
) {

    fun getStations(): Flow<List<StationEntity>> {
        return dao.getAllStations()
    }

    fun getEffectiveTimetableNameForDate(date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(date)
        
        val override = dao.getOverrideForDate(dateStr)
        if (override != null) {
            return override.timetableName
        }

        val cal = Calendar.getInstance().apply { time = date }
        // Calendar.DAY_OF_WEEK is 1-indexed starting Sunday. Python parser dayOfWeek is 0-indexed starting Monday.
        val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0 ... Saturday=5, Sunday=6

        return if (dayOfWeek == 6) {
            // Sunday uses Sunday default
            dao.getDefaultTimetableForDay(6)?.timetableName
                ?: dao.getDefaultTimetableForDay(0)?.timetableName
                ?: "13S010326_5TPHT_MRP1"
        } else {
            // Monday through Saturday (0..5) uses Weekday default (day 0)
            dao.getDefaultTimetableForDay(0)?.timetableName
                ?: dao.getDefaultTimetableForDay(dayOfWeek)?.timetableName
                ?: "16W070926_TPHTOFFPEAK_MRP1"
        }
    }

    suspend fun getUpcomingTrains(
        fromStationId: Int,
        toStationId: Int,
        limit: Int = 100,
        currentDate: Date = Date(),
        timeStrOverride: String? = null
    ): Pair<String, List<JourneyResult>> = withContext(Dispatchers.IO) {
        val direction = if (fromStationId < toStationId) "UP" else "DOWN"
        
        // 1. Determine Timetable to use
        val timetableName = getEffectiveTimetableNameForDate(currentDate)
        
        val timetable = dao.getTimetableByName(timetableName)
            ?: dao.getAllTimetables().maxByOrNull { it.id }
            ?: return@withContext Pair(timetableName, emptyList())
        
        // 2. Query trains
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val timeStr = timeStrOverride ?: timeFormat.format(currentDate)
        
        val results = dao.getUpcomingDepartures(
            timetableId = timetable.id,
            direction = direction,
            fromStationId = fromStationId,
            toStationId = toStationId,
            timeStr = timeStr,
            limit = limit
        )
        return@withContext Pair(timetable.name, results)
    }

    /** 
     * Get all trains calling at a given station for both directions.
     * Resolves the correct timetable for the date (respecting overrides + day defaults).
     */
    suspend fun getStationTimings(
        stationId: Int,
        currentDate: Date = Date()
    ): Triple<String, List<StationTrainResult>, List<StationTrainResult>> = withContext(Dispatchers.IO) {
        // Determine timetable
        val timetableName = getEffectiveTimetableNameForDate(currentDate)

        val timetable = dao.getTimetableByName(timetableName)
            ?: dao.getAllTimetables().maxByOrNull { it.id }
            ?: return@withContext Triple(timetableName, emptyList(), emptyList())

        val all = dao.getStationTimings(timetable.id, stationId)

        // UP = towards TPHT (station sequence increases), DOWN = towards Aluva
        val upTrains   = all.filter { it.direction == "UP" }
        val downTrains = all.filter { it.direction == "DOWN" }

        return@withContext Triple(timetableName, upTrains, downTrains)
    }

    suspend fun getAvailableTrainNumbers(currentDate: Date = Date()): List<String> = withContext(Dispatchers.IO) {
        val timetableName = getEffectiveTimetableNameForDate(currentDate)
        val timetable = dao.getTimetableByName(timetableName)
            ?: dao.getAllTimetables().maxByOrNull { it.id }
            ?: return@withContext emptyList()
        dao.getAllTrainNumbers(timetable.id)
    }

    suspend fun getTrainTrackingData(
        trainNoInput: String,
        currentDate: Date = Date(),
        timeStrOverride: String? = null
    ): TrainTrackingData? = withContext(Dispatchers.IO) {
        val cleanNo = trainNoInput.trim().trimStart('0')
        if (cleanNo.isEmpty()) return@withContext null
        val paddedNo = cleanNo.padStart(2, '0')

        val timetableName = getEffectiveTimetableNameForDate(currentDate)
        val timetable = dao.getTimetableByName(timetableName)
            ?: dao.getAllTimetables().maxByOrNull { it.id }
            ?: return@withContext null

        val tripEntities = dao.getTripsForTrain(timetable.id, cleanNo, paddedNo)
        if (tripEntities.isEmpty()) return@withContext null

        val tripsWithStops = tripEntities.map { trip ->
            val stops = dao.getStopsForTrip(trip.id, trip.direction)
            TripWithStops(trip, stops)
        }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val currentTimeStr = timeStrOverride ?: timeFormat.format(currentDate)
        val currentSec = parseSeconds(currentTimeStr)

        // Find active trip or next trip
        var activeIndex = -1
        for (i in tripsWithStops.indices) {
            val trip = tripsWithStops[i].trip
            val sSec = parseSeconds(trip.startTime)
            val eSec = parseSeconds(trip.endTime)
            if (currentSec in sSec..eSec) {
                activeIndex = i
                break
            }
        }

        // If no trip is currently in-transit, find next upcoming trip
        if (activeIndex == -1) {
            for (i in tripsWithStops.indices) {
                val sSec = parseSeconds(tripsWithStops[i].trip.startTime)
                if (currentSec < sSec) {
                    activeIndex = i
                    break
                }
            }
        }

        // If all trips for the day have finished, point to the last trip
        if (activeIndex == -1) {
            activeIndex = tripsWithStops.size - 1
        }

        val selectedTrip = tripsWithStops[activeIndex]
        val liveLocation = calculateLiveLocation(selectedTrip, currentTimeStr)

        TrainTrackingData(
            trainNo = paddedNo,
            timetableName = timetable.name,
            trips = tripsWithStops,
            activeTripIndex = activeIndex,
            liveLocation = liveLocation
        )
    }

    fun calculateLiveLocation(tripWithStops: TripWithStops, currentTimeStr: String): TrainLiveLocation {
        val stops = tripWithStops.stops
        val currentSec = parseSeconds(currentTimeStr)
        val timedStops = stops.mapIndexed { idx, st -> Pair(idx, st) }.filter { it.second.departureTime != null }

        if (timedStops.isEmpty()) {
            return TrainLiveLocation(
                statusText = "Schedule unavailable",
                statusType = LiveStatusType.NOT_STARTED,
                detailText = "No departure times found for this trip",
                lastPassedIndex = 0,
                nextUpcomingIndex = 0,
                progressFraction = 0f
            )
        }

        val firstStop = timedStops.first()
        val lastStop = timedStops.last()
        val startSec = parseSeconds(firstStop.second.departureTime)
        val endSec = parseSeconds(lastStop.second.departureTime)

        if (currentSec < startSec) {
            val diffMins = ((startSec - currentSec) / 60).coerceAtLeast(1)
            return TrainLiveLocation(
                statusText = "Waiting at ${firstStop.second.stationName}",
                statusType = LiveStatusType.WAITING_TERMINAL,
                detailText = "Departs in ~$diffMins min (${firstStop.second.departureTime})",
                lastPassedIndex = -1,
                nextUpcomingIndex = firstStop.first,
                progressFraction = 0f
            )
        }

        if (currentSec > endSec) {
            return TrainLiveLocation(
                statusText = "Trip completed at ${lastStop.second.stationName}",
                statusType = LiveStatusType.COMPLETED,
                detailText = "Arrived at ${lastStop.second.departureTime}",
                lastPassedIndex = lastStop.first,
                nextUpcomingIndex = -1,
                progressFraction = 1f
            )
        }

        for (i in 0 until timedStops.size - 1) {
            val s1 = timedStops[i]
            val s2 = timedStops[i + 1]
            val t1 = parseSeconds(s1.second.departureTime)
            val t2 = parseSeconds(s2.second.departureTime)

            if (currentSec in t1..t2) {
                val span = (t2 - t1).coerceAtLeast(1)
                val progress = (currentSec - t1).toFloat() / span.toFloat()

                return if (currentSec - t1 <= 25) {
                    TrainLiveLocation(
                        statusText = "At ${s1.second.stationName}",
                        statusType = LiveStatusType.AT_STATION,
                        detailText = "Departing towards ${s2.second.stationName}",
                        lastPassedIndex = s1.first,
                        nextUpcomingIndex = s2.first,
                        progressFraction = 0f
                    )
                } else if (t2 - currentSec <= 20) {
                    TrainLiveLocation(
                        statusText = "Arriving at ${s2.second.stationName}",
                        statusType = LiveStatusType.IN_TRANSIT,
                        detailText = "Next stop in < 1 min (${s2.second.departureTime})",
                        lastPassedIndex = s1.first,
                        nextUpcomingIndex = s2.first,
                        progressFraction = progress
                    )
                } else {
                    TrainLiveLocation(
                        statusText = "Between ${s1.second.stationName} and ${s2.second.stationName}",
                        statusType = LiveStatusType.IN_TRANSIT,
                        detailText = "En route to ${s2.second.stationName} (${s2.second.departureTime})",
                        lastPassedIndex = s1.first,
                        nextUpcomingIndex = s2.first,
                        progressFraction = progress
                    )
                }
            }
        }

        return TrainLiveLocation(
            statusText = "In transit",
            statusType = LiveStatusType.IN_TRANSIT,
            detailText = "Following schedule",
            lastPassedIndex = 0,
            nextUpcomingIndex = 1,
            progressFraction = 0.5f
        )
    }

    private fun parseSeconds(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return 0
        val parts = timeStr.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }
}

data class TrainLiveLocation(
    val statusText: String,
    val statusType: LiveStatusType,
    val detailText: String,
    val lastPassedIndex: Int,
    val nextUpcomingIndex: Int,
    val progressFraction: Float // 0f..1f
)

enum class LiveStatusType {
    IN_TRANSIT,
    AT_STATION,
    WAITING_TERMINAL,
    NOT_STARTED,
    COMPLETED
}

data class TripWithStops(
    val trip: com.example.kmrltimetable.data.local.entity.TrainTripInfo,
    val stops: List<com.example.kmrltimetable.data.local.entity.TripStopDetail>
)

data class TrainTrackingData(
    val trainNo: String,
    val timetableName: String,
    val trips: List<TripWithStops>,
    val activeTripIndex: Int,
    val liveLocation: TrainLiveLocation
)
