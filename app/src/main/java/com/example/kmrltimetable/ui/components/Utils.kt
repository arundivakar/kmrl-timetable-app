package com.example.kmrltimetable.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getCountdownMillis(targetTimeStr: String, current: Date): Long {
    val targetFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    try {
        val target = targetFormat.parse(targetTimeStr) ?: return Long.MIN_VALUE
        val cal = Calendar.getInstance().apply { time = current }
        val tCal = Calendar.getInstance().apply { time = target }
        cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
        cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
        cal.set(Calendar.SECOND, tCal.get(Calendar.SECOND))
        cal.set(Calendar.MILLISECOND, 0)
        
        return cal.timeInMillis - current.time
    } catch(e: Exception) { return Long.MIN_VALUE }
}

/**
 * Checks if a train is still considered upcoming (including the 60-second departure grace period).
 * Status priority:
 * - currentTime < departureTime -> UPCOMING (diff > 0)
 * - departureTime <= currentTime < departureTime + 60s -> DEPARTED (diff in [-60000ms, 0ms])
 * - currentTime >= departureTime + 60s -> EXCLUDE FROM UPCOMING TRAINS (diff <= -60000ms)
 */
fun isTrainValidUpcoming(departureTimeStr: String, current: Date): Boolean {
    val diff = getCountdownMillis(departureTimeStr, current)
    return diff > -60_000L
}

fun isTrainDeparted(departureTimeStr: String, current: Date): Boolean {
    val diff = getCountdownMillis(departureTimeStr, current)
    return diff <= 0
}

fun getCountdownFormatted(targetTimeStr: String, current: Date): String {
    val diff = getCountdownMillis(targetTimeStr, current)
    if (diff <= 0) return "Departed"
    val totalSecs = (diff + 999) / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return if (mins > 0) {
        String.format(Locale.US, "%dm %02ds", mins, secs)
    } else {
        String.format(Locale.US, "%ds", secs)
    }
}

fun getCountdownFormattedMins(targetTimeStr: String, current: Date): String {
    val diff = getCountdownMillis(targetTimeStr, current)
    if (diff <= 0) return "Departed"
    val totalSecs = (diff + 999) / 1000
    val mins = totalSecs / 60
    val hours = mins / 60
    val remainingMins = mins % 60
    
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, remainingMins)
    } else if (mins > 0) {
        String.format(Locale.US, "%dm", mins)
    } else {
        String.format(Locale.US, "%ds", totalSecs)
    }
}

fun getDurationStr(dep: String, arr: String): String {
    val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    try {
        val d1 = format.parse(dep) ?: return ""
        val d2 = format.parse(arr) ?: return ""
        var diff = d2.time - d1.time
        if (diff < 0) diff += 24 * 60 * 60 * 1000 // Handle crossing midnight just in case
        val mins = diff / 60000
        return "${mins} min"
    } catch(e: Exception) { return "" }
}

fun isRevenueService(terminalDepartureTime: String, stationDepartureTime: String, isSunday: Boolean): Boolean {
    val morningStart = if (isSunday) "07:30:00" else "06:00:00"
    val eveningEnd = "23:00:00"
    
    // To be revenue, it must depart the current station AT OR AFTER morning start
    // AND it must have departed the terminal AT OR BEFORE evening end
    val afterMorningStart = stationDepartureTime >= morningStart
    val beforeEveningEnd = terminalDepartureTime <= eveningEnd

    return afterMorningStart && beforeEveningEnd
}
