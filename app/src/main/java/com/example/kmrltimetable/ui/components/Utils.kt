package com.example.kmrltimetable.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getCountdownMillis(targetTimeStr: String, current: Date): Long {
    val targetFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    try {
        val target = targetFormat.parse(targetTimeStr) ?: return -1
        val cal = Calendar.getInstance().apply { time = current }
        val tCal = Calendar.getInstance().apply { time = target }
        cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
        cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
        cal.set(Calendar.SECOND, tCal.get(Calendar.SECOND))
        
        return cal.timeInMillis - current.time
    } catch(e: Exception) { return -1 }
}

fun getCountdownFormatted(targetTimeStr: String, current: Date): String {
    val diff = getCountdownMillis(targetTimeStr, current)
    if (diff <= 0) return "Departed"
    val mins = diff / 60000
    val secs = (diff % 60000) / 1000
    return if (mins > 0) String.format(Locale.US, "%dm %02ds", mins, secs) else String.format(Locale.US, "%02ds", secs)
}

fun getCountdownFormattedMins(targetTimeStr: String, current: Date): String {
    val diff = getCountdownMillis(targetTimeStr, current)
    if (diff <= 0) return "Departed"
    val mins = diff / 60000
    val hours = mins / 60
    val remainingMins = mins % 60
    
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, remainingMins)
    } else {
        String.format(Locale.US, "%dm", mins)
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
