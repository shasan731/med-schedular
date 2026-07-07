package com.meditrack.data.local.entity

import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.TreatmentType
import java.time.LocalDateTime

data class DoseEventWithMedication(
    val id: Long,
    val medicationId: Long,
    val scheduledDateTime: LocalDateTime,
    val status: DoseStatus,
    val takenDateTime: LocalDateTime?,
    val skippedDateTime: LocalDateTime?,
    val doseAmount: Double,
    val note: String?,
    val medicationName: String,
    val dosageInstruction: String,
    val doseUnit: String,
    val currentStock: Double,
    val lowStockThresholdDays: Double,
    val treatmentType: TreatmentType
) {
    fun asDoseEvent(): DoseEventEntity = DoseEventEntity(
        id = id,
        medicationId = medicationId,
        scheduledDateTime = scheduledDateTime,
        status = status,
        takenDateTime = takenDateTime,
        skippedDateTime = skippedDateTime,
        doseAmount = doseAmount,
        note = note
    )
}
