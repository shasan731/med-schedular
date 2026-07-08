package com.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.domain.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DoseEventDao {
    @Query(
        """
        SELECT
            dose_events.id AS id,
            dose_events.medicationId AS medicationId,
            dose_events.scheduledDateTime AS scheduledDateTime,
            dose_events.status AS status,
            dose_events.takenDateTime AS takenDateTime,
            dose_events.skippedDateTime AS skippedDateTime,
            dose_events.doseAmount AS doseAmount,
            dose_events.note AS note,
            medications.name AS medicationName,
            medications.dosageInstruction AS dosageInstruction,
            medications.doseUnit AS doseUnit,
            medications.currentStock AS currentStock,
            medications.lowStockThresholdDays AS lowStockThresholdDays,
            medications.treatmentType AS treatmentType,
            medications.foodRelation AS foodRelation
        FROM dose_events
        INNER JOIN medications ON medications.id = dose_events.medicationId
        WHERE dose_events.scheduledDateTime >= :start
          AND dose_events.scheduledDateTime < :end
        ORDER BY dose_events.scheduledDateTime ASC, medications.name COLLATE NOCASE
        """
    )
    fun observeDoseEventsWithMedication(
        start: LocalDateTime,
        end: LocalDateTime
    ): Flow<List<DoseEventWithMedication>>

    @Query(
        """
        SELECT * FROM dose_events
        WHERE medicationId = :medicationId
        ORDER BY scheduledDateTime DESC
        """
    )
    fun observeDoseHistory(medicationId: Long): Flow<List<DoseEventEntity>>

    @Query("SELECT * FROM dose_events ORDER BY scheduledDateTime DESC")
    fun observeAllDoseEvents(): Flow<List<DoseEventEntity>>

    @Query(
        """
        SELECT * FROM dose_events
        WHERE scheduledDateTime >= :start
          AND scheduledDateTime < :end
        """
    )
    suspend fun getDoseEventsForRange(start: LocalDateTime, end: LocalDateTime): List<DoseEventEntity>

    @Query("SELECT * FROM dose_events WHERE id = :id")
    suspend fun getDoseEvent(id: Long): DoseEventEntity?

    @Query(
        """
        SELECT * FROM dose_events
        WHERE medicationId = :medicationId
          AND scheduledDateTime = :scheduledDateTime
        LIMIT 1
        """
    )
    suspend fun getDoseEventAt(medicationId: Long, scheduledDateTime: LocalDateTime): DoseEventEntity?

    @Query(
        """
        SELECT COUNT(*) FROM dose_events
        WHERE medicationId = :medicationId
          AND status = :status
        """
    )
    suspend fun countByStatus(medicationId: Long, status: DoseStatus): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDoseEvents(events: List<DoseEventEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDoseEvent(event: DoseEventEntity): Long

    @Update
    suspend fun updateDoseEvent(event: DoseEventEntity)

    @Query(
        """
        UPDATE dose_events
        SET status = :missedStatus
        WHERE status = :pendingStatus
          AND scheduledDateTime < :cutoff
        """
    )
    suspend fun markPendingBeforeAsMissed(
        cutoff: LocalDateTime,
        pendingStatus: DoseStatus,
        missedStatus: DoseStatus
    )

    @Query("DELETE FROM dose_events WHERE medicationId = :medicationId")
    suspend fun deleteDoseEventsForMedication(medicationId: Long)

    @Query(
        """
        DELETE FROM dose_events
        WHERE medicationId = :medicationId
          AND scheduledDateTime >= :cutoff
          AND status = :pendingStatus
        """
    )
    suspend fun deletePendingDoseEventsForMedicationAfter(
        medicationId: Long,
        cutoff: LocalDateTime,
        pendingStatus: DoseStatus
    )

    @Query("DELETE FROM dose_events")
    suspend fun clearAll()
}
