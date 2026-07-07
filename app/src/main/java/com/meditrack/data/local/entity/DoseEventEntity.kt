package com.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meditrack.domain.model.DoseStatus
import java.time.LocalDateTime

@Entity(
    tableName = "dose_events",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("medicationId"),
        Index(value = ["medicationId", "scheduledDateTime"], unique = true)
    ]
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduledDateTime: LocalDateTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenDateTime: LocalDateTime? = null,
    val skippedDateTime: LocalDateTime? = null,
    val doseAmount: Double,
    val note: String? = null
)
