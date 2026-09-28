package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.detection.model.MonitoringDetectionResult
import com.lhordkent.drivealert.monitoring.ActiveMonitoringState
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.postauth.AlertEvent
import com.lhordkent.drivealert.postauth.AlertHistoryInsights
import com.lhordkent.drivealert.postauth.MonitoringSessionSummary
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.postauth.SharingState
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import com.lhordkent.drivealert.postauth.presentation
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.DriveRedSoft
import com.lhordkent.drivealert.ui.theme.InkRaised
import com.lhordkent.drivealert.ui.theme.Success
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MonitoringPreviewScreen(
    scenario: MonitoringScenario,
    monitoring: ActiveMonitoringState = ActiveMonitoringState(),
    onExit: () -> Unit,
    detectionResult: MonitoringDetectionResult? = null,
) {
    val presentation = scenario.presentation()
    val activeWarningStage = monitoring.currentStage
    ScrollableScreen(title = "Monitoring Activity", onBack = onExit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (monitoring.isActive) presentation.headline else "Monitoring inactive", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(if (monitoring.isActive) presentation.guidance else "Start monitoring from Driver Home to begin a session.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(Modifier.size(12.dp))
            StatusPill(
                label = when {
                    !monitoring.isActive -> "Inactive"
                    presentation.headline == "Monitoring active" -> "Active"
                    presentation.headline == "Monitoring limited" -> "Limited"
                    else -> "Paused"
                },
                tone = when {
                    !monitoring.isActive -> StatusTone.INFO
                    presentation.headline == "Monitoring active" -> StatusTone.SUCCESS
                    presentation.headline == "Monitoring limited" -> StatusTone.WARNING
                    else -> StatusTone.ERROR
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        GroupSurface {
            KeyValueRow("Session duration", monitoring.durationMs.durationText())
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Current warning", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (activeWarningStage == null) {
                    Text("No active warning", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                } else {
                    StatusPill("Warning ${activeWarningStage.label}", activeWarningStage.statusTone())
                }
            }
            KeyValueRow("Confirmed events", monitoring.confirmedEventCount.toString())
            KeyValueRow("Latest event", monitoring.latestSigns.joinToString { it.label }.ifEmpty { "None" })
        }
        if (activeWarningStage != null) {
            Spacer(Modifier.height(16.dp))
            GroupSurface {
                Text("${activeWarningStage.label}: ${activeWarningStage.explanation}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(warningOutputDescription(activeWarningStage, monitoring.warningDelivery), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Monitoring availability")
        Spacer(Modifier.height(8.dp))
        AvailabilityRow("Eye Monitoring", presentation.eyeAvailable)
        AvailabilityRow("Yawning Monitoring", presentation.yawningAvailable)
        AvailabilityRow("Head Monitoring", presentation.headAvailable)
        Spacer(Modifier.height(24.dp))
        SectionTitle("On-device detection diagnostics")
        Spacer(Modifier.height(8.dp))
        GroupSurface {
            if (detectionResult == null) {
                Text(
                    "Detection engine is installed; no camera frame has reached active monitoring yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                KeyValueRow("Face", if (detectionResult.faceDetected) "Detected" else "Unavailable")
                KeyValueRow("EAR", detectionResult.measurements.ear.metricText())
                KeyValueRow("EAR threshold", detectionResult.eye.threshold.metricText())
                KeyValueRow("Eye state", detectionResult.eye.state.name)
                KeyValueRow("Eye candidate", (detectionResult.eye.candidateDurationMs / 1_000.0).metricText(" s"))
                KeyValueRow("MAR", detectionResult.measurements.mar.metricText())
                KeyValueRow("Open-mouth mean", detectionResult.activeCalibration?.openMouthMar.metricText())
                KeyValueRow("MAR threshold", detectionResult.yawn.threshold.metricText())
                KeyValueRow("Yawn state", detectionResult.yawn.state.name)
                KeyValueRow("Yawn candidate", (detectionResult.yawn.candidateDurationMs / 1_000.0).metricText(" s"))
                KeyValueRow("Raw head pitch", detectionResult.measurements.rawHeadPitchDegrees.metricText("°"))
                KeyValueRow("Neutral head pitch", detectionResult.activeCalibration?.neutralHeadPitchDegrees.metricText("°"))
                KeyValueRow("Relative head pitch", detectionResult.relativeHeadPitchDegrees.metricText("°"))
                KeyValueRow("Head threshold", detectionResult.head.threshold.metricText("°"))
                KeyValueRow("Downward ratio", detectionResult.headDownwardRatio.metricText())
                KeyValueRow("Head state", detectionResult.head.state.name)
                KeyValueRow("Events in latest frame", detectionResult.events.joinToString { it.type.name }.ifEmpty { "None" })
            }
        }
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Back to Driver Home", onExit)
        Spacer(Modifier.height(16.dp))
    }
}

private fun Double?.metricText(suffix: String = ""): String =
    this?.let { "%.4f%s".format(it, suffix) } ?: "Unavailable"

@Composable
private fun AvailabilityRow(label: String, available: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (available) Icons.Rounded.CheckCircle else Icons.Rounded.VisibilityOff,
            contentDescription = if (available) "Available" else "Unavailable",
            tint = if (available) Success else DriveRed,
        )
        Spacer(Modifier.size(6.dp))
        Text(if (available) "Available" else "Unavailable", style = MaterialTheme.typography.labelLarge, color = if (available) Success else DriveRed)
    }
}

@Composable
fun AlertHistoryScreen(
    events: List<AlertEvent>,
    syncRecords: List<Stage3SyncRecord>,
    selectedFilter: WarningStage?,
    onFilterSelected: (WarningStage?) -> Unit,
    insights: AlertHistoryInsights = AlertHistoryInsights(),
    sessions: List<MonitoringSessionSummary> = emptyList(),
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onEventSelected: (String) -> Unit,
) {
    val today = LocalDate.now()
    val filtered = events.filter { selectedFilter == null || it.stage == selectedFilter }.sortedByDescending { it.occurredAt }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryValue("Today", insights.alertsToday.toString(), Modifier.weight(1f))
            SummaryValue("7 days", insights.weeklyAlerts.toString(), Modifier.weight(1f))
            SummaryValue("Highest", insights.highestStage?.label ?: "None", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        GroupSurface {
            SectionTitle("Insights")
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Most frequent sign", insights.mostFrequentSign?.label ?: "No alerts")
            KeyValueRow("Alert frequency", insights.trend.label)
            KeyValueRow("Stage 3 synchronization", syncSummary(syncRecords))
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlertFilterChip("All", selectedFilter == null) { onFilterSelected(null) }
            WarningStage.values().forEach { stage ->
                AlertFilterChip(stage.label, selectedFilter == stage) { onFilterSelected(stage) }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (errorMessage != null) {
            EmptyState("Alert history unavailable", errorMessage)
        } else if (isLoading) {
            EmptyState("Loading alert history", "Your locally stored records will appear here shortly.")
        } else if (filtered.isEmpty() && events.isEmpty()) {
            EmptyState("No alerts recorded yet", "Confirmed warning events will appear here after monitoring.")
        } else if (filtered.isEmpty()) {
            EmptyState("No alerts in this filter", "Choose another Warning Stage to review your event history.")
        } else {
            filtered.groupBy { it.occurredAt.toLocalDate() }.forEach { (date, dateEvents) ->
                SectionTitle(relativeDate(date, today), Modifier.padding(top = 8.dp, bottom = 4.dp))
                dateEvents.forEach { event ->
                    AlertEventRow(
                        event,
                        session = sessions.firstOrNull { it.id == event.sessionId },
                        syncRecords = syncRecords.filter { it.sessionId != null && it.sessionId == event.sessionId },
                        onClick = { onEventSelected(event.id) },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(InkRaised, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun AlertFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = InkRaised,
            labelColor = TextSecondary,
            selectedContainerColor = DriveRedSoft,
            selectedLabelColor = TextPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Border,
            selectedBorderColor = DriveRed,
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
fun AlertEventRow(
    event: AlertEvent,
    session: MonitoringSessionSummary? = null,
    syncRecords: List<Stage3SyncRecord> = emptyList(),
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(event.occurredAt.format(DateTimeFormatter.ofPattern("h:mm a")), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            StagePill(event.stage)
        }
        Spacer(Modifier.height(7.dp))
        Text(event.signs.joinToString { it.label }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        session?.let {
            Spacer(Modifier.height(4.dp))
            Text("Session ${it.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (syncRecords.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Stage 3 sync: ${syncSummary(syncRecords)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun AlertDetailScreen(
    event: AlertEvent,
    onBack: () -> Unit,
    session: MonitoringSessionSummary? = null,
    syncRecords: List<Stage3SyncRecord> = emptyList(),
) {
    ScrollableScreen(title = "Alert Detail", onBack = onBack) {
        Text(
            event.occurredAt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(Modifier.height(24.dp))
        SectionTitle("Event information")
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Detected visible sign(s)", event.signs.joinToString { it.label })
        KeyValueRow("Monitoring session", session?.let { "${it.id.take(8)} (${it.status})" } ?: "Unavailable")
        KeyValueRow("Warning output", when (event.alarmTriggered) {
            true -> "Triggered"
            false -> "Not delivered"
            null -> "Unavailable"
        })
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Warning Stage at Detection", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
            StagePill(event.stage)
        }
        Spacer(Modifier.height(20.dp))
        GroupSurface {
            Text("${event.stage.label}: ${event.stage.explanation}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (event.stage == WarningStage.STAGE_3) {
                    "This is the maximum system intervention stage. It does not describe drowsiness severity."
                } else {
                    "Warning Stages describe increasing system intervention, not drowsiness severity."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Record visibility")
        Spacer(Modifier.height(10.dp))
        StatusPill("Driver history", StatusTone.NEUTRAL)
        Spacer(Modifier.height(10.dp))
        KeyValueRow(
            "Trusted Contact synchronization",
            when {
                event.stage != WarningStage.STAGE_3 -> "Not eligible"
                syncRecords.isEmpty() -> "No session boundary record available"
                else -> syncSummary(syncRecords)
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (event.stage == WarningStage.STAGE_3) "This confirmed event remains in Driver Alert History. A separate Stage 3 transition or persistence record may be eligible for Trusted Contact synchronization."
            else "Stage 1 and Stage 2 events remain in Driver Alert History and are not eligible for Trusted Contact synchronization.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Stage3SyncRecordDetailScreen(record: Stage3SyncRecord, onBack: () -> Unit) {
    ScrollableScreen(title = "Shared Record", onBack = onBack) {
        Text(record.occurredAt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        record.sourceDriverName?.let {
            Spacer(Modifier.height(4.dp))
            Text("Shared by $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Stage 3 synchronization")
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Record type", record.kind.label)
        KeyValueRow("Detected visible sign(s)", record.signs.joinToString { it.label })
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Warning Stage", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            StagePill(WarningStage.STAGE_3)
        }
        Spacer(Modifier.height(18.dp))
        SharingPill(record.sharingState)
        record.receivedAt?.let {
            Spacer(Modifier.height(8.dp))
            Text("Received ${it.format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a"))}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(18.dp))
        Text("This record reports a Stage 3 transition or continued Stage 3 period. It is an intervention record, not a diagnosis or measurement of drowsiness severity.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
    }
}

private fun warningOutputDescription(stage: WarningStage, delivery: WarningDeliveryStatus): String = when (stage) {
    WarningStage.STAGE_1 -> "Selected warning sound requested. ${delivery.label}."
    WarningStage.STAGE_2 -> "Selected warning sound requested. The fixed rest advisory remains pending a voice-only Stage 2 SD track. ${delivery.label}."
    WarningStage.STAGE_3 -> "Maximum Driver warning requested. ${delivery.label}. Eligible Stage 3 boundary records are queued separately."
}

private fun WarningStage.statusTone() = when (this) {
    WarningStage.STAGE_1 -> StatusTone.INFO
    WarningStage.STAGE_2 -> StatusTone.WARNING
    WarningStage.STAGE_3 -> StatusTone.ERROR
}

private fun Long.durationText(): String {
    val totalSeconds = this.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun syncSummary(records: List<Stage3SyncRecord>): String {
    if (records.isEmpty()) return "No eligible records"
    val shared = records.count { it.sharingState == SharingState.SHARED }
    val waiting = records.count { it.sharingState == SharingState.PENDING || it.sharingState == SharingState.FAILED }
    return "$shared shared, $waiting waiting"
}

private fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
}
