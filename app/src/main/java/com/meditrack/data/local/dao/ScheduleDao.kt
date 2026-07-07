package com.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meditrack.data.local.entity.MedicationScheduleEntity

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM medication_schedules WHERE medicationId = :medicationId AND isActive = 1")
    suspend fun getActiveSchedulesForMedication(medicationId: Long): List<MedicationScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<MedicationScheduleEntity>)

    @Query("DELETE FROM medication_schedules WHERE medicationId = :medicationId")
    suspend fun deleteSchedulesForMedication(medicationId: Long)
}
