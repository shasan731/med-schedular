package com.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meditrack.domain.model.IntervalUnit
import com.meditrack.domain.model.ScheduleType

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class MedicationScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduleType: ScheduleType,
    val timeOfDay: String? = null,
    val intervalValue: Int? = null,
    val intervalUnit: IntervalUnit? = null,
    val daysOfWeek: String? = null,
    val dayOfMonth: Int? = null,
    val isActive: Boolean = true
)
