package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.postauth.WarningStage

data class ActiveMonitoringState(
    val isActive: Boolean = false,
    val sessionId: String? = null,
    val startedAtElapsedMs: Long? = null,
    val durationMs: Long = 0L,
    val currentStage: WarningStage? = null,
    val confirmedEventCount: Int = 0,
    val latestSigns: Set<VisibleSign> = emptySet(),
    val latestEventAtElapsedMs: Long? = null,
    val warningDelivery: WarningDeliveryStatus = WarningDeliveryStatus.NOT_REQUESTED,
)

enum class WarningDeliveryStatus(val label: String) {
    NOT_REQUESTED("Not requested"),
    HARDWARE_PENDING("Onboard speaker delivery pending"),
    DELIVERED("Delivered"),
    FAILED("Delivery failed"),
}

data class WarningOutputCommand(
    val stage: WarningStage,
    val sound: WarningSound,
    val volume: PreferredVolume,
    val spokenAdvisory: String?,
)

fun interface WarningOutputGateway {
    suspend fun activate(command: WarningOutputCommand): WarningDeliveryStatus
}

/** Hardware command transport is intentionally not claimed until Android-to-ESP32 output exists. */
class PendingHardwareWarningOutputGateway : WarningOutputGateway {
    override suspend fun activate(command: WarningOutputCommand) = WarningDeliveryStatus.HARDWARE_PENDING
}

sealed interface MonitoringEffect {
    data class RecordAlert(
        val occurredAtElapsedMs: Long,
        val stage: WarningStage,
        val signs: Set<VisibleSign>,
    ) : MonitoringEffect

    data class ActivateWarning(val stage: WarningStage) : MonitoringEffect

    data class MergeLatestAlertSigns(val signs: Set<VisibleSign>) : MonitoringEffect

    data class RecordStage3Boundary(
        val type: StageSyncRecordType,
        val periodStartedAtElapsedMs: Long,
        val periodEndedAtElapsedMs: Long,
        val eventCount: Int,
        val signs: Set<VisibleSign>,
    ) : MonitoringEffect
}

private data class WarningPeriod(
    val stage: WarningStage,
    val startedAtMs: Long,
    val recurrence: Boolean,
    val eventCount: Int,
    val signs: Set<VisibleSign>,
)

/** Pure manuscript warning-stage state machine. All time inputs must be monotonic. */
class WarningStageController(
    private val periodDurationMs: Long = DEFAULT_PERIOD_MS,
) {
    private var period: WarningPeriod? = null

    val stage: WarningStage? get() = period?.stage

    init {
        require(periodDurationMs > 0L)
    }

    fun reset() {
        period = null
    }

    fun onDistinctEvent(
        nowMs: Long,
        signs: Set<VisibleSign>,
        persistentlyActiveSigns: Set<VisibleSign> = emptySet(),
    ): List<MonitoringEffect> {
        require(signs.isNotEmpty())
        val effects = advance(nowMs, persistentlyActiveSigns).toMutableList()
        val current = period
        period = when (current?.stage) {
            null -> WarningPeriod(WarningStage.STAGE_1, nowMs, false, 1, signs)
            WarningStage.STAGE_1 -> WarningPeriod(WarningStage.STAGE_2, nowMs, false, 1, signs)
            WarningStage.STAGE_2 -> {
                effects += current.stage3Record(
                    type = StageSyncRecordType.STAGE3_TRANSITION,
                    endedAtMs = nowMs,
                    additionalEventCount = 1,
                    additionalSigns = signs,
                )
                WarningPeriod(WarningStage.STAGE_3, nowMs, false, 1, signs)
            }
            WarningStage.STAGE_3 -> current.copy(
                recurrence = true,
                eventCount = current.eventCount + 1,
                signs = current.signs + signs,
            )
        }
        effects += MonitoringEffect.ActivateWarning(checkNotNull(period).stage)
        return effects
    }

    fun advance(
        nowMs: Long,
        persistentlyActiveSigns: Set<VisibleSign> = emptySet(),
    ): List<MonitoringEffect> {
        val effects = mutableListOf<MonitoringEffect>()
        while (true) {
            val current = period ?: break
            val boundary = current.startedAtMs + periodDurationMs
            if (nowMs < boundary) break
            period = when (current.stage) {
                WarningStage.STAGE_1 -> if (persistentlyActiveSigns.isNotEmpty()) {
                    effects += MonitoringEffect.ActivateWarning(WarningStage.STAGE_2)
                    current.nextPeriod(WarningStage.STAGE_2, boundary, persistentlyActiveSigns)
                } else null
                WarningStage.STAGE_2 -> if (persistentlyActiveSigns.isNotEmpty()) {
                    effects += current.stage3Record(
                        StageSyncRecordType.STAGE3_TRANSITION,
                        boundary,
                        additionalSigns = persistentlyActiveSigns,
                    )
                    effects += MonitoringEffect.ActivateWarning(WarningStage.STAGE_3)
                    current.nextPeriod(WarningStage.STAGE_3, boundary, persistentlyActiveSigns)
                } else {
                    WarningPeriod(WarningStage.STAGE_1, boundary, false, 0, emptySet())
                }
                WarningStage.STAGE_3 -> if (current.recurrence || persistentlyActiveSigns.isNotEmpty()) {
                    effects += current.stage3Record(
                        StageSyncRecordType.STAGE3_PERSISTENCE,
                        boundary,
                        additionalSigns = persistentlyActiveSigns,
                    )
                    effects += MonitoringEffect.ActivateWarning(WarningStage.STAGE_3)
                    current.nextPeriod(WarningStage.STAGE_3, boundary, persistentlyActiveSigns)
                } else {
                    WarningPeriod(WarningStage.STAGE_2, boundary, false, 0, emptySet())
                }
            }
        }
        return effects
    }

    internal fun periodStartedAtMs(): Long? = period?.startedAtMs

    private fun WarningPeriod.nextPeriod(
        nextStage: WarningStage,
        startedAtMs: Long,
        persistentSigns: Set<VisibleSign>,
    ) = WarningPeriod(
        stage = nextStage,
        startedAtMs = startedAtMs,
        recurrence = false,
        eventCount = if (persistentSigns.isEmpty()) 0 else 1,
        signs = persistentSigns,
    )

    private fun WarningPeriod.stage3Record(
        type: StageSyncRecordType,
        endedAtMs: Long,
        additionalEventCount: Int = 0,
        additionalSigns: Set<VisibleSign> = emptySet(),
    ) = MonitoringEffect.RecordStage3Boundary(
        type = type,
        periodStartedAtElapsedMs = startedAtMs,
        periodEndedAtElapsedMs = endedAtMs,
        eventCount = (eventCount + additionalEventCount).coerceAtLeast(1),
        signs = signs + additionalSigns,
    ).also {
        check(it.signs.isNotEmpty()) {
            "A Stage 3 synchronization boundary must include its qualifying signs."
        }
    }

    companion object {
        const val DEFAULT_PERIOD_MS = 60_000L
    }
}

