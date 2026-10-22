package com.example.kmrltimetable.data.model

import org.json.JSONObject

/**
 * Represents a scheduled station passenger assistance alarm.
 * Used by station controllers, platform security, and passenger assistance teams
 * to trigger an alert before a train arrives at a specific station (e.g. 5 minutes before VAKK).
 */
data class StationAlarm(
    val id: String,
    val trainNo: String,
    val direction: String,        // "UP" (Aluva -> Tripunithura) or "DOWN" (Tripunithura -> Aluva)
    val stationCode: String,      // e.g. "VAKK", "KLMT"
    val stationName: String,      // e.g. "Vadakkekotta", "Kalamassery"
    val tripId: Int,
    val scheduledTime: String,    // e.g. "09:42:00"
    val minutesBefore: Int,       // e.g. 5
    val targetEpochMs: Long,      // exact timestamp in milliseconds when alarm rings
    val notes: String = "",       // e.g. "Wheelchair Assistance on Platform 1"
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isTriggered: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("trainNo", trainNo)
            put("direction", direction)
            put("stationCode", stationCode)
            put("stationName", stationName)
            put("tripId", tripId)
            put("scheduledTime", scheduledTime)
            put("minutesBefore", minutesBefore)
            put("targetEpochMs", targetEpochMs)
            put("notes", notes)
            put("createdAtEpochMs", createdAtEpochMs)
            put("isTriggered", isTriggered)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): StationAlarm {
            return StationAlarm(
                id = json.getString("id"),
                trainNo = json.getString("trainNo"),
                direction = json.optString("direction", "UP"),
                stationCode = json.getString("stationCode"),
                stationName = json.getString("stationName"),
                tripId = json.optInt("tripId", 0),
                scheduledTime = json.getString("scheduledTime"),
                minutesBefore = json.optInt("minutesBefore", 5),
                targetEpochMs = json.getLong("targetEpochMs"),
                notes = json.optString("notes", ""),
                createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis()),
                isTriggered = json.optBoolean("isTriggered", false)
            )
        }
    }
}
