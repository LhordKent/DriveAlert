package com.lhordkent.drivealert.postauth

import java.time.LocalDate

/** The weekly window is today plus the previous six local calendar days. */
fun calculateAlertHistoryInsights(
    alerts: List<AlertEvent>,
    today: LocalDate,
): AlertHistoryInsights {
    val currentStart = today.minusDays(6)
    val previousStart = today.minusDays(13)
    val previousEnd = today.minusDays(7)
    val current = alerts.filter { !it.occurredAt.toLocalDate().isBefore(currentStart) && !it.occurredAt.toLocalDate().isAfter(today) }
    val previous = alerts.filter { !it.occurredAt.toLocalDate().isBefore(previousStart) && !it.occurredAt.toLocalDate().isAfter(previousEnd) }
    val counts = VisibleSign.values().associateWith { sign -> alerts.count { sign in it.signs } }
    val mostFrequent = counts.entries
        .filter { it.value > 0 }
        .sortedWith(compareByDescending<Map.Entry<VisibleSign, Int>> { it.value }.thenBy { it.key.ordinal })
        .firstOrNull()?.key

    return AlertHistoryInsights(
        alertsToday = alerts.count { it.occurredAt.toLocalDate() == today },
        weeklyAlerts = current.size,
        mostFrequentSign = mostFrequent,
        highestStage = alerts.maxByOrNull { it.stage.ordinal }?.stage,
        trend = when {
            current.size > previous.size -> AlertTrend.HIGHER
            current.size < previous.size -> AlertTrend.LOWER
            else -> AlertTrend.UNCHANGED
        },
    )
}
