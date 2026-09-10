package com.lhordkent.drivealert.postauth

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertHistoryInsightsTest {
    private val today = LocalDate.of(2026, 9, 9)
    private fun alert(id: String, daysAgo: Long, stage: WarningStage, vararg signs: VisibleSign) = AlertEvent(
        id, LocalDateTime.of(today.minusDays(daysAgo), java.time.LocalTime.NOON), signs.toSet(), stage,
    )

    @Test fun calculatesTodayWeeklyMostFrequentHighestAndHigherTrend() {
        val alerts = listOf(
            alert("1", 0, WarningStage.STAGE_1, VisibleSign.YAWNING, VisibleSign.HEAD_NODDING),
            alert("2", 1, WarningStage.STAGE_3, VisibleSign.YAWNING),
            alert("3", 8, WarningStage.STAGE_2, VisibleSign.HEAD_NODDING),
        )
        val value = calculateAlertHistoryInsights(alerts, today)
        assertEquals(1, value.alertsToday)
        assertEquals(2, value.weeklyAlerts)
        assertEquals(VisibleSign.YAWNING, value.mostFrequentSign)
        assertEquals(WarningStage.STAGE_3, value.highestStage)
        assertEquals(AlertTrend.HIGHER, value.trend)
    }

    @Test fun weeklyWindowExcludesFutureAndDaySeven() {
        val alerts = listOf(
            alert("inside", 6, WarningStage.STAGE_1, VisibleSign.YAWNING),
            alert("outside", 7, WarningStage.STAGE_1, VisibleSign.YAWNING),
            AlertEvent("future", today.plusDays(1).atTime(java.time.LocalTime.NOON), setOf(VisibleSign.YAWNING), WarningStage.STAGE_1),
        )
        assertEquals(1, calculateAlertHistoryInsights(alerts, today).weeklyAlerts)
    }

    @Test fun reportsLowerAndUnchangedTrends() {
        val previous = alert("previous", 8, WarningStage.STAGE_1, VisibleSign.YAWNING)
        assertEquals(AlertTrend.LOWER, calculateAlertHistoryInsights(listOf(previous), today).trend)
        assertEquals(AlertTrend.UNCHANGED, calculateAlertHistoryInsights(emptyList(), today).trend)
    }
}