/** Owns distinct-event accounting for one active monitoring session. */
class MonitoringSessionController(
    private val clock: () -> Long,
    private val warningStages: WarningStageController = WarningStageController(),
) {
    private val seenEvents = mutableSetOf<Pair<SignType, Long>>()
    private var activeEpisodeSigns = emptySet<VisibleSign>()
    var state: ActiveMonitoringState = ActiveMonitoringState()
        private set

    fun start(sessionId: String) {
        require(sessionId.isNotBlank())
        warningStages.reset()
        seenEvents.clear()
        activeEpisodeSigns = emptySet()
        val now = clock()
        state = ActiveMonitoringState(isActive = true, sessionId = sessionId, startedAtElapsedMs = now)
    }

    fun stop() {
        warningStages.reset()
        seenEvents.clear()
        activeEpisodeSigns = emptySet()
        state = ActiveMonitoringState()
    }

    fun accept(
        events: List<ConfirmedSignEvent>,
        currentlyConfirmedSigns: Set<VisibleSign> = events.mapTo(linkedSetOf()) { it.type.toVisibleSign() },
    ): List<MonitoringEffect> {
        if (!state.isActive) return emptyList()
        val episodeContinues = activeEpisodeSigns.any { it in currentlyConfirmedSigns }
        if (!episodeContinues) activeEpisodeSigns = emptySet()
        if (events.isEmpty()) return emptyList()
        val distinct = events.filter { seenEvents.add(it.type to it.timestampMs) }
        if (distinct.isEmpty()) return emptyList()
        val now = clock()
        val signs = distinct.mapTo(linkedSetOf()) { it.type.toVisibleSign() }
        if (activeEpisodeSigns.isNotEmpty()) {
            activeEpisodeSigns = activeEpisodeSigns + signs
            state = state.copy(
                durationMs = duration(now),
                latestSigns = state.latestSigns + signs,
                latestEventAtElapsedMs = now,
            )
            return listOf(MonitoringEffect.MergeLatestAlertSigns(signs))
        }
        val stageEffects = warningStages.onDistinctEvent(now, signs, activeEpisodeSigns)
        val effects = stageEffects.filterNotTo(mutableListOf()) {
            it is MonitoringEffect.RecordStage3Boundary
        }
        val stage = requireNotNull(warningStages.stage)
        state = state.copy(
            durationMs = duration(now),
            currentStage = stage,
            confirmedEventCount = state.confirmedEventCount + 1,
            latestSigns = signs,
            latestEventAtElapsedMs = now,
        )
        activeEpisodeSigns = signs
        effects += MonitoringEffect.RecordAlert(now, stage, signs)
        effects += stageEffects.filterIsInstance<MonitoringEffect.RecordStage3Boundary>()
        return effects
    }

    fun tick(): List<MonitoringEffect> {
        if (!state.isActive) return emptyList()
        val now = clock()
        val effects = warningStages.advance(now, activeEpisodeSigns)
        state = state.copy(durationMs = duration(now), currentStage = warningStages.stage)
        return effects
    }

    fun setWarningDelivery(status: WarningDeliveryStatus) {
        state = state.copy(warningDelivery = status)
    }

    private fun duration(nowMs: Long) = (nowMs - checkNotNull(state.startedAtElapsedMs)).coerceAtLeast(0L)
}

fun SignType.toVisibleSign() = when (this) {
    SignType.PROLONGED_EYE_CLOSURE -> VisibleSign.PROLONGED_EYE_CLOSURE
    SignType.YAWNING -> VisibleSign.YAWNING
    SignType.HEAD_NODDING -> VisibleSign.HEAD_NODDING
}

fun WarningStage.warningOutputCommand(sound: WarningSound, volume: PreferredVolume) = WarningOutputCommand(
    stage = this,
    sound = sound,
    volume = volume,
    spokenAdvisory = if (this == WarningStage.STAGE_1) null else REST_ADVISORY,
)

const val REST_ADVISORY = "DriveAlert recommends stopping in a safe place and taking a rest."
