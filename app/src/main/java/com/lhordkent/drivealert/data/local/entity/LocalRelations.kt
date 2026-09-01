package com.lhordkent.drivealert.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AlertWithSigns(
    @Embedded val alert: AlertEntity,
    @Relation(parentColumn = "alertId", entityColumn = "alertId")
    val signs: List<AlertSignEntity>,
)

data class StageSyncRecordWithSigns(
    @Embedded val record: StageSyncRecordEntity,
    @Relation(parentColumn = "stageSyncRecordId", entityColumn = "stageSyncRecordId")
    val signs: List<StageSyncRecordSignEntity>,
)
