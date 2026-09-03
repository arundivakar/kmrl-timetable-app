package com.example.kmrltimetable.ui

import com.example.kmrltimetable.data.local.entity.JourneyResult
import com.example.kmrltimetable.data.local.entity.StationTrainResult
import com.example.kmrltimetable.ui.components.getCountdownFormatted
import com.example.kmrltimetable.ui.components.getCountdownFormattedMins
import com.example.kmrltimetable.ui.components.getCountdownMillis
import com.example.kmrltimetable.ui.components.isTrainDeparted
import com.example.kmrltimetable.ui.components.isTrainValidUpcoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrainGracePeriodTest {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun parseDateTime(timeStr: String): Date {
        return timeFormat.parse("$todayPrefix $timeStr")!!
    }

    @Test
    fun testEdgeCase1_trainDepartsIn30Seconds() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:38:25") // exactly 30s before departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(30_000L, diff)
        assertTrue(isTrainValidUpcoming(departureTime, currentTime))
        assertFalse(isTrainDeparted(departureTime, currentTime))
        assertEquals("30s", getCountdownFormatted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase2_trainDepartsIn1Second() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:38:54") // 1s before departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(1_000L, diff)
        assertTrue(isTrainValidUpcoming(departureTime, currentTime))
        assertFalse(isTrainDeparted(departureTime, currentTime))
        assertEquals("1s", getCountdownFormatted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase3_departureTimeReached() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:38:55") // exact departure time

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(0L, diff)
        assertTrue(isTrainValidUpcoming(departureTime, currentTime))
        assertTrue(isTrainDeparted(departureTime, currentTime))
        assertEquals("Departed", getCountdownFormatted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase4_30SecondsAfterDeparture() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:39:25") // 30s after departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(-30_000L, diff)
        assertTrue("Train should remain valid upcoming during 60s grace period", isTrainValidUpcoming(departureTime, currentTime))
        assertTrue(isTrainDeparted(departureTime, currentTime))
        assertEquals("Departed", getCountdownFormatted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase5_59SecondsAfterDeparture() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:39:54") // 59s after departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(-59_000L, diff)
        assertTrue("Train should still be visible at 59s after departure", isTrainValidUpcoming(departureTime, currentTime))
        assertTrue(isTrainDeparted(departureTime, currentTime))
        assertEquals("Departed", getCountdownFormatted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase6_exactly60SecondsAfterDeparture() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:39:55") // exactly 60s after departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(-60_000L, diff)
        assertFalse("Train should be excluded at exactly 60s after departure", isTrainValidUpcoming(departureTime, currentTime))
        assertTrue(isTrainDeparted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase7_2MinutesAfterDeparture() {
        val departureTime = "15:38:55"
        val currentTime = parseDateTime("15:40:55") // 2 mins after departure

        val diff = getCountdownMillis(departureTime, currentTime)
        assertEquals(-120_000L, diff)
        assertFalse("Train should be excluded 2 mins after departure", isTrainValidUpcoming(departureTime, currentTime))
        assertTrue(isTrainDeparted(departureTime, currentTime))
    }

    @Test
    fun testEdgeCase8and9_lastTrainOfTheDayGracePeriodAndPromotion() {
        val lastTrainDeparture = "23:00:00"
        val allTrains = listOf(
            JourneyResult(trainNo = "11", departureTime = "15:38:55", arrivalTime = "15:51:23", terminalDepartureTime = "15:38:55"),
            JourneyResult(trainNo = "12", departureTime = "15:48:55", arrivalTime = "16:01:23", terminalDepartureTime = "15:48:55"),
            JourneyResult(trainNo = "25", departureTime = lastTrainDeparture, arrivalTime = "23:12:30", terminalDepartureTime = lastTrainDeparture)
        )

        // 1. At 23:00:00: Last train departs -> still visible as valid upcoming train
        val timeAtDeparture = parseDateTime("23:00:00")
        val validAtDep = allTrains.filter { isTrainValidUpcoming(it.departureTime, timeAtDeparture) }
        assertEquals(1, validAtDep.size)
        assertEquals("25", validAtDep.first().trainNo)
        assertTrue(isTrainDeparted(validAtDep.first().departureTime, timeAtDeparture))

        // 2. At 23:00:30 (30s after departure): still visible as DEPARTED
        val timeAt30s = parseDateTime("23:00:30")
        val validAt30s = allTrains.filter { isTrainValidUpcoming(it.departureTime, timeAt30s) }
        assertEquals(1, validAt30s.size)
        assertEquals("25", validAt30s.first().trainNo)
        assertTrue(isTrainDeparted(validAt30s.first().departureTime, timeAt30s))

        // 3. At 23:00:59 (59s after departure): still visible as DEPARTED
        val timeAt59s = parseDateTime("23:00:59")
        val validAt59s = allTrains.filter { isTrainValidUpcoming(it.departureTime, timeAt59s) }
        assertEquals(1, validAt59s.size)
        assertEquals("25", validAt59s.first().trainNo)

        // 4. At 23:01:00 (60s after departure): removed, empty upcoming list
        val timeAt60s = parseDateTime("23:01:00")
        val validAt60s = allTrains.filter { isTrainValidUpcoming(it.departureTime, timeAt60s) }
        assertTrue("After 60-second grace period, upcoming train list should be empty", validAt60s.isEmpty())
    }

    @Test
    fun testTrainPromotion_nextTrainReplacedAfter60Seconds() {
        val train1 = JourneyResult(trainNo = "11", departureTime = "15:38:55", arrivalTime = "15:51:23", terminalDepartureTime = "15:38:55")
        val train2 = JourneyResult(trainNo = "12", departureTime = "15:48:55", arrivalTime = "16:01:23", terminalDepartureTime = "15:48:55")
        val allTrains = listOf(train1, train2)

        // At 15:38:00 (before Train 11 departs): Next train is Train 11 (UPCOMING)
        val t0 = parseDateTime("15:38:00")
        val list0 = allTrains.filter { isTrainValidUpcoming(it.departureTime, t0) }
        assertEquals(2, list0.size)
        assertEquals("11", list0.first().trainNo)
        assertFalse(isTrainDeparted(list0.first().departureTime, t0))

        // At 15:39:10 (during Train 11 grace period): Next train is STILL Train 11 (DEPARTED)
        val t1 = parseDateTime("15:39:10")
        val list1 = allTrains.filter { isTrainValidUpcoming(it.departureTime, t1) }
        assertEquals(2, list1.size)
        assertEquals("11", list1.first().trainNo)
        assertTrue(isTrainDeparted(list1.first().departureTime, t1))

        // At 15:39:55 (60s after Train 11 departure): Train 11 removed, Train 12 promoted to NEXT TRAIN
        val t2 = parseDateTime("15:39:55")
        val list2 = allTrains.filter { isTrainValidUpcoming(it.departureTime, t2) }
        assertEquals(1, list2.size)
        assertEquals("12", list2.first().trainNo)
        assertFalse(isTrainDeparted(list2.first().departureTime, t2))
    }

    @Test
    fun testNoNegativeCountdowns() {
        val departureTime = "15:38:55"
        val tPast = parseDateTime("15:39:30")
        val formatted = getCountdownFormatted(departureTime, tPast)
        val formattedMins = getCountdownFormattedMins(departureTime, tPast)

        assertFalse("Must not contain negative countdown", formatted.contains("-"))
        assertFalse("Must not contain DUE", formatted.contains("DUE", ignoreCase = true))
        assertFalse("Must not contain negative countdown in mins", formattedMins.contains("-"))
        assertEquals("Departed", formatted)
        assertEquals("Departed", formattedMins)
    }

    @Test
    fun testMinutesAndSecondsFormatting() {
        val targetTime = "15:38:55"
        // 7m 24s before
        val t7m24s = parseDateTime("15:31:31")
        assertEquals("7m 24s", getCountdownFormatted(targetTime, t7m24s))
        assertEquals("7m", getCountdownFormattedMins(targetTime, t7m24s))

        // 1h 15m before
        val t1h15m = parseDateTime("14:23:55")
        assertEquals("75m 00s", getCountdownFormatted(targetTime, t1h15m))
        assertEquals("1h 15m", getCountdownFormattedMins(targetTime, t1h15m))
    }
}
