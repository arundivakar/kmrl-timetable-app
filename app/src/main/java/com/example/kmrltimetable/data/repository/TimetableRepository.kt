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
}
