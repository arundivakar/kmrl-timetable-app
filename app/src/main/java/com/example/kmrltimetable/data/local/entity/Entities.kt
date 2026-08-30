package com.example.kmrltimetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

data class JourneyResult(
    @ColumnInfo(name = "train_no") val trainNo: String,
    @ColumnInfo(name = "departure_time") val departureTime: String,
    @ColumnInfo(name = "arrival_time") val arrivalTime: String
)

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey
    val id: Int,
    val code: String,
    val name: String,
    val sequence: Int
)

@Entity(tableName = "timetables")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    @ColumnInfo(name = "train_count")
    val trainCount: Int?,
    val notes: String?,
    val bundled: Int = 1
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "timetable_id")
    val timetableId: Int,
    @ColumnInfo(name = "train_no")
    val trainNo: String,
    val direction: String,
    @ColumnInfo(name = "col_index")
    val colIndex: Int
)

@Entity(tableName = "stop_times")
data class StopTimeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "trip_id", index = true)
    val tripId: Int,
    @ColumnInfo(name = "station_id", index = true)
    val stationId: Int,
    @ColumnInfo(name = "departure_time")
    val departureTime: String?
)

@Entity(tableName = "schedule_overrides")
data class ScheduleOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "override_date")
    val overrideDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "timetable_name")
    val timetableName: String
)

@Entity(tableName = "day_defaults")
data class DayDefaultEntity(
    @PrimaryKey
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int, // 0 = Monday, 6 = Sunday (from Python)
    @ColumnInfo(name = "timetable_name")
    val timetableName: String
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String?
)
