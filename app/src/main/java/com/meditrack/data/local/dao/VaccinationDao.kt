package com.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meditrack.data.local.entity.VaccinationEntity
import com.meditrack.domain.model.VaccinationStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM vaccinations ORDER BY scheduledDateTime ASC")
    fun observeVaccinations(): Flow<List<VaccinationEntity>>

    @Query("SELECT * FROM vaccinations ORDER BY scheduledDateTime ASC")
    suspend fun getAllVaccinations(): List<VaccinationEntity>

    @Query("SELECT * FROM vaccinations WHERE id = :id")
    suspend fun getVaccination(id: Long): VaccinationEntity?

    @Query(
        """
        SELECT * FROM vaccinations
        WHERE status = :status
          AND scheduledDateTime >= :from
        ORDER BY scheduledDateTime ASC
        """
    )
    suspend fun getUpcoming(from: LocalDateTime, status: VaccinationStatus): List<VaccinationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vaccination: VaccinationEntity): Long

    @Update
    suspend fun update(vaccination: VaccinationEntity)

    @Query("UPDATE vaccinations SET status = :status, completedDateTime = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: VaccinationStatus, completedAt: LocalDateTime?, updatedAt: LocalDateTime)

    @Query(
        """
        UPDATE vaccinations
        SET status = :missedStatus
        WHERE status = :upcomingStatus
          AND scheduledDateTime < :cutoff
        """
    )
    suspend fun markOverdueMissed(
        cutoff: LocalDateTime,
        upcomingStatus: VaccinationStatus,
        missedStatus: VaccinationStatus
    )

    @Query("DELETE FROM vaccinations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vaccinations")
    suspend fun clearAll()
}
