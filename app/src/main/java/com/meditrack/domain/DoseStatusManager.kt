package com.meditrack.domain

import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.domain.model.DoseStatus
import java.time.LocalDateTime
import kotlin.math.max

object DoseStatusManager {
    fun markDoseTaken(
        medication: MedicationEntity,
        doseEvent: DoseEventEntity,
        takenAt: LocalDateTime = LocalDateTime.now()
    ): Pair<MedicationEntity, DoseEventEntity> {
        val shouldDeduct = doseEvent.status != DoseStatus.TAKEN
        val newStock = if (shouldDeduct) {
            max(0.0, medication.currentStock - doseEvent.doseAmount)
        } else {
            medication.currentStock
        }
        return medication.copy(
            currentStock = newStock,
            updatedAt = takenAt
        ) to doseEvent.copy(
            status = DoseStatus.TAKEN,
            takenDateTime = takenAt,
            skippedDateTime = null
        )
    }

    fun markDoseSkipped(
        doseEvent: DoseEventEntity,
        skippedAt: LocalDateTime = LocalDateTime.now()
    ): DoseEventEntity {
        return doseEvent.copy(
            status = DoseStatus.SKIPPED,
            skippedDateTime = skippedAt,
            takenDateTime = null
        )
    }

    fun markDoseSkipped(
        medication: MedicationEntity,
        doseEvent: DoseEventEntity,
        skippedAt: LocalDateTime = LocalDateTime.now()
    ): Pair<MedicationEntity, DoseEventEntity> {
        val restoredStock = if (doseEvent.status == DoseStatus.TAKEN) {
            medication.currentStock + doseEvent.doseAmount
        } else {
            medication.currentStock
        }
        return medication.copy(
            currentStock = restoredStock,
            updatedAt = skippedAt
        ) to markDoseSkipped(doseEvent, skippedAt)
    }

    fun markMissedIfPastGracePeriod(
        doseEvent: DoseEventEntity,
        now: LocalDateTime = LocalDateTime.now(),
        graceMinutes: Long = 60
    ): DoseEventEntity {
        val missedCutoff = doseEvent.scheduledDateTime.plusMinutes(graceMinutes)
        return if (doseEvent.status == DoseStatus.PENDING && now.isAfter(missedCutoff)) {
            doseEvent.copy(status = DoseStatus.MISSED)
        } else {
            doseEvent
        }
    }
}
