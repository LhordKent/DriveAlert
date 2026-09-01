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
import com.lhordkent.drivealert.postauth.AlertEvent
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
    activeWarningStage: WarningStage?,
    confirmedEvents: Int,
    latestEvent: LocalDateTime?,
    onExit: () -> Unit,
) {
    val presentation = scenario.presentation()
    ScrollableScreen(title = "Monitoring Preview", onBack = onExit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(presentation.headline, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(presentation.guidance, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(Modifier.size(12.dp))
            StatusPill(
                label = when (presentation.headline) {
                    "Monitoring active" -> "Active"
                    "Monitoring limited" -> "Limited"
                    else -> "Paused"
                },
                tone = when (presentation.headline) {
                    "Monitoring active" -> StatusTone.SUCCESS
                    "Monitoring limited" -> StatusTone.WARNING
                    else -> StatusTone.ERROR
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        GroupSurface {
            KeyValueRow("Session duration", "01:24:16")
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Current warning", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (activeWarningStage == null) Text("No active warning", style = MaterialTheme.typography.labelLarge, color = TextPrimary) else StagePill(activeWarningStage)
            }
            KeyValueRow("Confirmed events", confirmedEvents.toString())
            KeyValueRow("Latest event", latestEvent?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "None")
        }
        if (activeWarningStage != null) {
            Spacer(Modifier.height(16.dp))
            GroupSurface {
                Text("${activeWarningStage.label}: ${activeWarningStage.explanation}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(warningOutputDescription(activeWarningStage), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Monitoring availability")
        Spacer(Modifier.height(8.dp))
        AvailabilityRow("Eye Monitoring", presentation.eyeAvailable)
        AvailabilityRow("Yawning Monitoring", presentation.yawningAvailable)
        AvailabilityRow("Head Monitoring", presentation.headAvailable)
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Exit Preview", onExit)
        Spacer(Modifier.height(16.dp))
    }
}

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
    isLoading: Boolean = false,
    onEventSelected: (String) -> Unit,
) {
    val today = LocalDate.now()
    val todayCount = events.count { it.occurredAt.toLocalDate() == today }
    val weekCount = events.count { !it.occurredAt.toLocalDate().isBefore(today.minusDays(6)) }
    val highestStage = events.maxByOrNull { it.stage.ordinal }?.stage
    val signCounts = VisibleSign.values().associateWith { sign -> events.count { sign in it.signs } }
    val frequentSign = signCounts.maxByOrNull { it.value }?.key?.label ?: "No alerts"
    val currentWeekCount = events.count { !it.occurredAt.toLocalDate().isBefore(today.minusDays(6)) }
    val previousWeekCount = events.count { dateInRange(it.occurredAt.toLocalDate(), today.minusDays(13), today.minusDays(7)) }
    val trend = when {
        currentWeekCount > previousWeekCount -> "Higher than previous 7 days"
        currentWeekCount < previousWeekCount -> "Lower than previous 7 days"
        else -> "Same as previous 7 days"
    }
    val filtered = events.filter { selectedFilter == null || it.stage == selectedFilter }.sortedByDescending { it.occurredAt }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryValue("Today", todayCount.toString(), Modifier.weight(1f))
            SummaryValue("7 days", weekCount.toString(), Modifier.weight(1f))
            SummaryValue("Highest", highestStage?.label ?: "None", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        GroupSurface {
            SectionTitle("Insights")
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Most frequent sign", frequentSign)
            KeyValueRow("Alert frequency", trend)
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
        if (isLoading) {
            EmptyState("Loading alert history", "Your locally stored records will appear here shortly.")
        } else if (filtered.isEmpty() && events.isEmpty()) {
            EmptyState("No alerts recorded yet", "Confirmed warning events will appear here after monitoring.")
        } else if (filtered.isEmpty()) {
            EmptyState("No alerts in this filter", "Choose another Warning Stage to review your event history.")
        } else {
            filtered.groupBy { it.occurredAt.toLocalDate() }.forEach { (date, dateEvents) ->
                SectionTitle(relativeDate(date, today), Modifier.padding(top = 8.dp, bottom = 4.dp))
                dateEvents.forEach { event ->
                    AlertEventRow(event, onClick = { onEventSelected(event.id) })
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
fun AlertEventRow(event: AlertEvent, onClick: () -> Unit) {
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
    }
}

@Composable
fun AlertDetailScreen(
    event: AlertEvent,
    onBack: () -> Unit,
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

private fun warningOutputDescription(stage: WarningStage): String = when (stage) {
    WarningStage.STAGE_1 -> "The selected warning sound is sent to the onboard speaker."
    WarningStage.STAGE_2 -> "The warning sound is repeated with a spoken advisory recommending rest."
    WarningStage.STAGE_3 -> "Maximum intervention is active. An eligible synchronization record may be queued for an approved Trusted Contact."
}

private fun dateInRange(date: LocalDate, start: LocalDate, end: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

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
