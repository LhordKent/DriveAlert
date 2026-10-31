package com.lhordkent.drivealert.ui.postauth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.lhordkent.drivealert.data.monitoringlog.*
import com.lhordkent.drivealert.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

private fun logDate(time: Long, pattern: String): String = Instant.ofEpochMilli(time)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
private fun seconds(time: Long?): String = time?.let { String.format(Locale.getDefault(), "%.2f s", it / 1000.0) } ?: "Not recorded"
private fun number(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: "Not recorded"

fun MonitoringLogEvent.logTitle(): String = when (kind) {
    "START" -> "Monitoring started"
    "RECOVERED" -> "Monitoring resumed after recovery"
    "COMPLETED" -> "Monitoring ended"
    "INTERRUPTED" -> "Interruption recorded"
    "SIGN" -> when (sign) {
        "PROLONGED_EYE_CLOSURE" -> "Prolonged eye closure"
        "YAWNING" -> "Yawning"
        else -> "Head nodding"
    }
    "ALARM" -> "Stage $stage alarm${if (repeated) " · repeat" else ""}"
    else -> "Monitoring event"
}

fun MonitoringLogEvent.logEvidence(): String = when (kind) {
    "SIGN" -> when (sign) {
        "PROLONGED_EYE_CLOSURE" -> "EAR ${number(measuredValue)} · threshold < ${number(threshold)}\nDuration ${seconds(durationMs)} · required ${seconds(requiredDurationMs)}"
        "YAWNING" -> "MAR ${number(measuredValue)} · threshold > ${number(threshold)}\nDuration ${seconds(durationMs)} · required ${seconds(requiredDurationMs)}"
        else -> "Downward pitch ${number(measuredValue)}° · angle threshold > ${number(threshold)}°\nRequired observation window ${seconds(requiredDurationMs ?: durationMs)}\nDownward ratio ${number(downwardRatio)} · required ≥ ${number(ratioThreshold)}"
    }
    "ALARM" -> when (result) {
        "ACCEPTED" -> "Command accepted by ESP32"
        "FAILED" -> "Request failed or timed out"
        "UNAVAILABLE" -> "Hardware unavailable · command not accepted"
        "UNKNOWN_INTERRUPTED" -> "Result unknown · request interrupted"
        else -> "Waiting for ESP32 response"
    } + (responseAt?.let { "\n${if (result == "UNKNOWN_INTERRUPTED") "Recorded" else "Response"} ${logDate(it, "HH:mm:ss.SSS")} · ${seconds((it - occurredAt).coerceAtLeast(0))}" } ?: "")
    "INTERRUPTED" -> "Recorded when the app detected the interruption; the exact stop time may be unknown."
    "RECOVERED" -> "Events while the app was unavailable may be missing."
    else -> ""
}

/** UID is supplied only from the authenticated Driver route, never from navigation arguments. */
@Composable
fun MonitoringLogsScreen(uid: String, repository: MonitoringLogRepository, writer: MonitoringLogWriter, onBack: () -> Unit) {
    var sessionId by rememberSaveable(uid) { mutableStateOf<String?>(null) }
    var retention by rememberSaveable(uid) { mutableStateOf(false) }
    var limit by rememberSaveable(uid) { mutableStateOf(50) }
    var eventLimit by rememberSaveable(uid, sessionId) { mutableStateOf(50) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var busy by remember(uid) { mutableStateOf(false) }
    var confirmation by remember(uid) { mutableStateOf<String?>(null) }
    var proposedDays by remember(uid) { mutableStateOf<Int?>(null) }
    var previewCount by remember(uid) { mutableStateOf(0) }
    var previewCutoff by remember(uid) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { confirmation = null; error = "Could not update monitoring logs. Check available storage and try again." }
            finally { busy = false }
        }
    }
    LaunchedEffect(uid, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try { repository.cleanupAll() } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { error = "Automatic cleanup could not finish. Check storage and reopen Monitoring Logs to retry." }
                delay(60_000)
            }
        }
    }
    val sessionsFlow = remember(uid, limit) { repository.dao.sessions(uid, limit).catch { error = "Could not load sessions. Reopen Monitoring Logs to retry."; emit(emptyList()) } }
    val sessions by sessionsFlow.collectAsState(initial = null)
    val preferencesFlow = remember(uid) { repository.dao.preference(uid).catch { error = "Could not load deletion settings. Reopen Monitoring Logs to retry." } }
    val pref by preferencesFlow.collectAsState(initial = null)
    var preferenceLoaded by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) { repository.dao.preference(uid).catch { error = "Could not load deletion settings." }.collect { preferenceLoaded = true } }
    val failedDrivers by writer.failedDrivers.collectAsState()
    val back = { if (retention) retention = false else if (sessionId != null) sessionId = null else onBack() }
    BackHandler(onBack = back)
    val selectedId = sessionId
    if (retention) {
        MonitoringLogRetentionScreen(pref?.retentionDays, preferenceLoaded, busy, error, back) { days ->
            action {
                proposedDays = days
                previewCutoff = repository.cutoff(days)
                previewCount = repository.dao.deletionCount(uid, previewCutoff)
                confirmation = "retention"
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Ink).navigationBarsPadding()) {
            DriveAlertTopBar(if (selectedId == null) "Monitoring Logs" else "Session Log", onBack = back)
            val listState = rememberLazyListState()
            val headerFlow = remember(uid, selectedId) { (selectedId?.let { repository.dao.observe(uid, it) } ?: kotlinx.coroutines.flow.flowOf(null))
                .catch { error = "Could not load this session. Reopen it to retry."; emit(null) } }
            val eventFlow = remember(uid, selectedId, eventLimit) { (selectedId?.let { repository.dao.events(uid, it, eventLimit) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                .catch { error = "Could not load events. Reopen this session to retry."; emit(emptyList()) } }
            val header by headerFlow.collectAsState(initial = null)
            val events by eventFlow.collectAsState(initial = emptyList())
            var detailLoaded by remember(uid, selectedId) { mutableStateOf(false) }
            LaunchedEffect(headerFlow) { headerFlow.collect { detailLoaded = true } }
            if (selectedId != null && header?.endedAt == null && events.isNotEmpty()) {
                TextButton(onClick = { scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } }, modifier = Modifier.align(Alignment.End)) { Text("Jump to latest") }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).widthIn(max = 600.dp).align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                item("feedback") {
                    error?.let { Text(it, color = DriveRed, modifier = Modifier.padding(vertical = 12.dp)) }
                    if (uid in failedDrivers) Text("Some monitoring events could not be saved. This phone may have incomplete logs. Check available storage.", color = DriveRed, modifier = Modifier.padding(vertical = 12.dp))
                }
                if (selectedId == null) {
                    item("intro") {
                        Text("Your monitoring sessions", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("Private to your Driver account on this phone.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                        SettingsRow("Automatic deletion", { retention = true }, supportingText = pref?.retentionDays?.let { "After $it days from session end" } ?: "Keep until I delete")
                        if (!sessions.isNullOrEmpty()) TextButton(onClick = { action { previewCutoff = null; previewCount = repository.dao.deletionCount(uid, null); confirmation = "clear" } }, enabled = !busy) { Text("Clear completed logs", color = DriveRed) }
                        HorizontalDivider(color = Border)
                        if (sessions == null) Text("Loading sessions…", color = TextSecondary, modifier = Modifier.padding(vertical = 24.dp))
                        else if (sessions.isNullOrEmpty()) {
                            Text("No monitoring sessions yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(top = 28.dp))
                            Text("Start monitoring to record a session’s signs and alarm requests. Earlier sessions are not added.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    sessions.orEmpty().forEachIndexed { index, log ->
                        val date = logDate(log.startedAt, "EEEE, d MMM yyyy")
                        if (index == 0 || date != logDate(sessions!![index - 1].startedAt, "EEEE, d MMM yyyy")) item("date-${log.sessionId}") {
                            Text(date, color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
                        }
                        item(log.sessionId) {
                            SettingsRow(if (log.endedAt == null) "Current session · ${logDate(log.startedAt, "HH:mm")}" else "${logDate(log.startedAt, "HH:mm")} – ${logDate(log.endedAt, "HH:mm")}",
                                { sessionId = log.sessionId }, supportingText = if (log.endedAt == null) "In progress · open live log" else "${log.status.lowercase().replaceFirstChar(Char::uppercase)} · ${seconds((log.endedAt - log.startedAt).coerceAtLeast(0))}")
                            HorizontalDivider(color = Border)
                        }
                    }
                    if (sessions?.size == limit) item("more") { TextButton(onClick = { limit += 50 }) { Text("Load more sessions") } }
                } else {
                    item("summary") {
                        if (!detailLoaded) Text("Loading session…", color = TextSecondary)
                        else if (header == null) Text("This session log is no longer available.", color = TextSecondary)
                        header?.let { log ->
                            Text(logDate(log.startedAt, "d MMM yyyy"), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text("Started ${logDate(log.startedAt, "HH:mm:ss")}\n${log.endedAt?.let { "${if (log.status == "INTERRUPTED") "Interruption recorded" else "Ended"} ${logDate(it, "HH:mm:ss")} · ${seconds((it - log.startedAt).coerceAtLeast(0))}" } ?: "In progress · updates live"}", color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                            if (log.recordingGap) Text("Incomplete log · some events could not be recorded.", color = DriveRed, modifier = Modifier.padding(bottom = 12.dp))
                            Text("Timeline", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            if (log.endedAt != null) TextButton(onClick = { confirmation = "delete" }, enabled = !busy) { Text("Delete session log", color = DriveRed) }
                            if (events.size == eventLimit) TextButton(onClick = { eventLimit += 50 }) { Text("Load earlier events") }
                        }
                    }
                    items(events.asReversed(), key = { it.eventId }) { event -> MonitoringLogEventRow(event) }
                }
            }
        }
    }
    confirmation?.let { type ->
        ConfirmDialog(if (type == "retention") "Apply automatic deletion?" else if (type == "clear") "Clear completed logs?" else "Delete session log?",
            (if (type == "delete") "This removes this completed monitoring session log." else if (type == "retention" && proposedDays == null) "Keep completed session logs until you delete them. No logs will be removed now."
            else "This removes $previewCount completed session logs${if (type == "retention") " now. Older completed logs will also be removed automatically" else ""}.") +
                " Current sessions are protected. Alert History stays unchanged. Deletion cannot be undone." + (error?.let { "\n\n$it" } ?: ""),
            if (busy) "Saving…" else if (type == "retention") "Save and apply" else "Delete",
            onConfirm = {
                action {
                    if (type == "delete") { selectedId?.let { repository.dao.delete(uid, it) }; sessionId = null }
                    else if (!repository.confirmDeletion(uid, previewCutoff, previewCount, type == "retention", proposedDays)) {
                        previewCount = repository.dao.deletionCount(uid, previewCutoff)
                        error = "The completed logs changed. Review the updated count before confirming."
                        return@action
                    }
                    confirmation = null
                }
            }, onDismiss = { if (!busy) confirmation = null })
    }
}

@Composable
fun MonitoringLogEventRow(event: MonitoringLogEvent) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(logDate(event.occurredAt, "HH:mm:ss.SSS"), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(event.logTitle(), style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
        val evidence = event.logEvidence()
        if (evidence.isNotEmpty()) Text(evidence, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
    }
    HorizontalDivider(color = Border)
}

@Composable
private fun MonitoringLogRetentionScreen(current: Int?, loaded: Boolean, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (Int?) -> Unit) {
    var selected by rememberSaveable(current) { mutableStateOf(current?.takeIf { it in listOf(1, 3, 7) } ?: if (current == null) 0 else -1) }
    var custom by rememberSaveable(current) { mutableStateOf(if (selected == -1) current.toString() else "") }
    val days = when (selected) { 0 -> null; -1 -> custom.toIntOrNull(); else -> selected }
    val valid = selected != -1 || days != null && days in 1..3650
    ScrollableScreen("Log deletion", onBack) {
        Text("Keep monitoring logs", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text("Choose when completed session logs are removed from this phone. This setting is separate from Alert History.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        Column(Modifier.selectableGroup()) {
            listOf(1 to "1 day", 3 to "3 days", 7 to "7 days", -1 to "Custom", 0 to "Keep until I delete").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected == value, enabled = loaded && !busy, role = Role.RadioButton, onClick = { selected = value }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == value, onClick = null, enabled = loaded && !busy)
                    Text(label, color = TextPrimary, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        if (selected == -1) OutlinedTextField(custom, { custom = it.take(5) }, label = { Text("Number of days") },
            supportingText = { Text("Enter 1 to 3,650 whole days.") }, singleLine = true, isError = custom.isNotEmpty() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("Each day means 24 hours after the session ends. Current sessions are kept. Android may delay background cleanup. Removed logs cannot be restored.", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 20.dp))
        error?.let { Text(it, color = DriveRed) }
        PrimaryButton(if (busy) "Saving…" else "Save", { onSave(days) }, enabled = loaded && !busy && valid && days != current)
    }
}
