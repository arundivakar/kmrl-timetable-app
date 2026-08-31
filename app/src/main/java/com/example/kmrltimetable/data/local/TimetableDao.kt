package com.example.kmrltimetable.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.kmrltimetable.data.local.entity.DayDefaultEntity
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.data.local.entity.StopTimeEntity
import com.example.kmrltimetable.data.local.entity.SyncMetadataEntity
import com.example.kmrltimetable.data.local.entity.TimetableEntity
import com.example.kmrltimetable.data.local.entity.TripEntity
import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.local.entity.StationTrainResult
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    
    // ------ Stations ------

    @Query("SELECT * FROM stations ORDER BY sequence ASC")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :stationId")
    fun getStation(stationId: Int): StationEntity?

    // ------ Timetables ------

    @Query("SELECT * FROM timetables")
    fun getAllTimetables(): List<TimetableEntity>

    @Query("SELECT * FROM timetables WHERE name = :name LIMIT 1")
    fun getTimetableByName(name: String): TimetableEntity?

    // ------ Day defaults ------

    @Query("SELECT * FROM day_defaults")
    fun getDayDefaults(): List<DayDefaultEntity>

    @Query("SELECT * FROM day_defaults WHERE day_of_week = :dayOfWeek LIMIT 1")
    fun getDefaultTimetableForDay(dayOfWeek: Int): DayDefaultEntity?

    @Query("UPDATE day_defaults SET timetable_name = :timetableName WHERE day_of_week = :dayOfWeek")
    fun updateDayDefault(dayOfWeek: Int, timetableName: String)

    // ------ Schedule overrides (date-specific assignments) ------

    @Query("SELECT * FROM schedule_overrides")
    fun getScheduleOverrides(): List<ScheduleOverrideEntity>

    @Query("SELECT * FROM schedule_overrides WHERE override_date = :dateStr LIMIT 1")
    fun getOverrideForDate(dateStr: String): ScheduleOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOverrides(overrides: List<ScheduleOverrideEntity>)

    @Query("DELETE FROM schedule_overrides")
    fun clearAllOverrides()

    // ------ Train query ------

    @Query("""
        SELECT 
            t.train_no as train_no,
            st_from.departure_time as departure_time,
            st_to.departure_time as arrival_time
        FROM stop_times st_from
        INNER JOIN trips t ON st_from.trip_id = t.id
        INNER JOIN stop_times st_to ON t.id = st_to.trip_id
        WHERE t.timetable_id = :timetableId 
          AND t.direction = :direction
          AND st_from.station_id = :fromStationId
          AND st_to.station_id = :toStationId
          AND st_from.departure_time IS NOT NULL
          AND st_to.departure_time IS NOT NULL
          AND st_from.departure_time >= :timeStr
        ORDER BY st_from.departure_time ASC
        LIMIT :limit
    """)
    fun getUpcomingDepartures(
        timetableId: Int, 
        direction: String, 
        fromStationId: Int,
        toStationId: Int,
        timeStr: String,
        limit: Int = 50
    ): List<JourneyResult>

    @Query("""
        SELECT
            t.train_no as train_no,
            t.direction as direction,
            st.departure_time as departure_time
        FROM stop_times st
        INNER JOIN trips t ON st.trip_id = t.id
        WHERE t.timetable_id = :timetableId
          AND st.station_id = :stationId
          AND st.departure_time IS NOT NULL
        ORDER BY st.departure_time ASC
    """)
    fun getStationTimings(timetableId: Int, stationId: Int): List<StationTrainResult>

    @Query("""
        SELECT count(*) FROM trips WHERE timetable_id = :timetableId
    """)
    fun getTripCountForTimetable(timetableId: Int): Int

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun getTrip(tripId: Int): TripEntity?

    // ------ Sync metadata ------

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key LIMIT 1")
    fun getSyncMetadata(key: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata")
    fun getAllSyncMetadata(): List<SyncMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSyncMetadata(metadata: SyncMetadataEntity)
}

