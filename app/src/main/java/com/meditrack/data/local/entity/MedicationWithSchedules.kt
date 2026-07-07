package com.meditrack.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MedicationWithSchedules(
    @Embedded val medication: MedicationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val schedules: List<MedicationScheduleEntity>
)
