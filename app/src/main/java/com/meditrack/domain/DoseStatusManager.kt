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
        // Record what was actually removed (clamped at 0) so a later revert restores exactly that
        // amount rather than the full dose, which would otherwise inflate stock.
        val deducted = if (shouldDeduct) medication.currentStock - newStock else doseEvent.deductedAmount
        return medication.copy(
            currentStock = newStock,
            updatedAt = takenAt
        ) to doseEvent.copy(
            status = DoseStatus.TAKEN,
            takenDateTime = takenAt,
            skippedDateTime = null,
            deductedAmount = deducted
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
            medication.currentStock + (doseEvent.deductedAmount ?: doseEvent.doseAmount)
        } else {
            medication.currentStock
        }
        return medication.copy(
            currentStock = restoredStock,
            updatedAt = skippedAt
        ) to markDoseSkipped(doseEvent, skippedAt).copy(deductedAmount = null)
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
