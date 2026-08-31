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

    suspend fun getUpcomingTrains(
        fromStationId: Int,
        toStationId: Int,
        limit: Int = 100,
        currentDate: Date = Date(),
        timeStrOverride: String? = null
    ): Pair<String, List<JourneyResult>> = withContext(Dispatchers.IO) {
        val direction = if (fromStationId < toStationId) "UP" else "DOWN"
        
        // 1. Determine Timetable to use
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(currentDate)
        
        val override = dao.getOverrideForDate(dateStr)
        val timetableName = if (override != null) {
            override.timetableName
        } else {
            val cal = Calendar.getInstance().apply { time = currentDate }
            // Calendar.DAY_OF_WEEK is 1-indexed starting Sunday. Python parser dayOfWeek is 0-indexed starting Monday.
            val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0, Sunday=6
            dao.getDefaultTimetableForDay(dayOfWeek)?.timetableName 
                ?: return@withContext Pair("Unknown", emptyList()) // Fallback if no default found
        }
        
        val timetable = dao.getTimetableByName(timetableName) ?: return@withContext Pair(timetableName, emptyList())
        
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
        return@withContext Pair(timetableName, results)
    }

    /** 
     * Get all trains calling at a given station for both directions.
     * Resolves the correct timetable for the date (respecting overrides + day defaults).
     */
    suspend fun getStationTimings(
        stationId: Int,
        currentDate: Date = Date()
    ): Triple<String, List<StationTrainResult>, List<StationTrainResult>> = withContext(Dispatchers.IO) {
        // Determine timetable (identical logic to getUpcomingTrains)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(currentDate)

        val override = dao.getOverrideForDate(dateStr)
        val timetableName = if (override != null) {
            override.timetableName
        } else {
            val cal = Calendar.getInstance().apply { time = currentDate }
            val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            dao.getDefaultTimetableForDay(dayOfWeek)?.timetableName
                ?: return@withContext Triple("Unknown", emptyList(), emptyList())
        }

        val timetable = dao.getTimetableByName(timetableName)
            ?: return@withContext Triple(timetableName, emptyList(), emptyList())

        val all = dao.getStationTimings(timetable.id, stationId)

        // UP = towards TPHT (station sequence increases), DOWN = towards Aluva
        val upTrains   = all.filter { it.direction == "UP" }
        val downTrains = all.filter { it.direction == "DOWN" }

        return@withContext Triple(timetableName, upTrains, downTrains)
    }
}
